package com.personalkanban.domain.board;

import java.util.List;

/**
 * Deep, self-contained snapshot of a {@link Board} at a point in time
 * (GoF Memento). Built from public immutable DTOs, so it can never be
 * mutated through the live board; doubles as the persistence DTO exchanged
 * with {@code BoardRepository} implementations.
 */
public record BoardMemento(List<ColumnSnapshot> columns) {

    public BoardMemento {
        columns = List.copyOf(columns);
    }

    public static BoardMemento empty() {
        return new BoardMemento(List.of());
    }

    /** Captures the full current state of the board. */
    public static BoardMemento capture(Board board) {
        return new BoardMemento(board.columns().stream().map(ColumnSnapshot::from).toList());
    }

    /** Rebuilds a live board from this snapshot. */
    public Board toBoard() {
        Board board = new Board();
        board.restore(this);
        return board;
    }
}
