package com.personalkanban.infrastructure.sqlite;

import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardMemento;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence hygiene (user request, session 5): the WAL folds into the
 * main file on checkpoint, and a clean connection close removes the
 * -wal/-shm leftovers. Every mutation is already committed instantly —
 * these tests prove the file-level tidy-up the user sees.
 */
class CheckpointTest {

    @TempDir
    Path tempDir;

    @Test
    void checkpointShrinksWalAfterMutations() {
        Path dbFile = tempDir.resolve("kanban.db");
        Database database = new Database(dbFile);
        new SchemaMigrator(database).migrate();
        var repository = new SqliteBoardRepository(database);
        var boardId = repository.createBoard("Checkpoint").id();
        var column = new com.personalkanban.domain.board.ColumnSnapshot(
                new com.personalkanban.domain.board.ColumnId("c1"),
                "To Do", "", BoardColor.DEFAULT, WipLimit.unlimited(),
                java.time.Instant.now(), java.util.List.of());
        repository.save(boardId, new BoardMemento(java.util.List.of(column)));

        Path wal = tempDir.resolve("kanban.db-wal");
        assertThat(wal).exists(); // WAL mode active while the connection is open

        assertThat(database.checkpoint()).isTrue();
        // After TRUNCATE the journal is folded in and the file reset to 0 bytes.
        try {
            assertThat(Files_size(wal)).isZero();
        } finally {
            database.close();
        }
    }

    @Test
    void cleanCloseRemovesWalAndShmLeftovers() {
        Path dbFile = tempDir.resolve("kanban.db");
        Database database = new Database(dbFile);
        new SchemaMigrator(database).migrate();
        new SqliteBoardRepository(database).createBoard("Clean");

        database.close();

        // The clean close checkpoints and deletes both side files: the data
        // folder ends up holding only kanban.db (+ config/history files).
        assertThat(tempDir.resolve("kanban.db")).exists();
        assertThat(tempDir.resolve("kanban.db-wal")).doesNotExist();
        assertThat(tempDir.resolve("kanban.db-shm")).doesNotExist();

        // And the folded file is a complete, openable database again.
        Database reopened = new Database(dbFile);
        var repository = new SqliteBoardRepository(reopened);
        assertThat(repository.listBoards())
                .anyMatch(descriptor -> descriptor.name().equals("Clean"));
        reopened.close();
    }

    private long Files_size(Path path) {
        try {
            return java.nio.file.Files.size(path);
        } catch (java.io.IOException e) {
            return -1;
        }
    }
}
