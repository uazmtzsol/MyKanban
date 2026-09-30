package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;

import java.util.Objects;

/** Sets the plain-text notes of one card (session 4.4), one undoable step. */
public final class NoteCardCommand implements BoardCommand {

    private final CardId cardId;
    private final String notes;

    public NoteCardCommand(CardId cardId, String notes) {
        this.cardId = Objects.requireNonNull(cardId);
        this.notes = notes == null ? "" : notes;
    }

    @Override
    public void execute(Board board) {
        board.annotateCard(cardId, notes);
    }
}
