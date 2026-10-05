package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;

import java.util.Objects;

/**
 * Removes a card from its process and deletes every precedence link
 * touching it (incoming and outgoing), so neighbors stop pointing at
 * it and it stops pointing at them (A → X → B becomes A and B with
 * no relation). One undoable step.
 */
public final class UnassignFromProcessCommand implements BoardCommand {

    private final CardId cardId;

    public UnassignFromProcessCommand(CardId cardId) {
        this.cardId = Objects.requireNonNull(cardId);
    }

    @Override
    public void execute(Board board) {
        board.unassignCardFromProcess(cardId);
    }
}
