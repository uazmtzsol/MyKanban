package com.personalkanban.application;

import com.personalkanban.application.port.InMemoryBoardRepository;
import com.personalkanban.application.port.InMemorySettingsStore;
import com.personalkanban.application.port.InMemoryUndoHistory;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardDescriptor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.WipLimit;
import com.personalkanban.domain.exception.WipLimitExceededException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardServiceTest {

    private final InMemoryBoardRepository repository = new InMemoryBoardRepository();
    private final InMemoryUndoHistory history = new InMemoryUndoHistory();
    private final InMemorySettingsStore settings = new InMemorySettingsStore();
    private final BoardService service = new BoardService(repository, history, settings);

    private BoardId addBoard(String name) {
        BoardId id = service.createBoard(name);
        service.openBoard(id);
        return id;
    }

    // ------------------------------------------------------------------
    // Multi-board
    // ------------------------------------------------------------------

    @Test
    void startsWithOneDefaultBoard() {
        assertThat(service.boards()).hasSize(1);
        assertThat(service.board().columnCount()).isZero();
    }

    @Test
    void createsOpensAndListsBoardsIndependently() {
        BoardId personal = addBoard("Personal");
        var personalColumn = service.addColumn("Personal", "", BoardColor.BLUE, WipLimit.unlimited());

        BoardId work = addBoard("Work");
        service.addColumn("Work", "", BoardColor.GREEN, WipLimit.unlimited());

        assertThat(service.boards()).extracting(BoardDescriptor::name)
                .containsExactly("My Board", "Personal", "Work");

        service.openBoard(personal);
        assertThat(service.board().columns()).extracting(com.personalkanban.domain.board.BoardColumn::title)
                .containsExactly("Personal");

        service.openBoard(work);
        assertThat(service.board().columns()).extracting(com.personalkanban.domain.board.BoardColumn::title)
                .containsExactly("Work");
    }

    @Test
    void remembersLastOpenedBoardAcrossRestarts() {
        BoardId work = addBoard("Work");
        // Simulate a restart with the same stores.
        BoardService restarted = new BoardService(repository, history, settings);
        assertThat(restarted.activeBoardId()).isEqualTo(work);
    }

    @Test
    void renamesAndDeletesBoards() {
        BoardId personal = addBoard("Personal");
        service.renameBoard(personal, "Casa");
        assertThat(service.boards()).extracting(BoardDescriptor::name).contains("Casa");

        BoardId second = addBoard("Second");
        service.deleteBoard(second);
        assertThat(service.boards()).extracting(BoardDescriptor::name).doesNotContain("Second");

        // Reduce to a single board; deleting the last one must be refused.
        service.deleteBoard(service.activeBoardId());
        assertThat(service.boards()).hasSize(1);
        assertThatThrownBy(() -> service.deleteBoard(service.activeBoardId()))
                .isInstanceOf(IllegalStateException.class);
    }

    // ------------------------------------------------------------------
    // Persistence & undo
    // ------------------------------------------------------------------

    @Test
    void persistsAfterEveryMutation() {
        var id = service.addColumn("To Do", "", BoardColor.BLUE, WipLimit.unlimited());
        int savesBefore = repository.saveCalls;
        service.addCard(id, "card", "", BoardColor.BLUE);
        assertThat(repository.saveCalls).isEqualTo(savesBefore + 1);
    }

    @Test
    void undoRestoresPreviousStateAndRedoReappliesIt() {
        var id = service.addColumn("A", "", BoardColor.BLUE, WipLimit.unlimited());
        service.renameColumn(id, "B");
        assertThat(service.column(id).title()).isEqualTo("B");

        service.undo();
        assertThat(service.column(id).title()).isEqualTo("A");

        service.redo();
        assertThat(service.column(id).title()).isEqualTo("B");
        assertThat(service.canRedo()).isFalse();
    }

    @Test
    void undoHistoryIsPerBoard() {
        var colA = service.addColumn("A", "", BoardColor.BLUE, WipLimit.unlimited());
        service.renameColumn(colA, "A2");

        BoardId work = addBoard("Work");
        var colW = service.addColumn("W", "", BoardColor.BLUE, WipLimit.unlimited());
        service.renameColumn(colW, "W2");

        // Undo on Work must not touch the first board.
        service.undo();
        assertThat(service.column(colW).title()).isEqualTo("W");

        service.openBoard(service.boards().get(0).id());
        assertThat(service.column(colA).title()).isEqualTo("A2");
    }

    @Test
    void newMutationDiscardsRedoBranch() {
        var id = service.addColumn("A", "", BoardColor.BLUE, WipLimit.unlimited());
        service.renameColumn(id, "B");
        service.undo();
        assertThat(service.canRedo()).isTrue();

        service.renameColumn(id, "C");
        assertThat(service.canRedo()).isFalse();
    }

    // ------------------------------------------------------------------
    // Card fields
    // ------------------------------------------------------------------

    @Test
    void cardsCarryDueDateAndLabels() {
        var col = service.addColumn("A", "", BoardColor.BLUE, WipLimit.unlimited());
        var due = LocalDate.of(2026, 12, 24);
        var cardId = service.addCard(col, "gift", "", BoardColor.PINK, due, List.of("home", "urgent"));

        var card = service.board().findCard(cardId).orElseThrow();
        assertThat(card.dueDate()).isEqualTo(due);
        assertThat(card.labels()).containsExactly("home", "urgent");

        service.editCard(cardId, "gift", "", BoardColor.PINK, null, List.of("home"));
        assertThat(service.board().findCard(cardId).orElseThrow().dueDate()).isNull();
    }

    @Test
    void wipViolationSurfacesFromService() {
        var id = service.addColumn("Limited", "", BoardColor.BLUE, WipLimit.of(1));
        service.addCard(id, "one", "", BoardColor.BLUE);
        assertThatThrownBy(() -> service.addCard(id, "two", "", BoardColor.BLUE))
                .isInstanceOf(WipLimitExceededException.class);
    }

    // ------------------------------------------------------------------
    // Persistence failures: memory must stay consistent with the database
    // ------------------------------------------------------------------

    /** Repository whose save() fails while a flag is set (delegating wrapper). */
    private static final class FailingSaveRepository implements com.personalkanban.application.port.BoardRepository {

        private final com.personalkanban.application.port.InMemoryBoardRepository delegate =
                new com.personalkanban.application.port.InMemoryBoardRepository();
        private boolean failNextSave;

        @Override
        public java.util.List<BoardDescriptor> listBoards() {
            return delegate.listBoards();
        }

        @Override
        public BoardDescriptor createBoard(String name) {
            return delegate.createBoard(name);
        }

        @Override
        public void renameBoard(BoardId boardId, String newName) {
            delegate.renameBoard(boardId, newName);
        }

        @Override
        public void deleteBoard(BoardId boardId) {
            delegate.deleteBoard(boardId);
        }

        @Override
        public com.personalkanban.domain.board.BoardMemento load(BoardId boardId) {
            return delegate.load(boardId);
        }

        @Override
        public void save(BoardId boardId, com.personalkanban.domain.board.BoardMemento board) {
            if (failNextSave) {
                failNextSave = false;
                throw new IllegalStateException("disk on fire");
            }
            delegate.save(boardId, board);
        }
    }

    @Test
    void failedSaveRollsBackModelAndLeavesUndoHistoryClean() {
        FailingSaveRepository failing = new FailingSaveRepository();
        BoardService service = new BoardService(failing, history, settings);
        var id = service.addColumn("A", "", BoardColor.BLUE, WipLimit.unlimited());

        failing.failNextSave = true;
        assertThatThrownBy(() -> service.renameColumn(id, "B"))
                .isInstanceOf(RuntimeException.class);

        // The in-memory model shows the OLD title; nothing was persisted.
        assertThat(service.column(id).title()).isEqualTo("A");
        assertThat(failing.load(service.activeBoardId()).columns().get(0).title())
                .isEqualTo("A");
        // The failed transaction adds no undo entry (depth unchanged).
        assertThat(history.depth(service.activeBoardId())).isEqualTo(1);

        // The service keeps working once the failure is gone.
        service.renameColumn(id, "B");
        assertThat(service.column(id).title()).isEqualTo("B");
    }

    @Test
    void failedUndoKeepsCurrentStateAndHistoryEntry() {
        FailingSaveRepository failing = new FailingSaveRepository();
        BoardService service = new BoardService(failing, history, settings);
        var id = service.addColumn("A", "", BoardColor.BLUE, WipLimit.unlimited());
        service.renameColumn(id, "B");
        assertThat(service.canUndo()).isTrue();

        failing.failNextSave = true;
        assertThatThrownBy(service::undo).isInstanceOf(RuntimeException.class);

        // Still showing B; the undo entry is back on the stack, retryable.
        assertThat(service.column(id).title()).isEqualTo("B");
        assertThat(service.canUndo()).isTrue();

        failing.failNextSave = false;
        service.undo();
        assertThat(service.column(id).title()).isEqualTo("A");
    }

    // ------------------------------------------------------------------
    // Delete-board repro (user report: deleting an empty board "did nothing")
    // ------------------------------------------------------------------

    @Test
    void reproDeleteActiveEmptyBoardExactUiSequence() {
        // Sequence: app starts with "My Board"; user creates an empty board
        // (it becomes active), then Boards > Delete board, confirms.
        BoardId myBoard = service.activeBoardId();
        BoardId empty = service.createBoard("Prueba vacia");
        service.openBoard(empty);

        BoardId capturedByUi = service.activeBoardId(); // what onDeleteBoard captures
        assertThat(service.boards().size() > 1).isTrue(); // the guard passes

        service.deleteBoard(capturedByUi); // what the confirm callback runs
        assertThat(service.boards()).extracting(BoardDescriptor::name)
                .doesNotContain("Prueba vacia");
        assertThat(service.activeBoardId()).isEqualTo(myBoard);
    }

    @Test
    void reproDeleteBoardWithColumnsCardsAndUndoHistory() {
        BoardId myBoard = service.activeBoardId();
        BoardId full = service.createBoard("Con contenido");
        service.openBoard(full);
        var col = service.addColumn("C", "", BoardColor.BLUE, WipLimit.unlimited());
        service.addCard(col, "t", "", BoardColor.BLUE);
        service.renameColumn(col, "C2"); // undo history entry for this board

        service.deleteBoard(full);

        assertThat(service.boards()).hasSize(1);
        assertThat(service.activeBoardId()).isEqualTo(myBoard);
        // Deleted board must not resurrect via restart simulation.
        BoardService restarted = new BoardService(repository, history, settings);
        assertThat(restarted.boards()).extracting(BoardDescriptor::name).hasSize(1);
    }

    // ------------------------------------------------------------------
    // Bulk (multi-selection) use cases
    // ------------------------------------------------------------------

    @Test
    void bulkMoveIsAtomicWhenTargetWipWouldBeExceeded() {
        var target = service.addColumn("Target", "", BoardColor.BLUE, WipLimit.of(2));
        service.addCard(target, "t1", "", BoardColor.BLUE);
        var source = service.addColumn("Source", "", BoardColor.GREEN, WipLimit.unlimited());
        var c1 = service.addCard(source, "s1", "", BoardColor.BLUE);
        var c2 = service.addCard(source, "s2", "", BoardColor.BLUE);

        // 1 occupied + 2 moving > 2 → whole batch refused, memory intact.
        assertThatThrownBy(() -> service.moveCardsToColumn(List.of(c1, c2), target))
                .isInstanceOf(RuntimeException.class);
        assertThat(service.board().findCard(c1)).isPresent();
        assertThat(service.board().findCard(c2)).isPresent();
        assertThat(service.column(target).cardCount()).isEqualTo(1);
        // A refused transaction must not enter the undo history: the depth
        // still equals 2 columns + 3 addCard commands, nothing more.
        assertThat(history.depth(service.activeBoardId())).isEqualTo(5);
    }

    @Test
    void bulkRecolorIsUndoableAsOneStep() {
        var col = service.addColumn("A", "", BoardColor.BLUE, WipLimit.unlimited());
        var c1 = service.addCard(col, "one", "", BoardColor.BLUE);
        var c2 = service.addCard(col, "two", "", BoardColor.BLUE);

        service.recolorCards(List.of(c1, c2), BoardColor.PINK);
        assertThat(service.board().findCard(c1).orElseThrow().color()).isEqualTo(BoardColor.PINK);

        service.undo();
        assertThat(service.board().findCard(c1).orElseThrow().color()).isEqualTo(BoardColor.BLUE);
        assertThat(service.board().findCard(c2).orElseThrow().color()).isEqualTo(BoardColor.BLUE);
    }

    @Test
    void bulkAddAndRemoveLabelsRoundTrip() {
        var col = service.addColumn("A", "", BoardColor.BLUE, WipLimit.unlimited());
        var c1 = service.addCard(col, "one", "", BoardColor.BLUE, null, List.of("uaz"));
        var c2 = service.addCard(col, "two", "", BoardColor.BLUE);

        service.addLabelsToCards(List.of(c1, c2), List.of("uaz", "urgent"));
        // c1 already had "uaz" (case-insensitive dedup keeps one chip).
        assertThat(service.board().findCard(c1).orElseThrow().labels())
                .containsExactly("uaz", "urgent");
        assertThat(service.board().findCard(c2).orElseThrow().labels())
                .containsExactly("uaz", "urgent");

        service.removeLabelsFromCards(List.of(c1, c2), List.of("UAZ"));
        assertThat(service.board().findCard(c1).orElseThrow().labels())
                .containsExactly("urgent");
        assertThat(service.board().findCard(c2).orElseThrow().labels()).containsExactly("urgent");
    }

    @Test
    void collapsedColumnPreferencePersistsPerBoard() {
        BoardId first = addBoard("Uno");
        var col = service.addColumn("A", "", BoardColor.BLUE, WipLimit.unlimited());
        BoardId second = addBoard("Dos");

        assertThat(service.collapsedColumnsOf(first)).isEmpty();
        service.setCollapsedColumns(first, java.util.Set.of(col.value(), "ghost-id"));

        assertThat(service.collapsedColumnsOf(first))
                .containsExactlyInAnyOrder(col.value(), "ghost-id");
        assertThat(service.collapsedColumnsOf(second)).isEmpty(); // per-board isolation

        // Clearing works too (empty set stored as empty string).
        service.setCollapsedColumns(first, java.util.Set.of());
        assertThat(service.collapsedColumnsOf(first)).isEmpty();
    }

    // ------------------------------------------------------------------
    // JSON export / import
    // ------------------------------------------------------------------

    @Test
    void exportThenImportCreatesAnIndependentCopy(@TempDir Path tempDir) throws Exception {
        var col = service.addColumn("To Do", "", BoardColor.BLUE, WipLimit.unlimited());
        service.addCard(col, "task", "desc", BoardColor.TEAL, LocalDate.of(2027, 1, 1), List.of("x"));

        Path file = tempDir.resolve("board.json");
        service.exportBoard(file);
        assertThat(file).exists();

        BoardId imported = service.importBoard(file);
        assertThat(service.boards()).extracting(BoardDescriptor::name).contains("My Board");

        service.openBoard(imported);
        assertThat(service.board().columnCount()).isEqualTo(1);
        assertThat(service.board().cardCount()).isEqualTo(1);
        var card = service.board().columns().get(0).cards().get(0);
        assertThat(card.dueDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(card.labels()).containsExactly("x");

        // Independence: mutating the copy must not change the original.
        service.clearBoard();
        service.openBoard(service.boards().get(0).id());
        assertThat(service.board().cardCount()).isEqualTo(1);
    }

    @Test
    void importRejectsInvalidFiles(@TempDir Path tempDir) throws Exception {
        Path bogus = tempDir.resolve("bogus.json");
        Files.writeString(bogus, "{\"payload\": {\"name\": \"no board here\"}}");
        assertThatThrownBy(() -> service.importBoard(bogus))
                .isInstanceOf(IllegalArgumentException.class);

        Path garbage = tempDir.resolve("garbage.json");
        Files.writeString(garbage, "not json at all");
        assertThatThrownBy(() -> service.importBoard(garbage))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void importedNameCollisionsGetUniqueNames(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("board.json");
        service.exportBoard(file);

        BoardId first = service.importBoard(file);
        BoardId second = service.importBoard(file);

        BoardId originalId = service.boards().get(0).id();
        String originalName = service.boards().stream()
                .filter(descriptor -> descriptor.id().equals(originalId))
                .findFirst().orElseThrow().name();

        assertThat(service.boards()).extracting(BoardDescriptor::name)
                .contains(originalName, originalName + " (2)", originalName + " (3)");
        assertThat(first).isNotEqualTo(second);
    }
}
