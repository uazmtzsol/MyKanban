package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Changes only the due date of one card (session 15, T5): one undoable step, so
 * the quick reschedule from the card menu can be taken back like any other
 * change. A {@code null} date clears it.
 */
public final class RescheduleCardCommand implements BoardCommand {

    private final CardId cardId;
    private final LocalDate newDueDate;

    public RescheduleCardCommand(CardId cardId, LocalDate newDueDate) {
        this.cardId = Objects.requireNonNull(cardId, "cardId");
        this.newDueDate = newDueDate;
    }

    @Override
    public void execute(Board board) {
        board.rescheduleCard(cardId, newDueDate);
    }
}
