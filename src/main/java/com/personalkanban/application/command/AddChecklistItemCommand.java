package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ChecklistItem;

import java.util.Objects;

/** Adds a checklist item to one card (session 4.5), one undoable step. */
public final class AddChecklistItemCommand implements BoardCommand {

    private final CardId cardId;
    private final String text;

    private String createdItemId;

    public AddChecklistItemCommand(CardId cardId, String text) {
        this.cardId = Objects.requireNonNull(cardId);
        this.text = Objects.requireNonNull(text);
    }

    @Override
    public void execute(Board board) {
        ChecklistItem item = board.addChecklistItem(cardId, text);
        createdItemId = item.id();
    }

    public String createdItemId() {
        return Objects.requireNonNull(createdItemId, "command not executed yet");
    }
}
