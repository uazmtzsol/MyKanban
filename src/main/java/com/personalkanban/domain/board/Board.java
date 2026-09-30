package com.personalkanban.domain.board;

import com.personalkanban.domain.exception.NotFoundException;
import com.personalkanban.domain.exception.WipLimitBulkException;
import com.personalkanban.domain.exception.WipLimitExceededException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * One board's aggregate root, identified by a {@link BoardId}. All mutations
 * flow through here; the board keeps the column ordering and collects the
 * domain events produced during a use case.
 */
public final class Board {

    private final BoardId id;
    private final List<BoardColumn> columns = new ArrayList<>();
    private final List<DomainEvent> events = new ArrayList<>();

    /** Creates a fresh board with its own identity (GRASP Creator). */
    public Board() {
        this(new BoardId(java.util.UUID.randomUUID().toString()));
    }

    public Board(BoardId id) {
        if (id == null) {
            throw new IllegalArgumentException("Board id must not be null");
        }
        this.id = id;
    }

    public BoardId id() {
        return id;
    }

    // ------------------------------------------------------------------
    // Column commands
    // ------------------------------------------------------------------

    public BoardColumn addColumn(String title, String description, BoardColor color, WipLimit wipLimit) {
        BoardColumn column = new BoardColumn(title, description, color, wipLimit);
        columns.add(column);
        return column;
    }

    public void renameColumn(ColumnId columnId, String newTitle) {
        columnOrThrow(columnId).rename(newTitle);
    }

    public void redescribeColumn(ColumnId columnId, String newDescription) {
        columnOrThrow(columnId).describe(newDescription);
    }

    public void recolorColumn(ColumnId columnId, BoardColor color) {
        columnOrThrow(columnId).recolor(color);
    }

    public void limitColumn(ColumnId columnId, WipLimit wipLimit) {
        columnOrThrow(columnId).limitTo(wipLimit);
    }

    public void removeColumn(ColumnId columnId) {
        columns.removeIf(column -> column.id().equals(columnId));
    }

    /** Reorders a column so that it lands at {@code targetIndex} (clamped). */
    public void moveColumn(ColumnId columnId, int targetIndex) {
        BoardColumn column = columnOrThrow(columnId);
        columns.remove(column);
        columns.add(Math.clamp(targetIndex, 0, columns.size()), column);
    }

    // ------------------------------------------------------------------
    // Card commands
    // ------------------------------------------------------------------

    public CardAdded addCard(ColumnId columnId, String title, String description, BoardColor color) {
        return addCard(columnId, title, description, color, null, List.of());
    }

    public CardAdded addCard(ColumnId columnId, String title, String description, BoardColor color,
                             LocalDate dueDate, List<String> labels) {
        CardAdded event = columnOrThrow(columnId).addCard(title, description, color, dueDate, labels);
        events.add(event);
        return event;
    }

    public void editCard(CardId cardId, String newTitle, String newDescription, BoardColor newColor) {
        editCard(cardId, newTitle, newDescription, newColor, currentDueDateOf(cardId), currentLabelsOf(cardId));
    }

    public void editCard(CardId cardId, String newTitle, String newDescription, BoardColor newColor,
                         LocalDate newDueDate, List<String> newLabels) {
        Card card = findCard(cardId).orElseThrow(() -> new NotFoundException(cardId));
        card.rename(newTitle);
        card.describe(newDescription);
        card.recolor(newColor);
        card.schedule(newDueDate);
        card.tag(newLabels);
    }

    public void removeCard(CardId cardId) {
        BoardColumn column = findColumnOf(cardId)
                .orElseThrow(() -> new NotFoundException(cardId));
        column.removeCard(cardId);
        events.add(new CardRemoved(cardId, column.id(), Instant.now()));
    }

    /**
     * Bulk: adds labels to the given cards (dedup is case-insensitive, so an
     * existing "uaz" plus "UAZ" stays a single label).
     */
    public void addLabels(List<CardId> cardIds, List<String> labels) {
        if (labels.isEmpty() || cardIds.isEmpty()) {
            return;
        }
        for (CardId cardId : cardIds) {
            Card card = findCard(cardId).orElseThrow(() -> new NotFoundException(cardId));
            List<String> merged = new ArrayList<>(card.labels());
            for (String label : labels) {
                boolean exists = merged.stream().anyMatch(existing ->
                        existing.equalsIgnoreCase(label.strip()));
                if (!exists) {
                    merged.add(label.strip());
                }
            }
            card.tag(merged);
        }
    }

    /** Bulk: removes the given labels (case-insensitive) from the given cards. */
    public void removeLabels(List<CardId> cardIds, List<String> labels) {
        if (labels.isEmpty() || cardIds.isEmpty()) {
            return;
        }
        for (CardId cardId : cardIds) {
            Card card = findCard(cardId).orElseThrow(() -> new NotFoundException(cardId));
            List<String> remaining = card.labels().stream()
                    .filter(existing -> labels.stream().noneMatch(l -> l.strip().equalsIgnoreCase(existing)))
                    .toList();
            card.tag(remaining);
        }
    }

    /** Bulk: recolors all the given cards. */
    public void recolorCards(List<CardId> cardIds, BoardColor color) {
        Objects.requireNonNull(color, "color");
        for (CardId cardId : cardIds) {
            findCard(cardId).orElseThrow(() -> new NotFoundException(cardId)).recolor(color);
        }
    }

    /**
     * Bulk: removes several cards, wherever they live. Unknown ids throw.
     */
    public void removeCards(List<CardId> cardIds) {
        for (CardId cardId : cardIds) {
            BoardColumn column = findColumnOf(cardId)
                    .orElseThrow(() -> new NotFoundException(cardId));
            column.removeCard(cardId);
            events.add(new CardRemoved(cardId, column.id(), Instant.now()));
        }
    }

    /**
     * Toggles one label on one card (system flags like Urgente/Importante).
     * Adding respects the label cap; removing is case-insensitive. Returns
     * the new state so the UI can reflect it immediately.
     */
    public boolean toggleLabel(CardId cardId, String label) {
        Card card = findCard(cardId).orElseThrow(() -> new NotFoundException(cardId));
        if (card.hasLabelIgnoreCase(label)) {
            card.tag(card.labels().stream()
                    .filter(existing -> !existing.equalsIgnoreCase(label))
                    .toList());
            return false;
        }
        List<String> merged = new ArrayList<>(card.labels());
        merged.add(label);
        card.tag(merged);
        return true;
    }

    /**
     * Bulk: moves the given cards into the target column, appending them in
     * list order. Atomic: if the move would push the target past its WIP
     * limit, nothing changes and a {@link WipLimitExceededException} carrying
     * the required extra capacity is thrown.
     */
    public void moveCardsToColumn(List<CardId> cardIds, ColumnId targetColumnId) {
        BoardColumn target = columnOrThrow(targetColumnId);
        // Validation pass: unknown ids throw before any mutation, and the
        // whole batch must fit the target's WIP limit (all-or-nothing).
        int moving = 0;
        for (CardId cardId : cardIds) {
            BoardColumn source = findColumnOf(cardId)
                    .orElseThrow(() -> new NotFoundException(cardId));
            if (!source.id().equals(target.id())) {
                moving++;
            }
        }
        if (moving > 0 && target.wipLimit().isExceededBy(target.cardCount() + moving)) {
            int limit = target.wipLimit().asOptional().orElseThrow();
            throw new WipLimitBulkException(target.id(), limit,
                    moving - (limit - target.cardCount()));
        }
        // Transfer pass: preserves the caller's selection order. Cards already
        // in the target stay where they are.
        for (CardId cardId : cardIds) {
            BoardColumn source = findColumnOf(cardId)
                    .orElseThrow(() -> new NotFoundException(cardId));
            if (!source.id().equals(target.id())) {
                source.transferCardsTo(target, List.of(cardId));
            }
        }
    }

    public void removeAllCards(ColumnId columnId) {
        columnOrThrow(columnId).clearCards();
    }

    /**
     * Moves a card within its column or across columns. Positions are clamped,
     * WIP limits are enforced by the target column, and a {@link CardMoved}
     * event is emitted.
     */
    public CardMoved moveCard(CardId cardId, ColumnId targetColumnId, int targetIndex) {
        BoardColumn target = columnOrThrow(targetColumnId);
        BoardColumn source = findColumnOf(cardId)
                .orElseThrow(() -> new NotFoundException(cardId));
        target.moveCardFrom(source, cardId, targetIndex);
        CardMoved event = new CardMoved(cardId, source.id(), target.id(), Instant.now());
        events.add(event);
        return event;
    }

    /**
     * Moves a card into the empty slot that visually existed between two
     * neighbors at drag time (the drop indicator's position). Slot indexes are
     * pre-drop: dropping into the slot right after card X lands the card
     * exactly there, including when reordering inside one column (a drop onto
     * the card's own trailing slot is a no-op). {@link #moveCard} already
     * carries these pre-drop semantics, so this method is an explicit,
     * intention-revealing alias for drag & drop use.
     */
    public CardMoved moveCardToSlot(CardId cardId, ColumnId targetColumnId, int slotIndex) {
        return moveCard(cardId, targetColumnId, slotIndex);
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public List<BoardColumn> columns() {
        return List.copyOf(columns);
    }

    public Optional<BoardColumn> columnById(ColumnId columnId) {
        return columns.stream().filter(column -> column.id().equals(columnId)).findFirst();
    }

    public BoardColumn columnOrThrow(ColumnId columnId) {
        return columnById(columnId)
                .orElseThrow(() -> new NotFoundException(columnId));
    }

    public Optional<BoardColumn> findColumnOf(CardId cardId) {
        return columns.stream().filter(column -> column.hasCard(cardId)).findFirst();
    }

    public Optional<Card> findCard(CardId cardId) {
        return findColumnOf(cardId).flatMap(column -> column.cardById(cardId));
    }

    /** Cards having a due date, grouped by date, ordered chronologically. */
    public Map<LocalDate, List<Card>> cardsDueBy() {
        Map<LocalDate, List<Card>> byDate = new TreeMap<>();
        for (BoardColumn column : columns) {
            for (Card card : column.cards()) {
                if (card.dueDate() != null) {
                    byDate.computeIfAbsent(card.dueDate(), date -> new ArrayList<>()).add(card);
                }
            }
        }
        return byDate;
    }

    public int columnCount() {
        return columns.size();
    }

    public int cardCount() {
        return columns.stream().mapToInt(BoardColumn::cardCount).sum();
    }

    /** Events produced since the last drain; consumed by the application layer. */
    public List<DomainEvent> drainEvents() {
        List<DomainEvent> drained = List.copyOf(events);
        events.clear();
        return drained;
    }

    // ------------------------------------------------------------------
    // Snapshot restore (memento / persistence)
    // ------------------------------------------------------------------

    /** Replaces the whole state with the snapshot's state (undo/redo, startup load). */
    public void restore(BoardMemento memento) {
        columns.clear();
        memento.columns().stream().map(ColumnSnapshot::toColumn).forEach(columns::add);
    }

    private LocalDate currentDueDateOf(CardId cardId) {
        return findCard(cardId).map(Card::dueDate).orElse(null);
    }

    private List<String> currentLabelsOf(CardId cardId) {
        return findCard(cardId).map(Card::labels).orElse(List.of());
    }
}
