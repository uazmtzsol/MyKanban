package com.personalkanban.infrastructure.sqlite;

import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;
import com.personalkanban.domain.board.CardLink;
import com.personalkanban.domain.board.CardSnapshot;
import com.personalkanban.domain.board.ChecklistItem;
import com.personalkanban.domain.board.ColumnSnapshot;
import com.personalkanban.domain.board.ProcessId;
import com.personalkanban.domain.board.ProcessSnapshot;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip tests over a real SQLite database (temp file) for the
 * session-4 data: card notes, checklists, processes and precedence links.
 */
class SqliteSessionFourRoundTripTest {

    @TempDir
    Path tempDir;

    private Database database;
    private SqliteBoardRepository repository;
    private BoardId boardId;

    @BeforeEach
    void setUp() {
        database = new Database(tempDir.resolve("test.db"));
        new SchemaMigrator(database).migrate();
        repository = new SqliteBoardRepository(database);
        boardId = repository.createBoard("Session 4 Board").id();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void notesChecklistProcessAndLinksRoundTrip() {
        Instant created = Instant.parse("2026-09-30T10:00:00Z");
        ChecklistItem pending = new ChecklistItem("item-1", "first step", false);
        ChecklistItem done = new ChecklistItem("item-2", "second step", true);
        ProcessId processId = new ProcessId("proc-1");

        ColumnSnapshot column = new ColumnSnapshot(
                new com.personalkanban.domain.board.ColumnId("col-1"),
                "To Do", "", BoardColor.BLUE, WipLimit.unlimited(), created,
                List.of(
                        new CardSnapshot(
                                new com.personalkanban.domain.board.CardId("card-1"),
                                "with extras", "desc", BoardColor.PINK, null,
                                List.of(), created,
                                "plain notes\nsecond line",
                                List.of(pending, done), processId),
                        new CardSnapshot(
                                new com.personalkanban.domain.board.CardId("card-2"),
                                "plain", "desc", BoardColor.PINK, null,
                                List.of(), created, "", List.of(), null)));

        BoardMemento snapshot = new BoardMemento(
                List.of(column),
                List.of(new ProcessSnapshot(processId, "Mudanza")),
                List.of(new CardLink(
                        new com.personalkanban.domain.board.CardId("card-1"),
                        new com.personalkanban.domain.board.CardId("card-2"))));

        repository.save(boardId, snapshot);
        BoardMemento loaded = repository.load(boardId);

        assertThat(loaded.processes()).extracting(ProcessSnapshot::name).containsExactly("Mudanza");
        assertThat(loaded.links()).hasSize(1);

        var cards = loaded.columns().get(0).cards();
        var first = cards.get(0);
        assertThat(first.notes()).isEqualTo("plain notes\nsecond line");
        assertThat(first.processId()).isEqualTo(processId);
        assertThat(first.checklist()).hasSize(2);
        assertThat(first.checklist().get(0).id()).isEqualTo("item-1");
        assertThat(first.checklist().get(0).text()).isEqualTo("first step");
        assertThat(first.checklist().get(0).done()).isFalse();
        assertThat(first.checklist().get(1).done()).isTrue();

        var second = cards.get(1);
        assertThat(second.notes()).isEmpty();
        assertThat(second.checklist()).isEmpty();
        assertThat(second.processId()).isNull();
    }

    @Test
    void migratorAppliesV5ToV7AndIsIdempotent() {
        new SchemaMigrator(database).migrate();
        new SchemaMigrator(database).migrate(); // must not throw
    }
}
