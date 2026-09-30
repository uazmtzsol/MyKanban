package com.personalkanban.infrastructure.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Owns the single SQLite connection (GRASP Pure Fabrication: nobody else
 * touches JDBC beyond the repository and migrator). Enables WAL journaling
 * and foreign-key enforcement on every connection. On any construction
 * failure the half-opened connection is closed before the exception
 * propagates, so an invalid file never leaks a Windows file lock.
 */
public final class Database implements AutoCloseable {

    static {
        // Fails fast at startup if the driver artifact is missing from the classpath.
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("SQLite JDBC driver not found on classpath", e);
        }
    }

    private final Connection connection;

    public Database(Path databaseFile) {
        Objects.requireNonNull(databaseFile);
        Connection opened = null;
        try {
            if (databaseFile.getParent() != null) {
                Files.createDirectories(databaseFile.getParent());
            }
            String url = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
            opened = DriverManager.getConnection(url);
            configure(opened);
            this.connection = opened;
        } catch (IOException | SQLException | RuntimeException e) {
            closeQuietly(opened);
            throw new DataAccessException("Could not open SQLite database at " + databaseFile, e);
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // nothing better to do while already failing
            }
        }
    }

    /** In-memory database for tests and quick trials. */
    public static Database inMemory() {
        return new Database(Path.of(":memory:"));
    }

    /**
     * Folds the WAL journal into the main database file (user request:
     * "persistir los cambios" periodically). SQLite already guarantees
     * durability of every committed transaction — the WAL is part of the
     * database — but a TRUNCATE checkpoint keeps {@code kanban.db} itself
     * up to date and shrinks the {@code -wal} file to zero bytes, so a
     * periodic checkpoint is cheap reassurance. Best-effort: a busy
     * checkpoint just returns false and the next one will succeed.
     */
    public boolean checkpoint() {
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            return true;
        } catch (SQLException e) {
            // Never break the app for an optimization: the data is safe
            // in the WAL regardless of this result.
            return false;
        }
    }

    private static void configure(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA foreign_keys = ON");
        }
    }

    public Connection connection() {
        return connection;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new DataAccessException("Could not close SQLite connection", e);
        }
    }
}
