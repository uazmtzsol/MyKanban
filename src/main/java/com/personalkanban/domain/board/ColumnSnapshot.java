package com.personalkanban.domain.board;

import java.time.Instant;
import java.util.List;

/**
 * Public immutable DTO describing one column and its cards. Canonical shape
 * shared by persistence adapters and undo/redo mementos. {@code done} marks
 * the board's single "finalized" column and {@code backgroundColor} is an
 * optional hex color painted behind the column header/cards.
 */
public record ColumnSnapshot(ColumnId id, String title, String description, BoardColor color,
                             WipLimit wipLimit, Instant createdAt, boolean done,
                             String backgroundColor, List<CardSnapshot> cards) {

    /** Legacy shape: a column without done flag nor background color. */
    public ColumnSnapshot(ColumnId id, String title, String description, BoardColor color,
                          WipLimit wipLimit, Instant createdAt, List<CardSnapshot> cards) {
        this(id, title, description, color, wipLimit, createdAt, false, null, cards);
    }

    public ColumnSnapshot {
        if (id == null || color == null || wipLimit == null || createdAt == null || cards == null) {
            throw new IllegalArgumentException("Column snapshot fields must not be null");
        }
        cards = List.copyOf(cards);
    }

    static ColumnSnapshot from(BoardColumn column) {
        return new ColumnSnapshot(
                column.id(), column.title(), column.description(), column.color(),
                column.wipLimit(), column.createdAt(), column.isDone(),
                column.backgroundColor(),
                column.cards().stream().map(CardSnapshot::from).toList());
    }

    BoardColumn toColumn() {
        BoardColumn column = BoardColumn.restore(id(), title(), description(), color(),
                wipLimit(), createdAt(), done(), backgroundColor());
        cards.forEach(snapshot -> column.adoptForMemento(snapshot.toCard(id())));
        return column;
    }

    /** Re-adopts card objects with a different column owner (memento support). */
    BoardColumn withCardsOwnedBy(ColumnId ownerId) {
        BoardColumn column = BoardColumn.restore(id(), title(), description(), color(),
                wipLimit(), createdAt(), done(), backgroundColor());
        cards.forEach(snapshot -> column.adoptForMemento(snapshot.toCard(ownerId)));
        return column;
    }
}
