package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ChecklistItem;

import java.util.Objects;

/** Adds a checklist item to one card (session 4.5), one undoable step. */
public final class AddChecklistItemCommand implements BoardCommand {

    private final CardId cardId;
    private final String text;
    private final String parentId;

    private String createdItemId;

    public AddChecklistItemCommand(CardId cardId, String text) {
        this(cardId, text, null);
    }

    /** Adds a subtask of {@code parentId} (null = top-level item, T16 · B8). */
    public AddChecklistItemCommand(CardId cardId, String text, String parentId) {
        this.cardId = Objects.requireNonNull(cardId);
        this.text = Objects.requireNonNull(text);
        this.parentId = parentId;
    }

    @Override
    public void execute(Board board) {
        ChecklistItem item = parentId == null
                ? board.addChecklistItem(cardId, text)
                : board.addChecklistItem(cardId, text, parentId);
        createdItemId = item.id();
    }

    public String createdItemId() {
        return Objects.requireNonNull(createdItemId, "command not executed yet");
    }
}
