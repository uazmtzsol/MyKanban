package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.ColumnId;

import java.util.Objects;

/** Renames a column (undoable via the service's memento stack). */
public final class RenameColumnCommand implements BoardCommand {

    private final ColumnId columnId;
    private final String newTitle;

    public RenameColumnCommand(ColumnId columnId, String newTitle) {
        this.columnId = Objects.requireNonNull(columnId);
        this.newTitle = Objects.requireNonNull(newTitle);
    }

    @Override
    public void execute(Board board) {
        board.renameColumn(columnId, newTitle);
    }
}
