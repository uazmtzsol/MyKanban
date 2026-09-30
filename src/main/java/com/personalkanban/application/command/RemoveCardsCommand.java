package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;

import java.util.List;

/** Bulk: removes several cards at once. */
public final class RemoveCardsCommand implements BoardCommand {

    private final List<CardId> cardIds;

    public RemoveCardsCommand(List<CardId> cardIds) {
        this.cardIds = List.copyOf(cardIds);
    }

    @Override
    public void execute(Board board) {
        board.removeCards(cardIds);
    }
}
