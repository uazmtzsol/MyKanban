package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ProcessId;

import java.util.Objects;

/** Assigns one card to a process, or clears the assignment with null (4.6). */
public final class AssignProcessCommand implements BoardCommand {

    private final CardId cardId;
    private final ProcessId processId; // nullable on purpose: null = unassign

    public AssignProcessCommand(CardId cardId, ProcessId processId) {
        this.cardId = Objects.requireNonNull(cardId);
        this.processId = processId;
    }

    @Override
    public void execute(Board board) {
        board.assignCardToProcess(cardId, processId);
    }
}
