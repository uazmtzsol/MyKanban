package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardAdded;
import com.personalkanban.domain.board.CardId;

import java.util.Objects;

/**
 * Converts a checklist item into its own card (session 4.5, user request:
 * items promotable to cards). One undoable step: the new card is created in
 * the source card's column and the item disappears from its checklist.
 */
public final class ConvertChecklistItemCommand implements BoardCommand {

    private final CardId cardId;
    private final String itemId;

    private CardId createdCardId;

    public ConvertChecklistItemCommand(CardId cardId, String itemId) {
        this.cardId = Objects.requireNonNull(cardId);
        this.itemId = Objects.requireNonNull(itemId);
    }

    @Override
    public void execute(Board board) {
        CardAdded event = board.convertChecklistItemToCard(cardId, itemId);
        createdCardId = event.cardId();
    }

    public CardId createdCardId() {
        return Objects.requireNonNull(createdCardId, "command not executed yet");
    }
}
