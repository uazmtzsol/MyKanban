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
