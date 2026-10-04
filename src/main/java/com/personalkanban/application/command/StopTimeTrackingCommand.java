package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;

import java.time.Instant;
import java.util.Objects;

/** Stops the running time-tracking entry of one card (session 6), undoable. */
public final class StopTimeTrackingCommand implements BoardCommand {

    private final CardId cardId;

    public StopTimeTrackingCommand(CardId cardId) {
        this.cardId = Objects.requireNonNull(cardId);
    }

    @Override
    public void execute(Board board) {
        board.stopTimeTracking(cardId, Instant.now());
    }
}
