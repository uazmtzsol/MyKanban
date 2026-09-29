package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.ColumnId;

import java.util.Objects;

/** Removes a column together with all of its cards (the original app's semantics). */
public final class RemoveColumnCommand implements BoardCommand {

    private final ColumnId columnId;

    public RemoveColumnCommand(ColumnId columnId) {
        this.columnId = Objects.requireNonNull(columnId);
    }

    @Override
    public void execute(Board board) {
        board.removeColumn(columnId);
    }
}
