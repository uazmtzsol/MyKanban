package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.CardAdded;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ColumnId;

import java.util.Objects;

/**
 * Duplicates a card of the active board into a column (the copy/paste-card
 * shortcut, user request T10): title, description, color, due date, labels,
 * notes, checklist and process travel along; time entries, reminders and
 * precedence links do not (same criteria as moving a card between boards).
 * Undo removes exactly the created card.
 */
public final class DuplicateCardCommand implements BoardCommand {

    private final CardId sourceCardId;
    private final ColumnId targetColumnId;

    private CardId createdId;

    public DuplicateCardCommand(CardId sourceCardId, ColumnId targetColumnId) {
        this.sourceCardId = Objects.requireNonNull(sourceCardId);
        this.targetColumnId = Objects.requireNonNull(targetColumnId);
    }

    @Override
    public void execute(Board board) {
        Card source = board.findCard(sourceCardId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown card: " + sourceCardId));
        CardAdded event = board.addCard(targetColumnId, source.title(), source.description(),
                source.color(), source.dueDate(), source.labels(), source.notes(),
                source.checklist(), source.processId());
        createdId = event.cardId();
    }

    public CardId createdCardId() {
        return Objects.requireNonNull(createdId, "command not executed yet");
    }
}
