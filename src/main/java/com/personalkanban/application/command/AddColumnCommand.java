package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.BoardColumn;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.WipLimit;

import java.util.Objects;

/** Adds a column; undoes by removing exactly the created column. */
public final class AddColumnCommand implements BoardCommand {

    private final String title;
    private final String description;
    private final BoardColor color;
    private final WipLimit wipLimit;

    private ColumnId createdId;

    public AddColumnCommand(String title, String description, BoardColor color, WipLimit wipLimit) {
        this.title = Objects.requireNonNull(title);
        this.description = description == null ? "" : description;
        this.color = Objects.requireNonNull(color);
        this.wipLimit = Objects.requireNonNull(wipLimit);
    }

    @Override
    public void execute(Board board) {
        BoardColumn column = board.addColumn(title, description, color, wipLimit);
        createdId = column.id();
    }

    public ColumnId createdColumnId() {
        return Objects.requireNonNull(createdId, "command not executed yet");
    }
}
