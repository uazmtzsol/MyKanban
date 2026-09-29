package com.personalkanban.domain.board;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The aggregate root. All mutations flow through here; it keeps the column
 * ordering and collects the domain events produced during a use case.
 * Commands are intentionally coarse-grained so the application layer never
 * needs to know about intra-aggregate details (Information Expert).
 */
public final class Board {

    private final List<BoardColumn> columns = new ArrayList<>();
    private final List<DomainEvent> events = new ArrayList<>();

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

    public void clearColumns() {
        columns.clear();
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
        CardAdded event = columnOrThrow(columnId).addCard(title, description, color);
        events.add(event);
        return event;
    }

    public void editCard(CardId cardId, String newTitle, String newDescription, BoardColor newColor) {
        Card card = findCard(cardId)
                .orElseThrow(() -> new com.personalkanban.domain.exception.NotFoundException(cardId));
        card.rename(newTitle);
        card.describe(newDescription);
        card.recolor(newColor);
    }

    public void removeCard(CardId cardId) {
        BoardColumn column = findColumnOf(cardId)
                .orElseThrow(() -> new com.personalkanban.domain.exception.NotFoundException(cardId));
        column.removeCard(cardId);
        events.add(new CardRemoved(cardId, column.id(), Instant.now()));
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
                .orElseThrow(() -> new com.personalkanban.domain.exception.NotFoundException(cardId));
        target.moveCardFrom(source, cardId, targetIndex);
        CardMoved event = new CardMoved(cardId, source.id(), target.id(), Instant.now());
        events.add(event);
        return event;
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
                .orElseThrow(() -> new com.personalkanban.domain.exception.NotFoundException(columnId));
    }

    public Optional<BoardColumn> findColumnOf(CardId cardId) {
        return columns.stream().filter(column -> column.hasCard(cardId)).findFirst();
    }

    public Optional<Card> findCard(CardId cardId) {
        return findColumnOf(cardId).flatMap(column -> column.cardById(cardId));
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
}
