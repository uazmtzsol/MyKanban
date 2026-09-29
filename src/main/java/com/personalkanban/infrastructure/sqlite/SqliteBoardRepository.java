package com.personalkanban.infrastructure.sqlite;

import com.personalkanban.application.port.BoardRepository;
import com.personalkanban.domain.board.BoardColor;
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

/**
 * Adapter implementing the {@link BoardRepository} port on SQLite. Stores the
 * board as one row per column and per card; order comes from per-table
 * integer positions written from list order at save time (single writer, so
 * there is no contention). Each {@link #save} is one transaction. The load
 * path reads columns first and cards afterwards, so no two JDBC statements
 * are ever open at once (required by the SQLite driver).
 */
public final class SqliteBoardRepository implements BoardRepository {

    private final Database database;

    public SqliteBoardRepository(Database database) {
        this.database = Objects.requireNonNull(database);
    }

    @Override
    public void save(BoardMemento board) {
        try {
            boolean previousAutoCommit = database.connection().getAutoCommit();
            database.connection().setAutoCommit(false);
            try {
                replaceAll(board);
                database.connection().commit();
            } catch (SQLException e) {
                database.connection().rollback();
                throw e;
            } finally {
                database.connection().setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save board", e);
        }
    }

    @Override
    public BoardMemento load() {
        List<ColumnSnapshot> columns = new ArrayList<>();
        String sql = """
                SELECT id, title, description, color, wip_limit, created_at
                FROM board_column
                ORDER BY position
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                columns.add(readColumnRow(resultSet));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load board", e);
        }

        // Second pass: fetch cards per column, with no open outer ResultSet.
        List<ColumnSnapshot> withCards = new ArrayList<>();
        for (ColumnSnapshot column : columns) {
            withCards.add(new ColumnSnapshot(column.id(), column.title(), column.description(),
                    column.color(), column.wipLimit(), column.createdAt(), readCards(column.id())));
        }
        return new BoardMemento(withCards);
    }

    // ------------------------------------------------------------------
    // Save internals
    // ------------------------------------------------------------------

    private void replaceAll(BoardMemento board) throws SQLException {
        deleteAll();
        String columnSql = """
                INSERT INTO board_column (id, title, description, color, position, wip_limit, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        String cardSql = """
                INSERT INTO card (id, column_id, title, description, color, position, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement columnStatement = database.connection().prepareStatement(columnSql);
             PreparedStatement cardStatement = database.connection().prepareStatement(cardSql)) {
            int columnIndex = 0;
            for (ColumnSnapshot column : board.columns()) {
                bindColumn(columnStatement, column, columnIndex++);
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

    private void deleteAll() throws SQLException {
        try (Statement statement = database.connection().createStatement()) {
            statement.execute("DELETE FROM card");
            statement.execute("DELETE FROM board_column");
        }
    }

    private void bindColumn(PreparedStatement statement, ColumnSnapshot column, int position)
            throws SQLException {
        statement.setString(1, column.id().value());
        statement.setString(2, column.title());
        statement.setString(3, column.description());
        statement.setString(4, column.color().name());
        statement.setInt(5, position);
        if (column.wipLimit().asOptional().isPresent()) {
            statement.setInt(6, column.wipLimit().asOptional().get());
        } else {
            statement.setNull(6, Types.INTEGER);
        }
        // Epoch millis as an integer: driver-agnostic and timezone-free.
        statement.setLong(7, column.createdAt().toEpochMilli());
    }

    private void bindCard(PreparedStatement statement, CardSnapshot card, ColumnId ownerId, int position)
            throws SQLException {
        statement.setString(1, card.id().value());
        statement.setString(2, ownerId.value());
        statement.setString(3, card.title());
        statement.setString(4, card.description());
        statement.setString(5, card.color().name());
        statement.setInt(6, position);
        statement.setLong(7, card.createdAt().toEpochMilli());
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
                SELECT id, title, description, color, created_at
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
                            Instant.ofEpochMilli(resultSet.getLong("created_at"))));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load cards of column " + columnId, e);
        }
        return cards;
    }

    private WipLimit readWipLimit(ResultSet resultSet) throws SQLException {
        int wip = resultSet.getInt("wip_limit");
        return resultSet.wasNull() ? WipLimit.unlimited() : WipLimit.of(wip);
    }
}
