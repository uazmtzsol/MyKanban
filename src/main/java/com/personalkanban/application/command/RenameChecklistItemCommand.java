package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;

import java.util.Objects;

/** Renames one checklist item (session 4.5), one undoable step. */
public final class RenameChecklistItemCommand implements BoardCommand {

    private final CardId cardId;
    private final String itemId;
    private final String newText;

    public RenameChecklistItemCommand(CardId cardId, String itemId, String newText) {
        this.cardId = Objects.requireNonNull(cardId);
        this.itemId = Objects.requireNonNull(itemId);
        this.newText = Objects.requireNonNull(newText);
    }

    @Override
    public void execute(Board board) {
        board.renameChecklistItem(cardId, itemId, newText);
    }
}
