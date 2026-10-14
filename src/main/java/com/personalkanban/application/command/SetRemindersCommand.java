package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.CardReminder;

import java.util.List;
import java.util.Objects;

/**
 * Replaces a card's whole reminder schedule (T23 · B10). The calendar dialog
 * edits a working copy and saves once, so picking five days and pressing Save
 * is a single Ctrl+Z.
 */
public final class SetRemindersCommand implements BoardCommand {

    private final CardId cardId;
    private final List<CardReminder> reminders;

    public SetRemindersCommand(CardId cardId, List<CardReminder> reminders) {
        this.cardId = Objects.requireNonNull(cardId);
        this.reminders = reminders == null ? List.of() : List.copyOf(reminders);
    }

    @Override
    public void execute(Board board) {
        board.setReminders(cardId, reminders);
    }
}
