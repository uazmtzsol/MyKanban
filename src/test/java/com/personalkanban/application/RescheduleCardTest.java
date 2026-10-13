package com.personalkanban.application;

import com.personalkanban.application.port.InMemoryBoardRepository;
import com.personalkanban.application.port.InMemorySettingsStore;
import com.personalkanban.application.port.InMemoryUndoHistory;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quick reschedule (session 15, T5/B3): it changes the due date only, and it is
 * one undoable step so a mis-click is one Ctrl+Z away.
 */
class RescheduleCardTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);

    private final InMemoryBoardRepository repository = new InMemoryBoardRepository();
    private final InMemoryUndoHistory history = new InMemoryUndoHistory();
    private final InMemorySettingsStore settings = new InMemorySettingsStore();
    private final BoardService service = new BoardService(repository, history, settings);

    private CardId card;

    private void setUp(LocalDate initialDueDate) {
        BoardId board = service.createBoard("Reschedule");
        service.openBoard(board);
        ColumnId column = service.addColumn("To Do", "", BoardColor.DEFAULT, WipLimit.unlimited());
        card = service.addCard(column, "task", "descripción", BoardColor.DEFAULT);
        if (initialDueDate != null) {
            service.rescheduleCard(card, initialDueDate);
        }
    }

    @Test
    void reschedulingChangesTheDueDateInOneUndoableStep() {
        setUp(null);

        service.rescheduleCard(card, TODAY.plusDays(1));
        assertThat(dueDate()).isEqualTo(TODAY.plusDays(1));

        service.undo();
        assertThat(dueDate()).isNull();
    }

    @Test
    void reschedulingKeepsEveryOtherFieldOfTheCard() {
        setUp(null);
        var before = service.board().findCard(card).orElseThrow();

        service.rescheduleCard(card, TODAY.plusDays(7));

        var after = service.board().findCard(card).orElseThrow();
        assertThat(after.dueDate()).isEqualTo(TODAY.plusDays(7));
        assertThat(after.title()).isEqualTo(before.title());
        assertThat(after.description()).isEqualTo(before.description());
        assertThat(after.color()).isEqualTo(before.color());
        assertThat(after.labels()).isEqualTo(before.labels());
    }

    @Test
    void clearingTheDateIsAlsoUndoable() {
        setUp(TODAY);

        service.rescheduleCard(card, null);
        assertThat(dueDate()).isNull();

        service.undo();
        assertThat(dueDate()).isEqualTo(TODAY);
    }

    @Test
    void aRescheduleSurvivesReopeningTheBoard() {
        setUp(null);
        service.rescheduleCard(card, TODAY.plusDays(3));

        BoardId board = service.activeBoardId();
        service.openBoard(board);

        assertThat(dueDate()).isEqualTo(TODAY.plusDays(3));
    }

    private LocalDate dueDate() {
        return service.board().findCard(card).orElseThrow().dueDate();
    }
}
