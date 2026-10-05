package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ProcessId;

import java.util.List;
import java.util.Objects;

/**
 * Bulk: assigns the given cards to a process, or clears the
 * assignment of all of them when the process is null (multi-select
 * "Ninguno"). Unlike the single-card unassign, this keeps any
 * precedence links intact — a bulk pass must not silently destroy
 * relations between many cards. One undoable step.
 */
public final class BulkAssignProcessCommand implements BoardCommand {

    private final List<CardId> cardIds;
    private final ProcessId processId; // nullable on purpose: null = unassign

    public BulkAssignProcessCommand(List<CardId> cardIds, ProcessId processId) {
        this.cardIds = List.copyOf(Objects.requireNonNull(cardIds));
        this.processId = processId;
    }

    @Override
    public void execute(Board board) {
        for (CardId cardId : cardIds) {
            board.assignCardToProcess(cardId, processId);
        }
    }
}
