package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;

import java.util.Objects;

/**
 * Indents one checklist item under {@code parentId} or outdents it (null
 * parent), in one undoable step (user request T16 · B8). One level is enforced
 * by the aggregate.
 */
public final class MoveChecklistItemCommand implements BoardCommand {

    private final CardId cardId;
    private final String itemId;
    private final String parentId;

    public MoveChecklistItemCommand(CardId cardId, String itemId, String parentId) {
        this.cardId = Objects.requireNonNull(cardId);
        this.itemId = Objects.requireNonNull(itemId);
        this.parentId = parentId;
    }

    @Override
    public void execute(Board board) {
        board.setChecklistItemParent(cardId, itemId, parentId);
    }
}
