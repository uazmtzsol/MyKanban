package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.CardAdded;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ColumnId;

import java.util.Objects;

/** Adds a card to a column; undoes by removing exactly the created card. */
public final class AddCardCommand implements BoardCommand {

    private final ColumnId columnId;
    private final String title;
    private final String description;
    private final BoardColor color;

    private CardId createdId;

    public AddCardCommand(ColumnId columnId, String title, String description, BoardColor color) {
        this.columnId = Objects.requireNonNull(columnId);
        this.title = Objects.requireNonNull(title);
        this.description = description == null ? "" : description;
        this.color = Objects.requireNonNull(color);
    }

    @Override
    public void execute(Board board) {
        CardAdded event = board.addCard(columnId, title, description, color);
        createdId = event.cardId();
    }

    public CardId createdCardId() {
        return Objects.requireNonNull(createdId, "command not executed yet");
    }
}
