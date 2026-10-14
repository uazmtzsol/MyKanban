package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;

import java.util.List;
import java.util.Objects;

/**
 * Marks every reminder of the given cards as attended in <b>one</b> undoable
 * step (T23 · B10): the startup notice's "marcar todo como atendido" button,
 * so a backlog of owed reminders is one Ctrl+Z and not one per card.
 */
public final class AcknowledgeAllRemindersCommand implements BoardCommand {

    private final List<CardId> cardIds;

    public AcknowledgeAllRemindersCommand(List<CardId> cardIds) {
        Objects.requireNonNull(cardIds, "cardIds");
        this.cardIds = List.copyOf(cardIds);
    }

    @Override
    public void execute(Board board) {
        for (CardId cardId : cardIds) {
            board.acknowledgeAllReminders(cardId);
        }
    }
}
