package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.ColumnId;

import java.util.Objects;

/** Sets the background color of one column (null clears it). */
public final class SetColumnBackgroundCommand implements BoardCommand {

    private final ColumnId columnId;
    private final String backgroundColor; // nullable

    public SetColumnBackgroundCommand(ColumnId columnId, String backgroundColor) {
        this.columnId = Objects.requireNonNull(columnId);
        this.backgroundColor = backgroundColor;
    }

    @Override
    public void execute(Board board) {
        board.setColumnBackground(columnId, backgroundColor);
    }
}
