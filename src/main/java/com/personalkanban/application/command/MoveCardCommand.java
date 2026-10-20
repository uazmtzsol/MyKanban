package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ColumnId;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Moves a card to another column and/or index (drag & drop). Carries the day
 * so entering/leaving the board's finalized column can finish or reopen the
 * card inside the same undo step (T24 · B11).
 */
public final class MoveCardCommand implements BoardCommand {

    private final CardId cardId;
    private final ColumnId targetColumnId;
    private final int targetIndex;
    private final LocalDate today;

    public MoveCardCommand(CardId cardId, ColumnId targetColumnId, int targetIndex,
                           LocalDate today) {
        this.cardId = Objects.requireNonNull(cardId);
        this.targetColumnId = Objects.requireNonNull(targetColumnId);
        this.targetIndex = targetIndex;
        this.today = Objects.requireNonNull(today);
    }

    @Override
    public void execute(Board board) {
        board.moveCard(cardId, targetColumnId, targetIndex, today);
    }
}
