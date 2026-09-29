package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.CardId;

import java.util.Objects;

/** Edits title, description and color of a card in one undoable step. */
public final class EditCardCommand implements BoardCommand {

    private final CardId cardId;
    private final String newTitle;
    private final String newDescription;
    private final BoardColor newColor;

    public EditCardCommand(CardId cardId, String newTitle, String newDescription, BoardColor newColor) {
        this.cardId = Objects.requireNonNull(cardId);
        this.newTitle = Objects.requireNonNull(newTitle);
        this.newDescription = newDescription == null ? "" : newDescription;
        this.newColor = Objects.requireNonNull(newColor);
    }

    @Override
    public void execute(Board board) {
        board.editCard(cardId, newTitle, newDescription, newColor);
    }
}
