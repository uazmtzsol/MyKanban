package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.WipLimit;

import java.util.Objects;

/**
 * Edits description, color and WIP limit of a column in one undoable step.
 * The title is intentionally excluded: the original app has a dedicated
 * inline rename, and mixing it here would create two ways to do one thing.
 */
public final class EditColumnCommand implements BoardCommand {

    private final ColumnId columnId;
    private final String newDescription;
    private final BoardColor newColor;
    private final WipLimit newWipLimit;

    public EditColumnCommand(ColumnId columnId, String newDescription, BoardColor newColor, WipLimit newWipLimit) {
        this.columnId = Objects.requireNonNull(columnId);
        this.newDescription = newDescription == null ? "" : newDescription;
        this.newColor = Objects.requireNonNull(newColor);
        this.newWipLimit = Objects.requireNonNull(newWipLimit);
    }

    @Override
    public void execute(Board board) {
        board.redescribeColumn(columnId, newDescription);
        board.recolorColumn(columnId, newColor);
        board.limitColumn(columnId, newWipLimit);
    }
}
