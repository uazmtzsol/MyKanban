package com.personalkanban.application.command;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.CardAdded;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ChecklistItem;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.ProcessId;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Adds a card to a column; undoes by removing exactly the created card. */
public final class AddCardCommand implements BoardCommand {

    private final ColumnId columnId;
    private final String title;
    private final String description;
    private final BoardColor color;
    private final LocalDate dueDate;
    private final List<String> labels;
    private final String notes;                    // session 4.4
    private final List<ChecklistItem> checklist;   // session 4.5 (nullable)
    private final ProcessId processId;             // session 4.6 (nullable)

    private CardId createdId;

    public AddCardCommand(ColumnId columnId, String title, String description, BoardColor color,
                          LocalDate dueDate, List<String> labels) {
        this(columnId, title, description, color, dueDate, labels, null, null, null);
    }

    public AddCardCommand(ColumnId columnId, String title, String description, BoardColor color,
                          LocalDate dueDate, List<String> labels,
                          String notes, List<ChecklistItem> checklist, ProcessId processId) {
        this.columnId = Objects.requireNonNull(columnId);
        this.title = Objects.requireNonNull(title);
        this.description = description == null ? "" : description;
        this.color = Objects.requireNonNull(color);
        this.dueDate = dueDate;
        this.labels = labels == null ? List.of() : List.copyOf(labels);
        this.notes = notes == null ? "" : notes;
        this.checklist = checklist == null ? null : List.copyOf(checklist);
        this.processId = processId;
    }

    @Override
    public void execute(Board board) {
        CardAdded event = board.addCard(columnId, title, description, color, dueDate, labels,
                notes, checklist, processId);
        createdId = event.cardId();
    }

    public CardId createdCardId() {
        return Objects.requireNonNull(createdId, "command not executed yet");
    }
}
