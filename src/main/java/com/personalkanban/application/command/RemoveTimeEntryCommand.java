package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.EntryId;

import java.util.Objects;

/** Deletes one time-tracking entry of a card (session 6), one undoable step. */
public final class RemoveTimeEntryCommand implements BoardCommand {

    private final CardId cardId;
    private final EntryId entryId;

    public RemoveTimeEntryCommand(CardId cardId, EntryId entryId) {
        this.cardId = Objects.requireNonNull(cardId);
        this.entryId = Objects.requireNonNull(entryId);
    }

    @Override
    public void execute(Board board) {
        board.removeTimeEntry(cardId, entryId);
    }
}
