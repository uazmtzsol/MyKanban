package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;

/**
 * A board mutation as an object (GoF Command). The service snapshots board
 * state around execution, so commands stay dumb executors of one intent.
 */
public interface BoardCommand {

    /** Applies the mutation to the board. */
    void execute(Board board);
}
