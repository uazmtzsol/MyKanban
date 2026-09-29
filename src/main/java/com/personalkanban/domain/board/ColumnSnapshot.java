package com.personalkanban.domain.board;

import java.time.Instant;
import java.util.List;

/**
 * Public immutable DTO describing one column and its cards. Canonical shape
 * shared by persistence adapters and undo/redo mementos.
 */
public record ColumnSnapshot(ColumnId id, String title, String description, BoardColor color,
                             WipLimit wipLimit, Instant createdAt, List<CardSnapshot> cards) {

    public ColumnSnapshot {
        if (id == null || color == null || wipLimit == null || createdAt == null || cards == null) {
            throw new IllegalArgumentException("Column snapshot fields must not be null");
        }
        cards = List.copyOf(cards);
    }

    static ColumnSnapshot from(BoardColumn column) {
        return new ColumnSnapshot(
                column.id(), column.title(), column.description(), column.color(),
                column.wipLimit(), column.createdAt(),
                column.cards().stream().map(CardSnapshot::from).toList());
    }

    BoardColumn toColumn() {
        BoardColumn column = BoardColumn.restore(id(), title(), description(), color(), wipLimit(), createdAt());
        cards.forEach(snapshot -> column.adoptForMemento(snapshot.toCard(id())));
        return column;
    }
}
