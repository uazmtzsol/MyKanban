package com.personalkanban.infrastructure.sqlite;

import com.personalkanban.application.port.BoardRepository;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardDescriptor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.CardLink;
import com.personalkanban.domain.board.CardSnapshot;
import com.personalkanban.domain.board.ChecklistItem;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.ColumnSnapshot;
import com.personalkanban.domain.board.EntryId;
import com.personalkanban.domain.board.ProcessId;
import com.personalkanban.domain.board.ProcessSnapshot;
import com.personalkanban.domain.board.TimelineEntry;
import com.personalkanban.domain.board.WipLimit;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Adapter implementing the multi-board {@link BoardRepository} port on
 * SQLite. One row per board in the catalog, one row per column and per card
 * for board contents. Order comes from per-table integer positions written
 * from list order at save time (single writer). Each {@link #save} is one
 * transaction. No two JDBC statements are open at once (driver requirement).
 *
 * <p>Since session 6 the contents also include the per-card time-tracking
 * entries ({@code timeline}), flattened into a single table keyed by card.</p>
 */
public final class SqliteBoardRepository implements BoardRepository {

    private final Database database;

    public SqliteBoardRepository(Database database) {
        this.database = Objects.requireNonNull(database);
    }

    // ------------------------------------------------------------------
    // Catalog
    // ------------------------------------------------------------------

    @Override
    public List<BoardDescriptor> listBoards() {
        // Deterministic order: creation time first, then id as the tie-breaker
        // (two boards created in the same millisecond must keep creation order;
        // ordering by name alone would silently sort alphabetically).
        String sql = "SELECT id, name, created_at FROM board ORDER BY created_at, id";
        List<BoardDescriptor> boards = new ArrayList<>();
        try (PreparedStatement statement = database.connection().prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                boards.add(new BoardDescriptor(
                        new BoardId(resultSet.getString("id")),
                        resultSet.getString("name"),
                        Instant.ofEpochMilli(resultSet.getLong("created_at"))));
            }
            return boards;
        } catch (SQLException e) {
            throw new DataAccessException("Could not list boards", e);
        }
    }

    @Override
    public BoardDescriptor createBoard(String name) {
        String trimmed = name == null || name.isBlank() ? "Board" : name.strip();
        BoardId id = new BoardId(UUID.randomUUID().toString());
        String sql = "INSERT INTO board (id, name, created_at) VALUES (?, ?, ?)";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, id.value());
            statement.setString(2, trimmed);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not create board '" + trimmed + "'", e);
        }
        return new BoardDescriptor(id, trimmed, Instant.now());
    }

    @Override
    public void renameBoard(BoardId boardId, String newName) {
        String trimmed = newName == null || newName.isBlank() ? "Board" : newName.strip();
        String sql = "UPDATE board SET name = ? WHERE id = ?";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, trimmed);
            statement.setString(2, boardId.value());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not rename board " + boardId, e);
        }
    }

    @Override
    public void deleteBoard(BoardId boardId) {
        try (Statement statement = database.connection().createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("DELETE FROM board WHERE id = '" + boardId.value().replace("'", "''") + "'");
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete board " + boardId, e);
        }
    }

    // ------------------------------------------------------------------
    // Board contents
    // ------------------------------------------------------------------

    @Override
    public BoardMemento load(BoardId boardId) {
        List<ColumnSnapshot> columns = new ArrayList<>();
        String sql = """
                SELECT id, title, description, color, wip_limit, created_at, done, background_color
                FROM board_column
                WHERE board_id = ?
                ORDER BY position
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, boardId.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    columns.add(readColumnRow(resultSet));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load board " + boardId, e);
        }

        // Second pass: fetch cards per column, with no open outer ResultSet.
        List<ColumnSnapshot> withCards = new ArrayList<>();
        for (ColumnSnapshot column : columns) {
            withCards.add(new ColumnSnapshot(column.id(), column.title(), column.description(),
                    column.color(), column.wipLimit(), column.createdAt(), column.done(),
                    column.backgroundColor(), readCards(column.id())));
        }
        return new BoardMemento(withCards, readProcesses(boardId), readLinks(boardId),
                readTimeline(boardId));
    }

    /** Processes of one board, in stored position order. */
    private List<ProcessSnapshot> readProcesses(BoardId boardId) {
        List<ProcessSnapshot> processes = new ArrayList<>();
        String sql = "SELECT id, name FROM process WHERE board_id = ? ORDER BY position";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, boardId.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    processes.add(new ProcessSnapshot(
                            new ProcessId(resultSet.getString("id")),
                            resultSet.getString("name")));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load processes of board " + boardId, e);
        }
        return processes;
    }

    /** Time entries of every card that belongs to the given board. */
    private List<TimelineEntry> readTimeline(BoardId boardId) {
        List<TimelineEntry> entries = new ArrayList<>();
        String sql = """
                SELECT t.card_id, t.id, t.started_at, t.ended_at, t.comment
                FROM timeline t
                JOIN card c ON c.id = t.card_id
                JOIN board_column bc ON bc.id = c.column_id
                WHERE bc.board_id = ?
                ORDER BY t.started_at, t.id
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, boardId.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(readTimelineEntry(resultSet));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load time entries of board " + boardId, e);
        }
        return entries;
    }

    private TimelineEntry readTimelineEntry(ResultSet resultSet) throws SQLException {
        EntryId id = new EntryId(resultSet.getString("id"));
        CardId cardId = new CardId(resultSet.getString("card_id"));
        Instant start = readInstant(resultSet, "started_at");
        Instant end = readInstant(resultSet, "ended_at");
        String comment = resultSet.getString("comment");
        return TimelineEntry.restore(cardId, id, start, end, comment);
    }

    private Instant readInstant(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : Instant.ofEpochMilli(value);
    }

    private List<CardLink> readLinks(BoardId boardId) {
        List<CardLink> links = new ArrayList<>();
        String sql = """
                SELECT l.from_card_id, l.to_card_id FROM card_link l
                JOIN card c ON c.id = l.from_card_id
                JOIN board_column bc ON bc.id = c.column_id
                WHERE bc.board_id = ?
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, boardId.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    links.add(new CardLink(
                            new CardId(resultSet.getString("from_card_id")),
                            new CardId(resultSet.getString("to_card_id"))));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load links of board " + boardId, e);
        }
        return links;
    }

    @Override
    public void save(BoardId boardId, BoardMemento board) {
        try {
            boolean previousAutoCommit = database.connection().getAutoCommit();
            database.connection().setAutoCommit(false);
            try {
                replaceAll(boardId, board);
                database.connection().commit();
            } catch (SQLException e) {
                database.connection().rollback();
                throw e;
            } finally {
                database.connection().setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save board " + boardId, e);
        }
    }

    // ------------------------------------------------------------------
    // Save internals
    // ------------------------------------------------------------------

    private void replaceAll(BoardId boardId, BoardMemento board) throws SQLException {
        deleteBoardContents(boardId);
        String columnSql = """
                INSERT INTO board_column (id, board_id, title, description, color, position, wip_limit, created_at, done, background_color)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String cardSql = """
                INSERT INTO card (id, column_id, title, description, color, position, due_date, labels, created_at, notes, process_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String checklistSql = """
                INSERT INTO card_checklist_item (id, card_id, position, text, done)
                VALUES (?, ?, ?, ?, ?)
                """;
        String timelineSql = """
                INSERT INTO timeline (id, card_id, started_at, ended_at, comment)
                VALUES (?, ?, ?, ?, ?)
                """;
        String processSql = """
                INSERT INTO process (id, board_id, name, position, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        String linkSql = """
                INSERT INTO card_link (from_card_id, to_card_id) VALUES (?, ?)
                """;
        try (PreparedStatement columnStatement = database.connection().prepareStatement(columnSql);
             PreparedStatement cardStatement = database.connection().prepareStatement(cardSql);
             PreparedStatement checklistStatement = database.connection().prepareStatement(checklistSql);
             PreparedStatement timelineStatement = database.connection().prepareStatement(timelineSql);
             PreparedStatement processStatement = database.connection().prepareStatement(processSql);
             PreparedStatement linkStatement = database.connection().prepareStatement(linkSql)) {
            int columnIndex = 0;
            for (ColumnSnapshot column : board.columns()) {
                bindColumn(columnStatement, boardId, column, columnIndex++);
                columnStatement.addBatch();
                int cardIndex = 0;
                for (CardSnapshot card : column.cards()) {
                    bindCard(cardStatement, card, column.id(), cardIndex++);
                    cardStatement.addBatch();
                    bindChecklistItems(checklistStatement, card);
                }
            }
            for (TimelineEntry entry : board.timeline()) {
                if (entry.isEmpty()) {
                    continue;
                }
                timelineStatement.setString(1, entry.id().value());
                timelineStatement.setString(2, entry.cardId().value());
                setInstant(timelineStatement, 3, entry.start());
                setInstant(timelineStatement, 4, entry.end());
                timelineStatement.setString(5, entry.comment());
                timelineStatement.addBatch();
            }
            int processIndex = 0;
            for (ProcessSnapshot process : board.processes()) {
                bindProcess(processStatement, boardId, process, processIndex++);
                processStatement.addBatch();
            }
            for (CardLink link : board.links()) {
                linkStatement.setString(1, link.from().value());
                linkStatement.setString(2, link.to().value());
                linkStatement.addBatch();
            }
            // FK order matters: processes first (cards reference them), then
            // columns, cards, their checklists/timeline, and finally the links
            // (which reference cards).
            processStatement.executeBatch();
            columnStatement.executeBatch();
            cardStatement.executeBatch();
            checklistStatement.executeBatch();
            timelineStatement.executeBatch();
            linkStatement.executeBatch();
        }
    }

    private void setInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setLong(index, value.toEpochMilli());
        }
    }

    private void bindChecklistItems(PreparedStatement statement, CardSnapshot card) throws SQLException {
        int position = 0;
        for (ChecklistItem item : card.checklist()) {
            statement.setString(1, item.id());
            statement.setString(2, card.id().value());
            statement.setInt(3, position++);
            statement.setString(4, item.text());
            statement.setInt(5, item.done() ? 1 : 0);
            statement.addBatch();
        }
    }

    private void bindProcess(PreparedStatement statement, BoardId boardId,
                             ProcessSnapshot process, int position) throws SQLException {
        statement.setString(1, process.id().value());
        statement.setString(2, boardId.value());
        statement.setString(3, process.name());
        statement.setInt(4, position);
        // Not round-tripped (the snapshot has no creation time); a stable
        // value keeps the NOT NULL column happy.
        statement.setLong(5, 0L);
    }

    private void deleteBoardContents(BoardId boardId) throws SQLException {
        // Cards of this board first (join through columns): their checklist
        // items, time entries and links cascade. Then its columns, then its
        // processes.
        String deleteCards = """
                DELETE FROM card WHERE column_id IN (
                    SELECT id FROM board_column WHERE board_id = ?
                )
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(deleteCards)) {
            statement.setString(1, boardId.value());
            statement.executeUpdate();
        }
        try (PreparedStatement statement =
                     database.connection().prepareStatement("DELETE FROM board_column WHERE board_id = ?")) {
            statement.setString(1, boardId.value());
            statement.executeUpdate();
        }
        try (PreparedStatement statement =
                     database.connection().prepareStatement("DELETE FROM process WHERE board_id = ?")) {
            statement.setString(1, boardId.value());
            statement.executeUpdate();
        }
    }

    private void bindColumn(PreparedStatement statement, BoardId boardId, ColumnSnapshot column, int position)
            throws SQLException {
        statement.setString(1, column.id().value());
        statement.setString(2, boardId.value());
        statement.setString(3, column.title());
        statement.setString(4, column.description());
        statement.setString(5, column.color().stored());
        statement.setInt(6, position);
        if (column.wipLimit().asOptional().isPresent()) {
            statement.setInt(7, column.wipLimit().asOptional().get());
        } else {
            statement.setNull(7, Types.INTEGER);
        }
        statement.setLong(8, column.createdAt().toEpochMilli());
        statement.setInt(9, column.done() ? 1 : 0);
        if (column.backgroundColor() == null) {
            statement.setNull(10, Types.VARCHAR);
        } else {
            statement.setString(10, column.backgroundColor());
        }
    }

    private void bindCard(PreparedStatement statement, CardSnapshot card, ColumnId ownerId, int position)
            throws SQLException {
        statement.setString(1, card.id().value());
        statement.setString(2, ownerId.value());
        statement.setString(3, card.title());
        statement.setString(4, card.description());
        statement.setString(5, card.color().stored());
        statement.setInt(6, position);
        if (card.dueDate() != null) {
            statement.setString(7, card.dueDate().toString()); // ISO-8601: 2026-12-31
        } else {
            statement.setNull(7, Types.VARCHAR);
        }
        // Unit separator: a label containing ';' (or a comma/space, now valid
        // separators in the UI) can no longer corrupt the stored list.
        statement.setString(8, String.join("\u001F", card.labels()));
        statement.setLong(9, card.createdAt().toEpochMilli());
        statement.setString(10, card.notes());
        if (card.processId() != null) {
            statement.setString(11, card.processId().value());
        } else {
            statement.setNull(11, Types.VARCHAR);
        }
    }

    // ------------------------------------------------------------------
    // Load internals
    // ------------------------------------------------------------------

    private ColumnSnapshot readColumnRow(ResultSet resultSet) throws SQLException {
        String background = resultSet.getString("background_color");
        return new ColumnSnapshot(
                new ColumnId(resultSet.getString("id")),
                resultSet.getString("title"),
                resultSet.getString("description"),
                BoardColor.fromStored(resultSet.getString("color")),
                readWipLimit(resultSet),
                Instant.ofEpochMilli(resultSet.getLong("created_at")),
                resultSet.getInt("done") != 0,
                background == null ? null : background,
                List.of());
    }

    private List<CardSnapshot> readCards(ColumnId columnId) {
        List<CardSnapshot> cards = new ArrayList<>();
        String sql = """
                SELECT id, title, description, color, due_date, labels, created_at, notes, process_id
                FROM card
                WHERE column_id = ?
                ORDER BY position
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, columnId.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    CardId cardId = new CardId(resultSet.getString("id"));
                    String processId = resultSet.getString("process_id");
                    cards.add(new CardSnapshot(
                            cardId,
                            resultSet.getString("title"),
                            resultSet.getString("description"),
                            BoardColor.fromStored(resultSet.getString("color")),
                            readDueDate(resultSet),
                            readLabels(resultSet),
                            Instant.ofEpochMilli(resultSet.getLong("created_at")),
                            readNotes(resultSet),
                            readChecklist(cardId),
                            processId == null ? null : new ProcessId(processId)));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load cards of column " + columnId, e);
        }
        return cards;
    }

    private String readNotes(ResultSet resultSet) throws SQLException {
        String notes = resultSet.getString("notes");
        return notes == null ? "" : notes;
    }

    /** Checklist of one card, in stored position order. */
    private List<ChecklistItem> readChecklist(CardId cardId) {
        List<ChecklistItem> items = new ArrayList<>();
        String sql = """
                SELECT id, text, done FROM card_checklist_item
                WHERE card_id = ? ORDER BY position
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, cardId.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    items.add(new ChecklistItem(
                            resultSet.getString("id"),
                            resultSet.getString("text"),
                            resultSet.getInt("done") != 0));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load checklist of card " + cardId, e);
        }
        return items;
    }

    private LocalDate readDueDate(ResultSet resultSet) throws SQLException {
        String iso = resultSet.getString("due_date");
        return iso == null ? null : LocalDate.parse(iso);
    }

    private List<String> readLabels(ResultSet resultSet) throws SQLException {
        String joined = resultSet.getString("labels");
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        // Backward compatible: rows written before the \u001F switch used ';'.
        String[] parts = joined.contains("\u001F")
                ? joined.split("\u001F")
                : joined.split(";");
        return List.of(parts);
    }

    private WipLimit readWipLimit(ResultSet resultSet) throws SQLException {
        int wip = resultSet.getInt("wip_limit");
        return resultSet.wasNull() ? WipLimit.unlimited() : WipLimit.of(wip);
    }
}
