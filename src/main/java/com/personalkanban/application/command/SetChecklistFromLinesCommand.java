package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.CardId;

import java.util.List;
import java.util.Objects;

/**
 * Replaces a card's checklist with the given plain lines (user request:
 * advanced card form shows the checklist as text lines). "[x] t" = done,
 * "[ ] t"/"t" = pending; existing items are matched by text so their
 * identity survives. One undoable step.
 */
public final class SetChecklistFromLinesCommand implements BoardCommand {

    private final CardId cardId;
    private final List<String> lines;

    public SetChecklistFromLinesCommand(CardId cardId, List<String> lines) {
        this.cardId = Objects.requireNonNull(cardId);
        this.lines = lines == null ? List.of() : List.copyOf(lines);
    }

    @Override
    public void execute(Board board) {
        board.setChecklistFromLines(cardId, lines);
    }
}
