package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.ColumnId;

import java.util.Objects;

/** Moves a column to a new index on the board (drag & drop reorder). */
public final class MoveColumnCommand implements BoardCommand {

    private final ColumnId columnId;
    private final int targetIndex;

    public MoveColumnCommand(ColumnId columnId, int targetIndex) {
        this.columnId = Objects.requireNonNull(columnId);
        this.targetIndex = targetIndex;
    }

    @Override
    public void execute(Board board) {
        board.moveColumn(columnId, targetIndex);
    }
}
