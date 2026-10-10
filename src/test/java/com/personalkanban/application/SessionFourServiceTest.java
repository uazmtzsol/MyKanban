package com.personalkanban.application;

import com.personalkanban.application.port.InMemoryBoardRepository;
import com.personalkanban.application.port.InMemorySettingsStore;
import com.personalkanban.application.port.InMemoryUndoHistory;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Service-level tests for the session-4 use cases: every mutation goes
 * through one undoable transaction (persist-first).
 */
class SessionFourServiceTest {

    private final InMemoryBoardRepository repository = new InMemoryBoardRepository();
    private final InMemoryUndoHistory history = new InMemoryUndoHistory();
    private final InMemorySettingsStore settings = new InMemorySettingsStore();
    private final BoardService service = new BoardService(repository, history, settings);

    private BoardId board;

    private void setUp() {
        board = service.createBoard("Session 4");
        service.openBoard(board);
        service.addColumn("To Do", "", BoardColor.DEFAULT, WipLimit.unlimited());
    }

    @Test
    void notesAreUndoable() {
        setUp();
        var columnId = service.board().columns().get(0).id();
        var cardId = service.addCard(columnId, "task", "", BoardColor.DEFAULT);

        service.setCardNotes(cardId, "plain notes");
        assertThat(service.board().findCard(cardId).orElseThrow().notes()).isEqualTo("plain notes");

        service.undo();
        assertThat(service.board().findCard(cardId).orElseThrow().notes()).isEmpty();
    }

    @Test
    void checklistLifecycleIsUndoableStepByStep() {
        setUp();
        var columnId = service.board().columns().get(0).id();
        var cardId = service.addCard(columnId, "task", "", BoardColor.DEFAULT);

        String itemId = service.addChecklistItem(cardId, "step one");
        assertThat(service.board().findCard(cardId).orElseThrow().checklist()).hasSize(1);

        service.undo(); // item removed again
        assertThat(service.board().findCard(cardId).orElseThrow().checklist()).isEmpty();

        service.redo(); // and back
        assertThat(service.board().findCard(cardId).orElseThrow().checklist()).hasSize(1);

        service.setChecklistItemDone(cardId, itemId, true);
        assertThat(service.board().findCard(cardId).orElseThrow().doneChecklistCount()).isEqualTo(1);

        service.undo();
        assertThat(service.board().findCard(cardId).orElseThrow().doneChecklistCount()).isZero();
    }

    @Test
    void conversionCreatesCardAndUndoRestoresItem() {
        setUp();
        var columnId = service.board().columns().get(0).id();
        var sourceId = service.addCard(columnId, "source", "", BoardColor.DEFAULT);
        var itemId = service.addChecklistItem(sourceId, "become a card");

        var createdId = service.convertChecklistItemToCard(sourceId, itemId);
        assertThat(service.board().cardCount()).isEqualTo(2);
        assertThat(service.board().findCard(sourceId).orElseThrow().checklist()).isEmpty();

        service.undo();
        assertThat(service.board().cardCount()).isEqualTo(1);
        assertThat(service.board().findCard(sourceId).orElseThrow().checklist())
                .extracting(item -> item.text())
                .containsExactly("become a card");
        assertThat(history.depth(service.activeBoardId())).isEqualTo(3); // add + checklist + convert
    }

    @Test
    void processLifecycleIsUndoable() {
        setUp();
        var columnId = service.board().columns().get(0).id();
        var cardId = service.addCard(columnId, "task", "", BoardColor.DEFAULT);

        var processId = service.addProcess("Mudanza");
        service.assignCardToProcess(cardId, processId);
        assertThat(service.board().cardsOfProcess(processId)).hasSize(1);

        service.undo(); // unassign
        assertThat(service.board().findCard(cardId).orElseThrow().processId()).isNull();

        service.redo();
        assertThat(service.board().findCard(cardId).orElseThrow().processId()).isEqualTo(processId);

        service.removeProcess(processId);
        assertThat(service.processes()).isEmpty();
        assertThat(service.board().findCard(cardId).orElseThrow().processId()).isNull();

        service.undo(); // process back, card re-assigned
        assertThat(service.processes()).extracting(p -> p.name()).containsExactly("Mudanza");
        assertThat(service.board().findCard(cardId).orElseThrow().processId()).isEqualTo(processId);
    }

    @Test
    void linksRejectCyclesAndUndoCoversLinking() {
        setUp();
        var columnId = service.board().columns().get(0).id();
        var a = service.addCard(columnId, "A", "", BoardColor.DEFAULT);
        var b = service.addCard(columnId, "B", "", BoardColor.DEFAULT);

        service.linkCards(a, b);
        assertThat(service.board().linkList()).hasSize(1);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.linkCards(b, a))
                .isInstanceOf(com.personalkanban.domain.exception.CyclicDependencyException.class);

        service.undo();
        assertThat(service.board().linkList()).isEmpty();
        service.redo();
        assertThat(service.board().linkList()).hasSize(1);
    }

    @Test
    void suggestedOrderReturnsTopologicalResult() {
        setUp();
        var columnId = service.board().columns().get(0).id();
        var a = service.addCard(columnId, "A", "", BoardColor.DEFAULT);
        var b = service.addCard(columnId, "B", "", BoardColor.DEFAULT);
        var c = service.addCard(columnId, "C", "", BoardColor.DEFAULT);
        service.linkCards(a, b);
        service.linkCards(b, c);

        var result = service.suggestedOrder(List.of(c, b, a));
        assertThat(result.ordered()).containsExactly(a, b, c);
        assertThat(result.cycleRemaining()).isEmpty();
    }

    @Test
    void persistedStateSurvivesRepositoryRoundTrip() {
        setUp();
        var columnId = service.board().columns().get(0).id();
        var processId = service.addProcess("P");
        var a = service.addCard(columnId, "A", "", BoardColor.DEFAULT,
                null, List.of(), "some notes", null, processId);
        var b = service.addCard(columnId, "B", "", BoardColor.DEFAULT);
        service.linkCards(a, b);

        // Save through the repository and load back: notes, process and the
        // link must all survive the replaceAll round trip.
        var loaded = repository.load(board);
        assertThat(loaded.columns().get(0).cards()).hasSize(2);
        var loadedA = loaded.columns().get(0).cards().stream()
                .filter(card -> card.id().equals(a)).findFirst().orElseThrow();
        assertThat(loadedA.notes()).isEqualTo("some notes");
        assertThat(loadedA.processId()).isEqualTo(processId);
        assertThat(loaded.links()).hasSize(1);
        assertThat(loaded.processes()).extracting(p -> p.name()).containsExactly("P");
    }
}
