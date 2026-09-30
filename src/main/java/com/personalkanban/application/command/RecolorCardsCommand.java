package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.CardId;

import java.util.List;

/** Bulk: recolors several cards at once. */
public final class RecolorCardsCommand implements BoardCommand {

    private final List<CardId> cardIds;
    private final BoardColor color;

    public RecolorCardsCommand(List<CardId> cardIds, BoardColor color) {
        this.cardIds = List.copyOf(cardIds);
        this.color = color;
    }

    @Override
    public void execute(Board board) {
        board.recolorCards(cardIds, color);
    }
}
