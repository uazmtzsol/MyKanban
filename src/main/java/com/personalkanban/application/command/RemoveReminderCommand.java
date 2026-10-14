package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;

import java.util.Objects;

/** Removes one reminder moment of a card, one undoable step (T23 · B10). */
public final class RemoveReminderCommand implements BoardCommand {

    private final CardId cardId;
    private final String reminderId;

    public RemoveReminderCommand(CardId cardId, String reminderId) {
        this.cardId = Objects.requireNonNull(cardId);
        this.reminderId = Objects.requireNonNull(reminderId);
    }

    @Override
    public void execute(Board board) {
        board.removeReminder(cardId, reminderId);
    }
}
