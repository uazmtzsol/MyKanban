package com.personalkanban;

import com.personalkanban.application.BoardService;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardDescriptor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the database file management feature: create a new
 * database file, open an existing one, switch back and forth, and remember
 * the choice across restarts — the workflow that makes a database a portable
 * unit for USB sticks or synced cloud folders.
 */
class AppContextDatabaseManagementTest {

    @TempDir
    Path tempDir;

    private Path configFile() {
        return tempDir.resolve("config.properties");
    }

    @Test
    void openAtCreatesSchemaAndStartsEmpty() {
        Path db = tempDir.resolve("fresh.db");
        try (AppContext context = AppContext.openAt(db, configFile())) {
            assertThat(db).exists();
            assertThat(context.databasePath()).isEqualTo(db.toAbsolutePath().normalize());
            assertThat(context.boardService().boards()).hasSize(1);
            assertThat(context.boardService().board().columnCount()).isZero();
        }
    }

    @Test
    void dataWrittenToDatabaseSurvivesReopen() {
        Path dbA = tempDir.resolve("a.db");
        try (AppContext context = AppContext.openAt(dbA, configFile())) {
            var col = context.boardService().addColumn("Home", "", BoardColor.BLUE, WipLimit.unlimited());
            context.boardService().addCard(col, "card in A", "", BoardColor.BLUE);
        }

        try (AppContext context = AppContext.openAt(dbA, configFile())) {
            assertThat(context.boardService().board().cardCount()).isEqualTo(1);
            assertThat(context.databasePath()).isEqualTo(dbA.toAbsolutePath().normalize());
        }
    }

    @Test
    void switchingDatabasesShowsIndependentContents() {
        Path dbA = tempDir.resolve("a.db");
        Path dbB = tempDir.resolve("b.db");

        try (AppContext context = AppContext.openAt(dbA, configFile())) {
            var col = context.boardService().addColumn("Only In A", "", BoardColor.BLUE, WipLimit.unlimited());
            context.boardService().addCard(col, "work item", "", BoardColor.BLUE);
            context.openDatabase(dbB);
            assertThat(context.databasePath()).isEqualTo(dbB.toAbsolutePath().normalize());
            assertThat(context.boardService().board().columnCount()).isZero();
        }

        try (AppContext context = AppContext.openAt(dbA, configFile())) {
            assertThat(context.boardService().board().columnCount()).isEqualTo(1);
        }
    }

    @Test
    void lastDatabaseIsRememberedInConfigAcrossRestarts() {
        Path dbA = tempDir.resolve("a.db");
        Path dbB = tempDir.resolve("b.db");
        AppContext.openAt(dbA, configFile()).close();

        try (AppContext context = AppContext.openAt(dbA, configFile())) {
            context.openDatabase(dbB);
        }
        // create() must reopen the last used database (B), not the default.
        try (AppContext context = AppContext.createWith(configFile(), tempDir.resolve("unused-default.db"))) {
            assertThat(context.databasePath()).isEqualTo(dbB.toAbsolutePath().normalize());
        }
    }

    @Test
    void openingAnInvalidFileKeepsCurrentDatabaseAlive() throws Exception {
        Path good = tempDir.resolve("good.db");
        Path bad = tempDir.resolve("bad.db");

        BoardId boardId;
        try (AppContext context = AppContext.openAt(good, configFile())) {
            boardId = context.boardService().activeBoardId();
            Files.writeString(bad, "this is not a sqlite database at all");
            assertThatThrownBy(() -> context.openDatabase(bad))
                    .isInstanceOf(RuntimeException.class);

            // The failure must leave the current database fully usable.
            assertThat(context.databasePath()).isEqualTo(good.toAbsolutePath().normalize());
            assertThat(context.boardService().activeBoardId()).isEqualTo(boardId);
            var col = context.boardService().addColumn("Still Works", "", BoardColor.BLUE, WipLimit.unlimited());
            assertThat(col).isNotNull();
        }
    }

    @Test
    void missingConfiguredFileFallsBackToDefault() throws Exception {
        Path dbA = tempDir.resolve("a.db");
        try (AppContext ignored = AppContext.openAt(dbA, configFile())) {
            // config now points at a.db
        }
        Files.delete(dbA); // the remembered file vanished (e.g. USB unplugged)

        Path defaultDb = tempDir.resolve("fallback.db");
        try (AppContext context = AppContext.createWith(configFile(), defaultDb)) {
            assertThat(context.databasePath()).isEqualTo(defaultDb.toAbsolutePath().normalize());
        }
    }

    // ------------------------------------------------------------------
    // Delete-board repro with the REAL stack (SQLite + JSON undo history):
    // the user reported "deleting a board did nothing" from the portable app.
    // ------------------------------------------------------------------

    @Test
    void deleteActiveEmptyBoardWithRealSqliteStack() {
        try (AppContext context = AppContext.openAt(
                tempDir.resolve("kanban.db"), configFile())) {
            BoardService service = context.boardService();
            BoardId original = service.activeBoardId();

            BoardId empty = service.createBoard("Prueba vacia");
            service.openBoard(empty); // it is now the active board

            // Exactly what onDeleteBoard does after the confirmation:
            service.deleteBoard(service.activeBoardId());

            assertThat(service.boards()).extracting(BoardDescriptor::name)
                    .doesNotContain("Prueba vacia");
            assertThat(service.activeBoardId()).isEqualTo(original);
        }
    }

    @Test
    void deleteActiveFullBoardWithRealSqliteStackAndHistory() {
        try (AppContext context = AppContext.openAt(
                tempDir.resolve("kanban.db"), configFile())) {
            BoardService service = context.boardService();
            BoardId original = service.activeBoardId();

            BoardId full = service.createBoard("Con todo");
            service.openBoard(full);
            var col = service.addColumn("C1", "", BoardColor.BLUE, WipLimit.unlimited());
            service.addCard(col, "t1", "", BoardColor.BLUE);
            service.addCard(col, "t2", "", BoardColor.BLUE, null, List.of("Urgente"));

            // Undo history exists for this board before deletion.
            assertThat(service.canUndo()).isTrue();

            service.deleteBoard(service.activeBoardId());

            assertThat(service.boards()).extracting(BoardDescriptor::name)
                    .doesNotContain("Con todo");
            assertThat(service.activeBoardId()).isEqualTo(original);
            assertThat(service.canUndo()).isFalse(); // deleted board's history gone
        }
    }
}
