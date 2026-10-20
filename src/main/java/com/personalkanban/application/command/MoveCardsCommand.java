package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.CardId;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Bulk: moves several cards into the target column (appended in order). Carries
 * the day for the finalized-column auto-completion (T24 · B11).
 */
public final class MoveCardsCommand implements BoardCommand {

    private final List<CardId> cardIds;
    private final ColumnId targetColumnId;
    private final LocalDate today;

    public MoveCardsCommand(List<CardId> cardIds, ColumnId targetColumnId, LocalDate today) {
        this.cardIds = List.copyOf(cardIds);
        this.targetColumnId = targetColumnId;
        this.today = Objects.requireNonNull(today);
    }

    @Override
    public void execute(Board board) {
        board.moveCardsToColumn(cardIds, targetColumnId, today);
    }
}
