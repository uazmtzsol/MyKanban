package com.personalkanban.infrastructure.sqlite;

import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardMemento;
import com.personalkanban.domain.board.CardSnapshot;
import com.personalkanban.domain.board.ColumnSnapshot;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteBoardRepositoryTest {

    @TempDir
    Path tempDir;

    private Database database;

    @BeforeEach
    void setUp() {
        database = new Database(tempDir.resolve("test.db"));
        new SchemaMigrator(database).migrate();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    void migratorIsIdempotent() {
        new SchemaMigrator(database).migrate();
        new SchemaMigrator(database).migrate();
    }

    @Test
    void emptyDatabaseLoadsEmptyBoard() {
        SqliteBoardRepository repository = new SqliteBoardRepository(database);
        assertThat(repository.load().columns()).isEmpty();
    }

    @Test
    void saveThenLoadRoundTripsEverything() {
        SqliteBoardRepository repository = new SqliteBoardRepository(database);

        Instant columnCreated = Instant.parse("2026-01-15T10:00:00Z");
        Instant cardCreated = Instant.parse("2026-02-20T12:30:00Z");

        ColumnSnapshot column = new ColumnSnapshot(
                new com.personalkanban.domain.board.ColumnId("col-1"),
                "To Do", "backlog items", BoardColor.BLUE, WipLimit.of(5),
                columnCreated,
                List.of(new CardSnapshot(
                        new com.personalkanban.domain.board.CardId("card-1"),
                        "write tests", "unit and integration", BoardColor.PINK,
                        cardCreated)));
        ColumnSnapshot unlimited = new ColumnSnapshot(
                new com.personalkanban.domain.board.ColumnId("col-2"),
                "Doing", "", BoardColor.TEAL, WipLimit.unlimited(),
                columnCreated,
                List.of());

        repository.save(new BoardMemento(List.of(column, unlimited)));

        BoardMemento loaded = repository.load();
        assertThat(loaded.columns()).hasSize(2);

        ColumnSnapshot first = loaded.columns().get(0);
        assertThat(first.id().value()).isEqualTo("col-1");
        assertThat(first.title()).isEqualTo("To Do");
        assertThat(first.description()).isEqualTo("backlog items");
        assertThat(first.color()).isEqualTo(BoardColor.BLUE);
        assertThat(first.wipLimit()).isEqualTo(WipLimit.of(5));
        assertThat(first.createdAt()).isEqualTo(columnCreated);
        assertThat(first.cards()).singleElement().satisfies(card -> {
            assertThat(card.id().value()).isEqualTo("card-1");
            assertThat(card.title()).isEqualTo("write tests");
            assertThat(card.description()).isEqualTo("unit and integration");
            assertThat(card.color()).isEqualTo(BoardColor.PINK);
            assertThat(card.createdAt()).isEqualTo(cardCreated);
        });

        ColumnSnapshot second = loaded.columns().get(1);
        assertThat(second.wipLimit()).isEqualTo(WipLimit.unlimited());
        assertThat(second.cards()).isEmpty();
    }

    @Test
    void saveReplacesPreviousContent() {
        SqliteBoardRepository repository = new SqliteBoardRepository(database);
        ColumnSnapshot original = new ColumnSnapshot(
                new com.personalkanban.domain.board.ColumnId("col-1"),
                "Old", "", BoardColor.GRAY, WipLimit.unlimited(), Instant.now(), List.of());
        repository.save(new BoardMemento(List.of(original)));

        ColumnSnapshot replacement = new ColumnSnapshot(
                new com.personalkanban.domain.board.ColumnId("col-2"),
                "New", "", BoardColor.ORANGE, WipLimit.of(3), Instant.now(), List.of());
        repository.save(new BoardMemento(List.of(replacement)));

        BoardMemento loaded = repository.load();
        assertThat(loaded.columns()).singleElement().satisfies(column -> {
            assertThat(column.id().value()).isEqualTo("col-2");
            assertThat(column.title()).isEqualTo("New");
        });
    }

    @Test
    void cardOrderFollowsSavedPosition() {
        SqliteBoardRepository repository = new SqliteBoardRepository(database);
        ColumnSnapshot column = new ColumnSnapshot(
                new com.personalkanban.domain.board.ColumnId("col-1"),
                "C", "", BoardColor.BLUE, WipLimit.unlimited(), Instant.now(),
                List.of(
                        card("c3"), card("c1"), card("c2")));
        repository.save(new BoardMemento(List.of(column)));

        BoardMemento loaded = repository.load();
        assertThat(loaded.columns().get(0).cards())
                .extracting(CardSnapshot::title)
                .containsExactly("c3", "c1", "c2");
    }

    private CardSnapshot card(String title) {
        return new CardSnapshot(
                new com.personalkanban.domain.board.CardId("id-" + title),
                title, "", BoardColor.BLUE, Instant.now());
    }
}
