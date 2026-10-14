package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.CardReminder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/** Adds one reminder moment to a card, one undoable step (T23 · B10). */
public final class AddReminderCommand implements BoardCommand {

    private final CardId cardId;
    private final LocalDate date;
    private final LocalTime time;

    private String createdReminderId;

    public AddReminderCommand(CardId cardId, LocalDate date, LocalTime time) {
        this.cardId = Objects.requireNonNull(cardId);
        this.date = Objects.requireNonNull(date);
        this.time = time;
    }

    @Override
    public void execute(Board board) {
        CardReminder reminder = board.addReminder(cardId, date, time);
        createdReminderId = reminder.id();
    }

    public String createdReminderId() {
        return Objects.requireNonNull(createdReminderId, "command not executed yet");
    }
}
