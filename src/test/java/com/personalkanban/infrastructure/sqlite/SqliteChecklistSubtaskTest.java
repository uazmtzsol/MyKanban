package com.personalkanban.infrastructure.sqlite;

import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.CardSnapshot;
import com.personalkanban.domain.board.ChecklistItem;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.ColumnSnapshot;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T16 · B8 over a real database: the migration <b>V10</b> adds
 * {@code parent_id}, the parent survives a save/load round trip, and a row
 * without a parent (exactly the shape written before V10) reads back as a
 * top-level item instead of breaking the load.
 */
class SqliteChecklistSubtaskTest {

    @TempDir
    Path tempDir;

    private Database database;
    private SqliteBoardRepository repository;
    private BoardId boardId;

    @BeforeEach
    void setUp() {
        database = new Database(tempDir.resolve("subtasks.db"));
        new SchemaMigrator(database).migrate();
        repository = new SqliteBoardRepository(database);
        boardId = repository.createBoard("Subtasks").id();
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    private BoardMemento boardWith(ChecklistItem... items) {
        Instant created = Instant.parse("2026-10-13T10:00:00Z");
        ColumnSnapshot column = new ColumnSnapshot(new ColumnId("col-1"), "To Do", "",
                BoardColor.BLUE, WipLimit.unlimited(), created,
                List.of(new CardSnapshot(new CardId("card-1"), "card", "", BoardColor.BLUE,
                        null, List.of(), created, "", List.of(items), null)));
        return new BoardMemento(List.of(column), List.of(), List.of());
    }

    @Test
    void parentSurvivesTheRoundTrip() {
        ChecklistItem parent = new ChecklistItem("item-1", "parent", false);
        ChecklistItem child = new ChecklistItem("item-2", "child", true, "item-1");

        repository.save(boardId, boardWith(parent, child));
        var loaded = repository.load(boardId).columns().get(0).cards().get(0).checklist();

        assertThat(loaded).hasSize(2);
        assertThat(loaded.get(0).parentId()).isNull();
        assertThat(loaded.get(1).parentId()).isEqualTo("item-1");
        assertThat(loaded.get(1).isSubtask()).isTrue();
        assertThat(loaded.get(1).done()).isTrue();
    }

    @Test
    void aRowWithoutParentReadsBackAsATopLevelItem() throws SQLException {
        repository.save(boardId, boardWith(new ChecklistItem("item-2", "child", false, "item-1"),
                new ChecklistItem("item-1", "parent", false)));

        // Exactly what a pre-V10 row looks like after the ALTER TABLE.
        try (PreparedStatement statement = database.connection().prepareStatement(
                "UPDATE card_checklist_item SET parent_id = NULL")) {
            statement.executeUpdate();
        }

        var loaded = repository.load(boardId).columns().get(0).cards().get(0).checklist();

        assertThat(loaded).allMatch(item -> !item.isSubtask());
        assertThat(loaded).extracting(ChecklistItem::text).containsExactly("child", "parent");
    }

    @Test
    void migrationIsIdempotent() {
        new SchemaMigrator(database).migrate();

        assertThat(repository.load(boardId).columns()).isEmpty();
    }
}
