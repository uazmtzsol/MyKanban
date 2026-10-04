package com.personalkanban.domain.board;

import java.util.ArrayList;
import java.util.List;

/**
 * Deep, self-contained snapshot of a {@link Board} at a point in time
 * (GoF Memento). Built from public immutable DTOs, so it can never be
 * mutated through the live board; doubles as the persistence DTO exchanged
 * with {@code BoardRepository} implementations.
 *
 * <p>It snapshots the board's columns (and their cards), processes, the
 * precedence links between cards (as a flat {@link CardLink} pair list) and
 * the time-tracking entries of every card (as a flat {@link TimelineEntry}
 * list keyed by card id), so undo/redo and persistence cover them.</p>
 */
public record BoardMemento(List<ColumnSnapshot> columns,
                           List<ProcessSnapshot> processes,
                           List<CardLink> links,
                           List<TimelineEntry> timeline) {

    public BoardMemento {
        columns = List.copyOf(columns);
        processes = processes == null ? List.of() : List.copyOf(processes);
        links = links == null ? List.of() : List.copyOf(links);
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
    }

    /** Backward-compatible constructor (no processes, no links, no timeline). */
    public BoardMemento(List<ColumnSnapshot> columns) {
        this(columns, List.of(), List.of(), List.of());
    }

    /** Backward-compatible constructor (no timeline). */
    public BoardMemento(List<ColumnSnapshot> columns,
                        List<ProcessSnapshot> processes,
                        List<CardLink> links) {
        this(columns, processes, links, List.of());
    }

    public static BoardMemento empty() {
        return new BoardMemento(List.of());
    }

    /** Captures the full current state of the board. */
    public static BoardMemento capture(Board board) {
        List<CardLink> links = new ArrayList<>();
        board.linksView().forEach((from, successors) ->
                successors.forEach(to -> links.add(new CardLink(from, to))));
        List<TimelineEntry> timeline = new ArrayList<>();
        // TimelineEntry is mutable, so the memento stores copies: otherwise a
        // later stop/comment would silently rewrite the captured history.
        board.allCards().forEach(card -> card.timeline().entries()
                .forEach(entry -> timeline.add(entry.copy())));
        return new BoardMemento(
                board.columns().stream().map(ColumnSnapshot::from).toList(),
                board.processList().stream().map(ProcessSnapshot::from).toList(),
                links,
                timeline);
    }

    /** Rebuilds a live board from this snapshot. */
    public Board toBoard() {
        Board board = new Board();
        board.restore(this);
        return board;
    }
}
