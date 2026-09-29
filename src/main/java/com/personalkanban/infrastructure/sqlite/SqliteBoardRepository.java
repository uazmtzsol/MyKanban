package com.personalkanban.infrastructure.sqlite;

import com.personalkanban.application.port.BoardRepository;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardDescriptor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.CardSnapshot;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.ColumnSnapshot;
import com.personalkanban.domain.board.WipLimit;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
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
        String sql = "SELECT id, name, created_at FROM board ORDER BY created_at, name";
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
                SELECT id, title, description, color, wip_limit, created_at
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
                    column.color(), column.wipLimit(), column.createdAt(), readCards(column.id())));
        }
        return new BoardMemento(withCards);
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
                INSERT INTO board_column (id, board_id, title, description, color, position, wip_limit, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String cardSql = """
                INSERT INTO card (id, column_id, title, description, color, position, due_date, labels, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement columnStatement = database.connection().prepareStatement(columnSql);
             PreparedStatement cardStatement = database.connection().prepareStatement(cardSql)) {
            int columnIndex = 0;
            for (ColumnSnapshot column : board.columns()) {
                bindColumn(columnStatement, boardId, column, columnIndex++);
                columnStatement.addBatch();
                int cardIndex = 0;
                for (CardSnapshot card : column.cards()) {
                    bindCard(cardStatement, card, column.id(), cardIndex++);
                    cardStatement.addBatch();
                }
            }
            columnStatement.executeBatch();
            cardStatement.executeBatch();
        }
    }

    private void deleteBoardContents(BoardId boardId) throws SQLException {
        // Cards of this board first (join through columns), then its columns.
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
    }

    private void bindColumn(PreparedStatement statement, BoardId boardId, ColumnSnapshot column, int position)
            throws SQLException {
        statement.setString(1, column.id().value());
        statement.setString(2, boardId.value());
        statement.setString(3, column.title());
        statement.setString(4, column.description());
        statement.setString(5, column.color().name());
        statement.setInt(6, position);
        if (column.wipLimit().asOptional().isPresent()) {
            statement.setInt(7, column.wipLimit().asOptional().get());
        } else {
            statement.setNull(7, Types.INTEGER);
        }
        statement.setLong(8, column.createdAt().toEpochMilli());
    }

    private void bindCard(PreparedStatement statement, CardSnapshot card, ColumnId ownerId, int position)
            throws SQLException {
        statement.setString(1, card.id().value());
        statement.setString(2, ownerId.value());
        statement.setString(3, card.title());
        statement.setString(4, card.description());
        statement.setString(5, card.color().name());
        statement.setInt(6, position);
        if (card.dueDate() != null) {
            statement.setString(7, card.dueDate().toString()); // ISO-8601: 2026-12-31
        } else {
            statement.setNull(7, Types.VARCHAR);
        }
        statement.setString(8, String.join(";", card.labels()));
        statement.setLong(9, card.createdAt().toEpochMilli());
    }

    // ------------------------------------------------------------------
    // Load internals
    // ------------------------------------------------------------------

    private ColumnSnapshot readColumnRow(ResultSet resultSet) throws SQLException {
        return new ColumnSnapshot(
                new ColumnId(resultSet.getString("id")),
                resultSet.getString("title"),
                resultSet.getString("description"),
                BoardColor.fromName(resultSet.getString("color")),
                readWipLimit(resultSet),
                Instant.ofEpochMilli(resultSet.getLong("created_at")),
                List.of());
    }

    private List<CardSnapshot> readCards(ColumnId columnId) {
        List<CardSnapshot> cards = new ArrayList<>();
        String sql = """
                SELECT id, title, description, color, due_date, labels, created_at
                FROM card
                WHERE column_id = ?
                ORDER BY position
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, columnId.value());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    cards.add(new CardSnapshot(
                            new CardId(resultSet.getString("id")),
                            resultSet.getString("title"),
                            resultSet.getString("description"),
                            BoardColor.fromName(resultSet.getString("color")),
                            readDueDate(resultSet),
                            readLabels(resultSet),
                            Instant.ofEpochMilli(resultSet.getLong("created_at"))));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load cards of column " + columnId, e);
        }
        return cards;
    }

    private java.time.LocalDate readDueDate(ResultSet resultSet) throws SQLException {
        String iso = resultSet.getString("due_date");
        return iso == null ? null : java.time.LocalDate.parse(iso);
    }

    private List<String> readLabels(ResultSet resultSet) throws SQLException {
        String joined = resultSet.getString("labels");
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return List.of(joined.split(";"));
    }

    private WipLimit readWipLimit(ResultSet resultSet) throws SQLException {
        int wip = resultSet.getInt("wip_limit");
        return resultSet.wasNull() ? WipLimit.unlimited() : WipLimit.of(wip);
    }
}
