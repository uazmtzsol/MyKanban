package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;

import java.util.Objects;

/** Marks or unmarks one checklist item (session 4.5), one undoable step. */
public final class ToggleChecklistItemCommand implements BoardCommand {

    private final CardId cardId;
    private final String itemId;
    private final boolean done;

    public ToggleChecklistItemCommand(CardId cardId, String itemId, boolean done) {
        this.cardId = Objects.requireNonNull(cardId);
        this.itemId = Objects.requireNonNull(itemId);
        this.done = done;
    }

    @Override
    public void execute(Board board) {
        board.setChecklistItemDone(cardId, itemId, done);
    }
}
