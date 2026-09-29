package com.personalkanban.application;

import com.personalkanban.application.port.InMemoryBoardRepository;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardColumn;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.WipLimit;
import com.personalkanban.domain.exception.WipLimitExceededException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardServiceTest {

    private final InMemoryBoardRepository repository = new InMemoryBoardRepository();
    private final BoardService service = new BoardService(repository);

    private ColumnId column(String title) {
        return service.addColumn(title, "", BoardColor.BLUE, WipLimit.unlimited());
    }

    @Test
    void persistsAfterEveryMutation() {
        ColumnId id = column("To Do");
        assertThat(repository.saveCalls).isEqualTo(1);

        service.addCard(id, "card", "", BoardColor.BLUE);
        assertThat(repository.saveCalls).isEqualTo(2);

        service.renameColumn(id, "Renamed");
        assertThat(repository.saveCalls).isEqualTo(3);
    }

    @Test
    void reloadsPersistedStateOnStartup() {
        ColumnId id = column("To Do");
        service.addCard(id, "card", "", BoardColor.BLUE);

        BoardService restarted = new BoardService(repository);
        assertThat(restarted.board().columnCount()).isEqualTo(1);
        assertThat(restarted.board().columns().get(0).title()).isEqualTo("To Do");
        assertThat(restarted.board().columns().get(0).cardCount()).isEqualTo(1);
    }

    @Test
    void failedMutationLeavesPersistenceUntouched() {
        ColumnId id = column("To Do");
        int savesBefore = repository.saveCalls;

        assertThatThrownBy(() -> service.addCard(id, "", "", BoardColor.BLUE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.saveCalls).isEqualTo(savesBefore);
    }

    @Test
    void wipViolationSurfacesFromService() {
        ColumnId id = service.addColumn("Limited", "", BoardColor.BLUE, WipLimit.of(1));
        service.addCard(id, "one", "", BoardColor.BLUE);

        assertThatThrownBy(() -> service.addCard(id, "two", "", BoardColor.BLUE))
                .isInstanceOf(WipLimitExceededException.class);
    }

    @Test
    void undoRestoresPreviousStateAndRedoReappliesIt() {
        ColumnId id = column("A");
        service.renameColumn(id, "B");
        assertThat(service.column(id).title()).isEqualTo("B");

        service.undo();
        assertThat(service.column(id).title()).isEqualTo("A");

        service.redo();
        assertThat(service.column(id).title()).isEqualTo("B");
        assertThat(service.canRedo()).isFalse();
    }

    @Test
    void newMutationDiscardsRedoBranch() {
        ColumnId id = column("A");
        service.renameColumn(id, "B");
        service.undo();
        assertThat(service.canRedo()).isTrue();

        service.renameColumn(id, "C");
        assertThat(service.canRedo()).isFalse();
    }

    @Test
    void undoRedoRoundTripsCards() {
        ColumnId a = column("A");
        ColumnId b = column("B");
        var card = service.addCard(a, "moving", "", BoardColor.BLUE);

        service.moveCard(card, b, 0);
        assertThat(service.board().findColumnOf(card).orElseThrow().id()).isEqualTo(b);

        service.undo();
        assertThat(service.board().findColumnOf(card).orElseThrow().id()).isEqualTo(a);

        service.redo();
        assertThat(service.board().findColumnOf(card).orElseThrow().id()).isEqualTo(b);
    }

    @Test
    void clearBoardIsUndoable() {
        ColumnId id = column("A");
        service.addCard(id, "one", "", BoardColor.BLUE);

        service.clearBoard();
        assertThat(service.board().columnCount()).isZero();

        service.undo();
        assertThat(service.board().columnCount()).isEqualTo(1);
        assertThat(service.column(id).cardCount()).isEqualTo(1);
    }
}
