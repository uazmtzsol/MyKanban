package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;

import java.util.Objects;

/** Toggles a single label on a single card (system flags, one undoable step). */
public final class ToggleLabelCommand implements BoardCommand {

    private final CardId cardId;
    private final String label;

    public ToggleLabelCommand(CardId cardId, String label) {
        this.cardId = Objects.requireNonNull(cardId);
        this.label = Objects.requireNonNull(label);
    }

    @Override
    public void execute(Board board) {
        board.toggleLabel(cardId, label);
    }
}
