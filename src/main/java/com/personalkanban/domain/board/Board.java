package com.personalkanban.domain.board;

import com.personalkanban.domain.exception.NotFoundException;
import com.personalkanban.domain.exception.WipLimitBulkException;
import com.personalkanban.domain.exception.WipLimitExceededException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.SequencedSet;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * One board's aggregate root, identified by a {@link BoardId}. All mutations
 * flow through here; the board keeps the column ordering and collects the
 * domain events produced during a use case.
 *
 * <p>Session 4 additions: processes (named card groups), precedence links
 * between cards (validated acyclic by {@link DependencyGuard}), and card
 * checklist orchestration. Process and link state participates in the
 * memento, so undo/redo and persistence cover them too.</p>
 */
public final class Board {

    private final BoardId id;
    private final List<BoardColumn> columns = new ArrayList<>();
    private final List<DomainEvent> events = new ArrayList<>();

    /** Processes of this board (insertion ordered). */
    private final SequencedMap<ProcessId, Process> processes = new LinkedHashMap<>();

    /** Precedence links: key precedes each value ("key → successor"). */
    private final SequencedMap<CardId, SequencedSet<CardId>> links = new LinkedHashMap<>();

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
        return addCard(columnId, title, description, color, dueDate, labels, null, null, null);
    }

    /**
     * Full card creation (session 4): plain notes, initial checklist items
     * and an optional process assignment ride along in one undoable step.
     * Null-safe extras keep the simpler overloads intact.
     */
    public CardAdded addCard(ColumnId columnId, String title, String description, BoardColor color,
                             LocalDate dueDate, List<String> labels,
                             String notes, List<ChecklistItem> checklist, ProcessId processId) {
        if (processId != null) {
            processOrThrow(processId); // validate BEFORE any mutation
        }
        CardAdded event = columnOrThrow(columnId).addCard(Ids.newCardId(), title, description,
                color, dueDate, labels);
        Card card = findCard(event.cardId()).orElseThrow();
        if (notes != null && !notes.isBlank()) {
            card.annotate(notes);
        }
        if (checklist != null) {
            checklist.forEach(card::adoptChecklistItem);
        }
        if (processId != null) {
            card.assignTo(processId);
        }
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

    /** Sets the plain-text notes of one card (session 4.4). */
    public void annotateCard(CardId cardId, String notes) {
        findCard(cardId).orElseThrow(() -> new NotFoundException(cardId)).annotate(notes);
    }

    /** Adds a checklist item to one card; returns the created item. */
    public ChecklistItem addChecklistItem(CardId cardId, String text) {
        return findCard(cardId).orElseThrow(() -> new NotFoundException(cardId)).addChecklistItem(text);
    }

    /** Renames one checklist item of one card. */
    public void renameChecklistItem(CardId cardId, String itemId, String newText) {
        findCard(cardId).orElseThrow(() -> new NotFoundException(cardId))
                .renameChecklistItem(itemId, newText);
    }

    /** Marks or unmarks one checklist item of one card. */
    public void setChecklistItemDone(CardId cardId, String itemId, boolean done) {
        findCard(cardId).orElseThrow(() -> new NotFoundException(cardId))
                .setChecklistItemDone(itemId, done);
    }

    /** Removes one checklist item from one card. */
    public void removeChecklistItem(CardId cardId, String itemId) {
        findCard(cardId).orElseThrow(() -> new NotFoundException(cardId))
                .removeChecklistItem(itemId);
    }

    /**
     * Converts a checklist item into its own card (user requirement: flat
     * checklist, items promotable to cards). The new card lands at the end
     * of the SAME column as the source card, and the item is removed from
     * the source checklist. Returns the created card's id.
     */
    public CardAdded convertChecklistItemToCard(CardId cardId, String itemId) {
        Card source = findCard(cardId).orElseThrow(() -> new NotFoundException(cardId));
        ChecklistItem item = source.checklistItem(itemId);
        BoardColumn column = columnOrThrow(source.columnId());
        CardAdded event = column.addCard(item.text(), "", column.defaultCardColor(), null, List.of());
        source.removeChecklistItem(itemId);
        events.add(event);
        return event;
    }

    public void removeCard(CardId cardId) {
        BoardColumn column = findColumnOf(cardId)
                .orElseThrow(() -> new NotFoundException(cardId));
        column.removeCard(cardId);
        detachCard(cardId); // dangling links are garbage, not invariant breaks
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
            detachCard(cardId);
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
     * Reorders ONE column by the quick flags, nothing else moves (user
     * request): (★+!) first, then (!), then (★); every card without those
     * labels keeps its current relative order right after them. Stable
     * partition — no other criterion, no reflow of untouched columns.
     */
    public void sortColumnByPriority(ColumnId columnId) {
        BoardColumn column = columnOrThrow(columnId);
        List<Card> current = column.cards();
        List<Card> sorted = new ArrayList<>(current.size());
        current.stream().filter(Card::isUrgentAndImportantCard).forEach(sorted::add);
        current.stream().filter(Card::isUrgentOnlyCard).forEach(sorted::add);
        current.stream().filter(Card::isImportantOnlyCard).forEach(sorted::add);
        current.stream().filter(card -> !card.isUrgentAndImportantCard()
                && !card.isUrgentOnlyCard() && !card.isImportantOnlyCard()).forEach(sorted::add);
        column.replaceCards(sorted);
    }

    /**
     * Rebuilds a card's checklist from plain lines (dialog advanced form):
     * "[x] text" = done, "[ ] text" or plain "text" = pending. Existing
     * items are matched BY TEXT so their identity (and conversion ability)
     * survives; lines removed from the input remove their items; new lines
     * append new items.
     */
    public void setChecklistFromLines(CardId cardId, List<String> lines) {
        Card card = findCard(cardId).orElseThrow(() -> new NotFoundException(cardId));
        List<ChecklistItem> rebuilt = new ArrayList<>();
        for (String line : lines) {
            String raw = line == null ? "" : line.strip();
            String lower = raw.toLowerCase(java.util.Locale.ROOT);
            // Single assignment per branch keeps done/text effectively final
            // for the lambdas below.
            final boolean done;
            final String text;
            if (lower.startsWith("[x]")) {
                done = true;
                text = raw.substring(3).strip();
            } else if (lower.startsWith("[ ]")) {
                done = false;
                text = raw.substring(3).strip();
            } else {
                done = false;
                text = raw;
            }
            if (text.isEmpty()) {
                continue;
            }
            ChecklistItem existing = card.checklist().stream()
                    .filter(item -> item.text().equalsIgnoreCase(text))
                    .findFirst()
                    .map(item -> item.withDone(done))
                    .orElseGet(() -> ChecklistItem.newItem(text).withDone(done));
            rebuilt.add(existing);
        }
        card.replaceChecklist(rebuilt);
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
    // Processes (session 4.6): named groups of related cards
    // ------------------------------------------------------------------

    /** Creates a process and returns it. */
    public Process addProcess(String name) {
        Process process = new Process(name);
        processes.put(process.id(), process);
        return process;
    }

    public void renameProcess(ProcessId processId, String newName) {
        processOrThrow(processId).rename(newName);
    }

    /** Deletes a process; member cards simply become unassigned. */
    public void removeProcess(ProcessId processId) {
        processOrThrow(processId);
        processes.remove(processId);
        for (BoardColumn column : columns) {
            for (Card card : column.cards()) {
                if (processId.equals(card.processId())) {
                    card.assignTo(null);
                }
            }
        }
    }

    /** Assigns one card to a process; null clears the assignment. */
    public void assignCardToProcess(CardId cardId, ProcessId processId) {
        Card card = findCard(cardId).orElseThrow(() -> new NotFoundException(cardId));
        if (processId != null) {
            processOrThrow(processId); // must exist
        }
        card.assignTo(processId);
    }

    public Process processOrThrow(ProcessId processId) {
        Process process = processes.get(processId);
        if (process == null) {
            throw new NotFoundException(processId);
        }
        return process;
    }

    /** Processes in insertion order. */
    public List<Process> processList() {
        return List.copyOf(processes.values());
    }

    public boolean hasProcesses() {
        return !processes.isEmpty();
    }

    /** Cards whose {@code processId} matches, in board order. */
    public List<Card> cardsOfProcess(ProcessId processId) {
        List<Card> members = new ArrayList<>();
        for (BoardColumn column : columns) {
            for (Card card : column.cards()) {
                if (processId.equals(card.processId())) {
                    members.add(card);
                }
            }
        }
        return members;
    }

    // ------------------------------------------------------------------
    // Precedence links (session 4.6): "from precedes to"
    // ------------------------------------------------------------------

    /** Links {@code from → to} ("from precedes to"); acyclicity enforced. */
    public void linkCards(CardId from, CardId to) {
        DependencyGuard.requireLinkable(this, from, to);
        if (outgoingSuccessorsOf(from).contains(to)) {
            return; // idempotent duplicate
        }
        links.computeIfAbsent(from, key -> new LinkedHashSet<>()).add(to);
    }

    /** Removes an existing link; unknown links are silently ignored. */
    public void unlinkCards(CardId from, CardId to) {
        SequencedSet<CardId> successors = links.get(from);
        if (successors != null) {
            successors.remove(to);
            if (successors.isEmpty()) {
                links.remove(from);
            }
        }
    }

    /** Direct successors of a card ("what comes after it"). */
    public SequencedSet<CardId> outgoingSuccessorsOf(CardId cardId) {
        return links.getOrDefault(cardId, new LinkedHashSet<>());
    }

    /** Direct predecessors of a card ("what comes before it"). */
    public SequencedSet<CardId> incomingPredecessorsOf(CardId cardId) {
        SequencedSet<CardId> predecessors = new LinkedHashSet<>();
        links.forEach((predecessor, successors) -> {
            if (successors.contains(cardId)) {
                predecessors.add(predecessor);
            }
        });
        return predecessors;
    }

    /** Live (unmodifiable) view of the whole link map, for snapshots. */
    SequencedMap<CardId, SequencedSet<CardId>> linksView() {
        return links;
    }

    /** Flat, order-stable view of every link, for tests and tooling. */
    public List<CardLink> linkList() {
        List<CardLink> all = new ArrayList<>();
        links.forEach((from, successors) ->
                successors.forEach(to -> all.add(new CardLink(from, to))));
        return all;
    }

    /** Cards of this board, in board order (streaming helper). */
    public List<Card> allCards() {
        List<Card> cards = new ArrayList<>();
        for (BoardColumn column : columns) {
            cards.addAll(column.cards());
        }
        return cards;
    }

    /** Titles of the given cards, in the given order (UI lookup helper). */
    public String titleOf(CardId cardId) {
        return findCard(cardId).map(Card::title)
                .orElse("?" + cardId.value());
    }

    /** Set of card ids present on the board (link integrity helper). */
    public java.util.Set<CardId> cardIds() {
        return allCards().stream().map(Card::id).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Removes every link touching the given card (used on card removal). */
    private void detachCard(CardId cardId) {
        links.remove(cardId);
        links.values().forEach(successors -> successors.remove(cardId));
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
        memento.columns().stream()
                .map(snapshot -> snapshot.withCardsOwnedBy(snapshot.id()))
                .forEach(columns::add);
        processes.clear();
        memento.processes().forEach(snapshot -> {
            Process process = snapshot.toProcess();
            processes.put(process.id(), process);
        });
        links.clear();
        for (CardLink link : memento.links()) {
            links.computeIfAbsent(link.from(), key -> new LinkedHashSet<>()).add(link.to());
        }
    }

    private LocalDate currentDueDateOf(CardId cardId) {
        return findCard(cardId).map(Card::dueDate).orElse(null);
    }

    private List<String> currentLabelsOf(CardId cardId) {
        return findCard(cardId).map(Card::labels).orElse(List.of());
    }
}
