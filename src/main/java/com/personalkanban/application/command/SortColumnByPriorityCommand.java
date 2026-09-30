package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.ColumnId;

import java.util.Objects;

/**
 * Stable priority sort of one column (user request): (★+!) first, then (!),
 * then (★); every other card keeps its current relative order. One
 * undoable step.
 */
public final class SortColumnByPriorityCommand implements BoardCommand {

    private final ColumnId columnId;

    public SortColumnByPriorityCommand(ColumnId columnId) {
        this.columnId = Objects.requireNonNull(columnId);
    }

    @Override
    public void execute(Board board) {
        board.sortColumnByPriority(columnId);
    }
}
