package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;

import java.util.Objects;

/** Removes a single card from the board. */
public final class RemoveCardCommand implements BoardCommand {

    private final CardId cardId;

    public RemoveCardCommand(CardId cardId) {
        this.cardId = Objects.requireNonNull(cardId);
    }

    @Override
    public void execute(Board board) {
        board.removeCard(cardId);
    }
}
