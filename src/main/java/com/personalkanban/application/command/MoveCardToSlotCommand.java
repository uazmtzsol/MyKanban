package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ColumnId;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Moves a card into the visual slot between two neighbors captured at drag
 * time (see {@code Board#moveCardToSlot}); undoable like any other command.
 * Carries the day for the finalized-column auto-completion (T24 · B11).
 */
public final class MoveCardToSlotCommand implements BoardCommand {

    private final CardId cardId;
    private final ColumnId targetColumnId;
    private final int slotIndex;
    private final LocalDate today;

    public MoveCardToSlotCommand(CardId cardId, ColumnId targetColumnId, int slotIndex,
                                 LocalDate today) {
        this.cardId = Objects.requireNonNull(cardId);
        this.targetColumnId = Objects.requireNonNull(targetColumnId);
        this.slotIndex = slotIndex;
        this.today = Objects.requireNonNull(today);
    }

    @Override
    public void execute(Board board) {
        board.moveCardToSlot(cardId, targetColumnId, slotIndex, today);
    }
}
