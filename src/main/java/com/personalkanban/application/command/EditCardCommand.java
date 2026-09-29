package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.CardId;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Edits all mutable card fields in one undoable step. */
public final class EditCardCommand implements BoardCommand {

    private final CardId cardId;
    private final String newTitle;
    private final String newDescription;
    private final BoardColor newColor;
    private final LocalDate newDueDate;
    private final List<String> newLabels;

    public EditCardCommand(CardId cardId, String newTitle, String newDescription, BoardColor newColor,
                           LocalDate newDueDate, List<String> newLabels) {
        this.cardId = Objects.requireNonNull(cardId);
        this.newTitle = Objects.requireNonNull(newTitle);
        this.newDescription = newDescription == null ? "" : newDescription;
        this.newColor = Objects.requireNonNull(newColor);
        this.newDueDate = newDueDate;
        this.newLabels = newLabels == null ? List.of() : List.copyOf(newLabels);
    }

    @Override
    public void execute(Board board) {
        board.editCard(cardId, newTitle, newDescription, newColor, newDueDate, newLabels);
    }
}
