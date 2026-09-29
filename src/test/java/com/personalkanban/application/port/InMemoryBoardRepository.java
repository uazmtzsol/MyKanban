package com.personalkanban.application.port;

import com.personalkanban.domain.board.BoardMemento;

import java.util.Objects;

/** Minimal in-memory implementation of the persistence port for tests. */
public final class InMemoryBoardRepository implements BoardRepository {

    private BoardMemento stored = BoardMemento.empty();
    public int saveCalls;

    @Override
    public void save(BoardMemento board) {
        this.stored = Objects.requireNonNull(board);
        saveCalls++;
    }

    @Override
    public BoardMemento load() {
        return stored;
    }
}
