package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;

import java.util.Objects;

/**
 * Marks one reminder as attended (or pending again), one undoable step
 * (T23 · B10). The state lives in the aggregate, so an answered reminder stays
 * answered across restarts and travels through export/sync.
 */
public final class AcknowledgeReminderCommand implements BoardCommand {

    private final CardId cardId;
    private final String reminderId;
    private final boolean acknowledged;

    public AcknowledgeReminderCommand(CardId cardId, String reminderId, boolean acknowledged) {
        this.cardId = Objects.requireNonNull(cardId);
        this.reminderId = Objects.requireNonNull(reminderId);
        this.acknowledged = acknowledged;
    }

    @Override
    public void execute(Board board) {
        board.acknowledgeReminder(cardId, reminderId, acknowledged);
    }
}
