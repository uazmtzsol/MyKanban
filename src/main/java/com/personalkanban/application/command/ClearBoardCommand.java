package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;

/** Wipes the board: every column and every card (the original's "clear board"). */
public final class ClearBoardCommand implements BoardCommand {

    @Override
    public void execute(Board board) {
        board.clearColumns();
    }
}
