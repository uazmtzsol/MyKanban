package com.personalkanban.infrastructure.sqlite;

import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.CardSnapshot;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.ColumnSnapshot;
import com.personalkanban.domain.board.EntryId;
import com.personalkanban.domain.board.TimelineEntry;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Round-trip tests for the session-6 time-tracking data over real SQLite. */
class SqliteTimelineRoundTripTest {

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
        boardId = repository.createBoard("Timeline Board").id();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void timelineEntriesRoundTrip() {
        Instant created = Instant.parse("2026-09-30T10:00:00Z");
        CardId cardId = new CardId("card-1");
        TimelineEntry closed = TimelineEntry.restore(cardId, new EntryId("entry-1"),
                Instant.parse("2027-01-01T13:00:00Z"), Instant.parse("2027-01-01T13:45:00Z"),
                "Iniciando el registro");
        TimelineEntry running = TimelineEntry.restore(cardId, new EntryId("entry-2"),
                Instant.parse("2027-01-02T09:00:00Z"), null, "en curso");

        ColumnSnapshot column = new ColumnSnapshot(
                new ColumnId("col-1"), "To Do", "", BoardColor.BLUE, WipLimit.unlimited(), created,
                List.of(new CardSnapshot(cardId, "with time", "desc", BoardColor.PINK, null,
                        List.of(), created, "", List.of(), null)));

        repository.save(boardId, new BoardMemento(List.of(column), List.of(), List.of(), List.of(closed, running)));
        BoardMemento loaded = repository.load(boardId);

        assertThat(loaded.timeline()).hasSize(2);
        var entries = loaded.toBoard().findCard(cardId).orElseThrow().timeline().entries();
        assertThat(entries).hasSize(2);

        TimelineEntry first = entries.get(0);
        assertThat(first.id().value()).isEqualTo("entry-1");
        assertThat(first.start()).isEqualTo(Instant.parse("2027-01-01T13:00:00Z"));
        assertThat(first.end()).isEqualTo(Instant.parse("2027-01-01T13:45:00Z"));
        assertThat(first.comment()).isEqualTo("Iniciando el registro");
        assertThat(first.isClosed()).isTrue();

        TimelineEntry second = entries.get(1);
        assertThat(second.id().value()).isEqualTo("entry-2");
        assertThat(second.end()).isNull();
        assertThat(second.isRunning()).isTrue();
    }

    @Test
    void deletingABoardCascadesItsTimelineRows() throws Exception {
        Instant created = Instant.parse("2026-09-30T10:00:00Z");
        CardId cardId = new CardId("card-1");
        TimelineEntry entry = TimelineEntry.restore(cardId, new EntryId("entry-1"),
                created, created.plusSeconds(60), "x");
        ColumnSnapshot column = new ColumnSnapshot(
                new ColumnId("col-1"), "To Do", "", BoardColor.BLUE, WipLimit.unlimited(), created,
                List.of(new CardSnapshot(cardId, "c", "d", BoardColor.PINK, null,
                        List.of(), created, "", List.of(), null)));
        repository.save(boardId, new BoardMemento(List.of(column), List.of(), List.of(), List.of(entry)));
        assertThat(timelineRowCount()).isEqualTo(1);

        repository.deleteBoard(boardId);

        assertThat(repository.listBoards()).noneMatch(b -> b.id().equals(boardId));
        assertThat(timelineRowCount()).isZero();
    }

    private int timelineRowCount() throws Exception {
        try (var statement = database.connection().createStatement();
             var resultSet = statement.executeQuery("SELECT COUNT(*) FROM timeline")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
