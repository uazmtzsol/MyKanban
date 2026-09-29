package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ColumnId;

import java.util.Objects;

/** Moves a card to another column and/or index (drag & drop). */
public final class MoveCardCommand implements BoardCommand {

    private final CardId cardId;
    private final ColumnId targetColumnId;
    private final int targetIndex;

    public MoveCardCommand(CardId cardId, ColumnId targetColumnId, int targetIndex) {
        this.cardId = Objects.requireNonNull(cardId);
        this.targetColumnId = Objects.requireNonNull(targetColumnId);
        this.targetIndex = targetIndex;
    }

    @Override
    public void execute(Board board) {
        board.moveCard(cardId, targetColumnId, targetIndex);
    }
}
