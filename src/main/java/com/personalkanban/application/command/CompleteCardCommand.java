package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Marks one card as finished on a given day, or reopens it with a {@code null}
 * date (T24 · B11): one undoable step, so the ✓ button on the card front can be
 * taken back like any other change. Dropping a card into the finalized column
 * reaches the same state through {@code Board#moveCard}.
 */
public final class CompleteCardCommand implements BoardCommand {

    private final CardId cardId;
    private final LocalDate finishedOn;

    public CompleteCardCommand(CardId cardId, LocalDate finishedOn) {
        this.cardId = Objects.requireNonNull(cardId, "cardId");
        this.finishedOn = finishedOn;
    }

    @Override
    public void execute(Board board) {
        board.completeCard(cardId, finishedOn);
    }
}
