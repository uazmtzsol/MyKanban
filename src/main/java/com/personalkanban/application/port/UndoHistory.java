package com.personalkanban.application.port;

import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;

import java.util.Optional;

/**
 * Outbound port for persistent undo/redo history, kept per board: a bounded
 * LIFO of "before" mementos (Policy Controller). The adapter enforces the
 * capacity bound on {@link #push}, dropping the oldest entries, so callers
 * never trim by hand. The application never knows where history lives
 * (SQLite now, JSON later).
 */
public interface UndoHistory {

    int CAPACITY = 100;

    /** Appends a snapshot; the adapter drops the oldest entries beyond capacity. */
    void push(BoardId boardId, BoardMemento snapshot);

    /** Most recent snapshot for the board, without removing it. */
    Optional<BoardMemento> peek(BoardId boardId);

    /** Removes and returns the most recent snapshot for the board. */
    Optional<BoardMemento> pop(BoardId boardId);

    /** Clears the board's history (e.g. after a delete). */
    void clear(BoardId boardId);

    /** Counts snapshots stored for the board (capacity-bound). */
    int depth(BoardId boardId);
}
