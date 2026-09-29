package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.ColumnId;

import java.util.Objects;

/** Deletes all cards of one column — the original's "delete all records" affordance. */
public final class ClearColumnCommand implements BoardCommand {

    private final ColumnId columnId;

    public ClearColumnCommand(ColumnId columnId) {
        this.columnId = Objects.requireNonNull(columnId);
    }

    @Override
    public void execute(Board board) {
        board.removeAllCards(columnId);
    }
}
