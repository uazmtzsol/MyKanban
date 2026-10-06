package com.personalkanban.infrastructure.sqlite;

import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardMemento;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence hygiene (user request, session 5; WAL later removed by
 * request): the database runs in the default rollback journal mode,
 * so every mutation commits instantly to the single file and no
 * -wal/-shm sidecars are ever created. A clean close leaves a
 * complete, reopenable database behind.
 */
class DurabilityTest {

    @TempDir
    Path tempDir;

    @Test
    void mutationsCommitWithoutWalSidecarFiles() {
        Path dbFile = tempDir.resolve("kanban.db");
        Database database = new Database(dbFile);
        new SchemaMigrator(database).migrate();
        var repository = new SqliteBoardRepository(database);
        var boardId = repository.createBoard("Durability").id();
        var column = new com.personalkanban.domain.board.ColumnSnapshot(
                new com.personalkanban.domain.board.ColumnId("c1"),
                "To Do", "", BoardColor.DEFAULT, WipLimit.unlimited(),
                java.time.Instant.now(), java.util.List.of());
        repository.save(boardId, new BoardMemento(java.util.List.of(column)));

        // Rollback journal mode: commits land in kanban.db directly and
        // no sidecar files exist while the connection is open.
        assertThat(tempDir.resolve("kanban.db-wal")).doesNotExist();
        assertThat(tempDir.resolve("kanban.db-shm")).doesNotExist();

        database.close();

        // Still no leftovers: the data folder holds only kanban.db.
        assertThat(tempDir.resolve("kanban.db-wal")).doesNotExist();
        assertThat(tempDir.resolve("kanban.db-shm")).doesNotExist();
    }

    @Test
    void cleanCloseLeavesCompleteReopenableDatabase() {
        Path dbFile = tempDir.resolve("kanban.db");
        Database database = new Database(dbFile);
        new SchemaMigrator(database).migrate();
        new SqliteBoardRepository(database).createBoard("Clean");

        database.close();

        assertThat(tempDir.resolve("kanban.db")).exists();

        // The single file is a complete, openable database again.
        Database reopened = new Database(dbFile);
        var repository = new SqliteBoardRepository(reopened);
        assertThat(repository.listBoards())
                .anyMatch(descriptor -> descriptor.name().equals("Clean"));
        reopened.close();
    }
}
