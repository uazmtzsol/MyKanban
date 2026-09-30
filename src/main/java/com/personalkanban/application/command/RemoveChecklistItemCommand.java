package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;

import java.util.Objects;

/** Removes one checklist item (session 4.5), one undoable step. */
public final class RemoveChecklistItemCommand implements BoardCommand {

    private final CardId cardId;
    private final String itemId;

    public RemoveChecklistItemCommand(CardId cardId, String itemId) {
        this.cardId = Objects.requireNonNull(cardId);
        this.itemId = Objects.requireNonNull(itemId);
    }

    @Override
    public void execute(Board board) {
        board.removeChecklistItem(cardId, itemId);
    }
}
