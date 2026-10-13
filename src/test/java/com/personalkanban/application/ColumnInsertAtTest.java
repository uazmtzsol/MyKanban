package com.personalkanban.application;

import com.personalkanban.application.port.InMemoryBoardRepository;
import com.personalkanban.application.port.InMemorySettingsStore;
import com.personalkanban.application.port.InMemoryUndoHistory;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardColumn;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User request (2026-10-13, N-3): «add column to the left / to the right» of an
 * existing column. The new column has to land exactly where the user asked
 * (not at the end and then moved, which would take two Ctrl+Z to undo).
 */
class ColumnInsertAtTest {

    private final InMemoryBoardRepository repository = new InMemoryBoardRepository();
    private final InMemoryUndoHistory history = new InMemoryUndoHistory();
    private final InMemorySettingsStore settings = new InMemorySettingsStore();
    private final BoardService service = new BoardService(repository, history, settings);

    @Test
    void aColumnLandsExactlyWhereItWasAskedFor() {
        setUp();
        service.addColumnAt("Middle", "", BoardColor.GREEN, WipLimit.unlimited(), 1);

        assertThat(titles()).containsExactly("A", "Middle", "C");
    }

    @Test
    void insertingBeforeTheFirstAndAfterTheLastWork() {
        setUp();
        service.addColumnAt("First", "", BoardColor.GREEN, WipLimit.unlimited(), 0);
        service.addColumnAt("Last", "", BoardColor.GREEN, WipLimit.unlimited(), 3);

        assertThat(titles()).containsExactly("First", "A", "C", "Last");
    }

    @Test
    void insertingPastTheEndAppends() {
        setUp();
        service.addColumnAt("Last", "", BoardColor.GREEN, WipLimit.unlimited(), 99);

        assertThat(titles()).containsExactly("A", "C", "Last");
    }

    @Test
    void oneUndoRemovesTheInsertedColumn() {
        setUp();
        service.addColumnAt("Middle", "", BoardColor.GREEN, WipLimit.unlimited(), 1);

        service.undo();

        assertThat(titles()).containsExactly("A", "C");
        // setUp: createBoard + 2 columns; the insert was a single entry.
        assertThat(history.depth(service.activeBoardId())).isEqualTo(2);
    }

    private void setUp() {
        service.openBoard(service.createBoard("Session 16"));
        service.addColumn("A", "", BoardColor.BLUE, WipLimit.unlimited());
        service.addColumn("C", "", BoardColor.BLUE, WipLimit.unlimited());
    }

    private java.util.List<String> titles() {
        return service.board().columns().stream().map(BoardColumn::title).toList();
    }
}
