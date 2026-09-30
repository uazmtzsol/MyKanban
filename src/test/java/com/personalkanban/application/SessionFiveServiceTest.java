package com.personalkanban.application;

import com.personalkanban.application.port.InMemoryBoardRepository;
import com.personalkanban.application.port.InMemorySettingsStore;
import com.personalkanban.application.port.InMemoryUndoHistory;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Service-level tests for the session-5 use cases: priority sort and
 * checklist-from-lines are single undoable transactions.
 */
class SessionFiveServiceTest {

    private final InMemoryBoardRepository repository = new InMemoryBoardRepository();
    private final InMemoryUndoHistory history = new InMemoryUndoHistory();
    private final InMemorySettingsStore settings = new InMemorySettingsStore();
    private final BoardService service = new BoardService(repository, history, settings);

    private BoardId board;

    private void setUp() {
        board = service.createBoard("Session 5");
        service.openBoard(board);
        service.addColumn("To Do", "", BoardColor.DEFAULT, WipLimit.unlimited());
    }

    @Test
    void prioritySortIsUndoableInOneStep() {
        setUp();
        var columnId = service.board().columns().get(0).id();
        var plain = service.addCard(columnId, "plain", "", BoardColor.DEFAULT);
        var urgent = service.addCard(columnId, "urgent", "", BoardColor.DEFAULT);
        service.toggleCardLabel(urgent, Card.LABEL_URGENT);

        service.sortColumnByPriority(columnId);
        assertThat(order()).containsExactly("urgent", "plain");

        service.undo();
        assertThat(order()).containsExactly("plain", "urgent");
        // addColumn + addCard + toggle + sort: each pushed one undo entry.
        assertThat(history.depth(service.activeBoardId())).isEqualTo(4);
    }

    @Test
    void checklistFromLinesIsUndoableInOneStep() {
        setUp();
        var columnId = service.board().columns().get(0).id();
        var cardId = service.addCard(columnId, "task", "", BoardColor.DEFAULT);

        service.setChecklistFromLines(cardId, java.util.List.of("alpha", "beta"));
        assertThat(service.board().findCard(cardId).orElseThrow().checklist()).hasSize(2);

        service.undo();
        assertThat(service.board().findCard(cardId).orElseThrow().checklist()).isEmpty();
    }

    private java.util.List<String> order() {
        return service.board().columns().get(0).cards().stream()
                .map(Card::title).toList();
    }
}
