package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;

import java.time.Instant;
import java.util.Objects;

/** Starts a new time-tracking entry on one card (session 6), one undoable step. */
public final class StartTimeTrackingCommand implements BoardCommand {

    private final CardId cardId;

    public StartTimeTrackingCommand(CardId cardId) {
        this.cardId = Objects.requireNonNull(cardId);
    }

    @Override
    public void execute(Board board) {
        board.startTimeTracking(cardId, Instant.now());
    }
}
