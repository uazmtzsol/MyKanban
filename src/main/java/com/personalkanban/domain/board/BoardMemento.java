package com.personalkanban.domain.board;

import java.util.ArrayList;
import java.util.List;

/**
 * Deep, self-contained snapshot of a {@link Board} at a point in time
 * (GoF Memento). Built from public immutable DTOs, so it can never be
 * mutated through the live board; doubles as the persistence DTO exchanged
 * with {@code BoardRepository} implementations.
 *
 * <p>Session 4: also snapshots the board's processes and the precedence
 * links between cards (as a flat {@link CardLink} pair list), so undo/redo
 * and persistence cover them.</p>
 */
public record BoardMemento(List<ColumnSnapshot> columns,
                           List<ProcessSnapshot> processes,
                           List<CardLink> links) {

    public BoardMemento {
        columns = List.copyOf(columns);
        processes = processes == null ? List.of() : List.copyOf(processes);
        links = links == null ? List.of() : List.copyOf(links);
    }

    /** Backward-compatible constructor (no processes, no links). */
    public BoardMemento(List<ColumnSnapshot> columns) {
        this(columns, List.of(), List.of());
    }

    public static BoardMemento empty() {
        return new BoardMemento(List.of());
    }

    /** Captures the full current state of the board. */
    public static BoardMemento capture(Board board) {
        List<CardLink> links = new ArrayList<>();
        board.linksView().forEach((from, successors) ->
                successors.forEach(to -> links.add(new CardLink(from, to))));
        return new BoardMemento(
                board.columns().stream().map(ColumnSnapshot::from).toList(),
                board.processList().stream().map(ProcessSnapshot::from).toList(),
                links);
    }

    /** Rebuilds a live board from this snapshot. */
    public Board toBoard() {
        Board board = new Board();
        board.restore(this);
        return board;
    }
}
