package com.personalkanban.infrastructure.sqlite;

import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardDescriptor;
import com.personalkanban.domain.board.BoardId;
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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteBoardRepositoryTest {

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
        boardId = repository.createBoard("Test Board").id();
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
    void catalogListsCreatedBoards() {
        repository.createBoard("Second");
        List<BoardDescriptor> boards = repository.listBoards();
        // "My Board" is the legacy entry created by the V4 data migration.
        assertThat(boards).extracting(BoardDescriptor::name)
                .containsExactly("My Board", "Test Board", "Second");
    }

    @Test
    void renamedBoardIsReflectedInCatalog() {
        repository.renameBoard(boardId, "Renamed");
        assertThat(repository.listBoards())
                .filteredOn(descriptor -> descriptor.id().equals(boardId))
                .singleElement()
                .satisfies(descriptor -> assertThat(descriptor.name()).isEqualTo("Renamed"));
    }

    @Test
    void unknownBoardLoadsEmpty() {
        BoardId ghost = new BoardId("no-such-board");
        assertThat(repository.load(ghost).columns()).isEmpty();
    }

    @Test
    void saveThenLoadRoundTripsEverything() {
        Instant columnCreated = Instant.parse("2026-01-15T10:00:00Z");
        Instant cardCreated = Instant.parse("2026-02-20T12:30:00Z");
        LocalDate due = LocalDate.of(2026, 12, 24);

        ColumnSnapshot column = new ColumnSnapshot(
                new com.personalkanban.domain.board.ColumnId("col-1"),
                "To Do", "backlog items", BoardColor.BLUE, WipLimit.of(5),
                columnCreated,
                List.of(new CardSnapshot(
                        new com.personalkanban.domain.board.CardId("card-1"),
                        "write tests", "unit and integration", BoardColor.PINK,
                        due, List.of("home", "urgent"), cardCreated)));
        ColumnSnapshot unlimited = new ColumnSnapshot(
                new com.personalkanban.domain.board.ColumnId("col-2"),
                "Doing", "", BoardColor.TEAL, WipLimit.unlimited(),
                columnCreated,
                List.of());

        repository.save(boardId, new BoardMemento(List.of(column, unlimited)));

        BoardMemento loaded = repository.load(boardId);
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
            assertThat(card.dueDate()).isEqualTo(due);
            assertThat(card.labels()).containsExactly("home", "urgent");
            assertThat(card.createdAt()).isEqualTo(cardCreated);
        });

        ColumnSnapshot second = loaded.columns().get(1);
        assertThat(second.wipLimit()).isEqualTo(WipLimit.unlimited());
        assertThat(second.cards()).isEmpty();
    }

    @Test
    void saveReplacesPreviousContent() {
        ColumnSnapshot original = new ColumnSnapshot(
                new com.personalkanban.domain.board.ColumnId("col-1"),
                "Old", "", BoardColor.GRAY, WipLimit.unlimited(), Instant.now(), List.of());
        repository.save(boardId, new BoardMemento(List.of(original)));

        ColumnSnapshot replacement = new ColumnSnapshot(
                new com.personalkanban.domain.board.ColumnId("col-2"),
                "New", "", BoardColor.ORANGE, WipLimit.of(3), Instant.now(), List.of());
        repository.save(boardId, new BoardMemento(List.of(replacement)));

        BoardMemento loaded = repository.load(boardId);
        assertThat(loaded.columns()).singleElement().satisfies(column -> {
            assertThat(column.id().value()).isEqualTo("col-2");
            assertThat(column.title()).isEqualTo("New");
        });
    }

    @Test
    void cardOrderFollowsSavedPosition() {
        ColumnSnapshot column = new ColumnSnapshot(
                new com.personalkanban.domain.board.ColumnId("col-1"),
                "C", "", BoardColor.BLUE, WipLimit.unlimited(), Instant.now(),
                List.of(card("c3"), card("c1"), card("c2")));
        repository.save(boardId, new BoardMemento(List.of(column)));

        BoardMemento loaded = repository.load(boardId);
        assertThat(loaded.columns().get(0).cards())
                .extracting(CardSnapshot::title)
                .containsExactly("c3", "c1", "c2");
    }

    @Test
    void boardsAreIsolatedFromEachOther() {
        ColumnSnapshot column = new ColumnSnapshot(
                new com.personalkanban.domain.board.ColumnId("col-1"),
                "Only Here", "", BoardColor.BLUE, WipLimit.unlimited(), Instant.now(), List.of());
        repository.save(boardId, new BoardMemento(List.of(column)));

        BoardId other = repository.createBoard("Other").id();
        assertThat(repository.load(other).columns()).isEmpty();
        assertThat(repository.load(boardId).columns()).hasSize(1);
    }

    @Test
    void deleteBoardRemovesCatalogEntryAndContents() {
        ColumnSnapshot column = new ColumnSnapshot(
                new com.personalkanban.domain.board.ColumnId("col-1"),
                "Doomed", "", BoardColor.BLUE, WipLimit.unlimited(), Instant.now(),
                List.of(card("doomed card")));
        repository.save(boardId, new BoardMemento(List.of(column)));

        repository.deleteBoard(boardId);

        assertThat(repository.listBoards())
                .extracting(BoardDescriptor::id)
                .doesNotContain(boardId);
        assertThat(repository.load(boardId).columns()).isEmpty();

        // The physical card rows must be gone too (cascade through columns).
        Long cardsLeft = queryScalar("SELECT COUNT(*) FROM card");
        Long columnsLeft = queryScalar("SELECT COUNT(*) FROM board_column");
        assertThat(cardsLeft).isZero();
        assertThat(columnsLeft).isZero();
    }

    @Test
    void labelsRoundTripWithSpecialCharactersAndLegacySeparators() {
        // A label containing ';' must survive (it used to corrupt the list).
        ColumnSnapshot column = new ColumnSnapshot(
                new com.personalkanban.domain.board.ColumnId("col-1"),
                "C", "", BoardColor.BLUE, WipLimit.unlimited(), Instant.now(),
                List.of(new CardSnapshot(
                        new com.personalkanban.domain.board.CardId("card-1"),
                        "tricky", "", BoardColor.GRAY, null,
                        List.of("et;iqueta", "uaz", "UAZ"), Instant.now())));
        repository.save(boardId, new BoardMemento(List.of(column)));

        assertThat(repository.load(boardId).columns().get(0).cards())
                .singleElement()
                .satisfies(card -> assertThat(card.labels())
                        .containsExactly("et;iqueta", "uaz", "UAZ"));

        // Legacy row written with ';' as separator still reads correctly.
        try (var statement = database.connection().createStatement()) {
            statement.executeUpdate("UPDATE card SET labels = 'a;b;c' WHERE id = 'card-1'");
        } catch (java.sql.SQLException e) {
            throw new DataAccessException("legacy labels setup failed", e);
        }
        assertThat(repository.load(boardId).columns().get(0).cards())
                .singleElement()
                .satisfies(card -> assertThat(card.labels()).containsExactly("a", "b", "c"));
    }

    @Test
    void saveThenLoadRoundTripsCustomColors() {
        // Regression for "Could not save board default-board": custom
        // (color-picker) colors used to persist as NULL and trip NOT NULL.
        BoardColor customCard = BoardColor.fromHex("#123456");
        BoardColor customColumn = BoardColor.fromHex("#654321");
        ColumnSnapshot column = new ColumnSnapshot(
                new com.personalkanban.domain.board.ColumnId("col-1"),
                "Custom", "", customColumn, WipLimit.unlimited(), Instant.now(),
                List.of(new CardSnapshot(
                        new com.personalkanban.domain.board.CardId("card-1"),
                        "painted", "", customCard, null, List.of(), Instant.now())));
        repository.save(boardId, new BoardMemento(List.of(column)));

        BoardMemento loaded = repository.load(boardId);
        assertThat(loaded.columns()).singleElement().satisfies(loadedColumn -> {
            assertThat(loadedColumn.color()).isEqualTo(customColumn);
            assertThat(loadedColumn.cards()).singleElement()
                    .satisfies(card -> assertThat(card.color()).isEqualTo(customCard));
        });
    }

    private Long queryScalar(String sql) {
        try (var statement = database.connection().createStatement();
             var resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        } catch (java.sql.SQLException e) {
            throw new DataAccessException("Query failed: " + sql, e);
        }
    }

    private CardSnapshot card(String title) {
        return new CardSnapshot(
                new com.personalkanban.domain.board.CardId("id-" + title),
                title, "", BoardColor.BLUE, null, List.of(), Instant.now());
    }
}
