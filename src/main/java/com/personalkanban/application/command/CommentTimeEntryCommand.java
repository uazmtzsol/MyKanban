package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;

import java.util.Objects;

/** Sets the comment of the running time-tracking entry (session 6), undoable. */
public final class CommentTimeEntryCommand implements BoardCommand {

    private final CardId cardId;
    private final String comment;

    public CommentTimeEntryCommand(CardId cardId, String comment) {
        this.cardId = Objects.requireNonNull(cardId);
        this.comment = comment == null ? "" : comment;
    }

    @Override
    public void execute(Board board) {
        board.annotateRunningTimeEntry(cardId, comment);
    }
}
