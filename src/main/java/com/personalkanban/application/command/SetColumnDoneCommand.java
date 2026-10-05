package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.ColumnId;

import java.util.Objects;

/**
 * Marks (or unmarks) a column as the board's "done" column. The
 * domain keeps the invariant that at most one column per board
 * carries the flag, so marking one unmarks any previous one.
 */
public final class SetColumnDoneCommand implements BoardCommand {

    private final ColumnId columnId;
    private final boolean done;

    public SetColumnDoneCommand(ColumnId columnId, boolean done) {
        this.columnId = Objects.requireNonNull(columnId);
        this.done = done;
    }

    @Override
    public void execute(Board board) {
        board.markColumnDone(columnId, done);
    }
}
