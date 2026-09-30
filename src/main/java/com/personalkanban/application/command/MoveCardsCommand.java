package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.CardId;

import java.util.List;

/** Bulk: moves several cards into the target column (appended in order). */
public final class MoveCardsCommand implements BoardCommand {

    private final List<CardId> cardIds;
    private final ColumnId targetColumnId;

    public MoveCardsCommand(List<CardId> cardIds, ColumnId targetColumnId) {
        this.cardIds = List.copyOf(cardIds);
        this.targetColumnId = targetColumnId;
    }

    @Override
    public void execute(Board board) {
        board.moveCardsToColumn(cardIds, targetColumnId);
    }
}
