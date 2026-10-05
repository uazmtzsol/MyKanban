package com.personalkanban.infrastructure.sqlite;

import com.personalkanban.application.port.SettingsStore;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Adapter implementing {@link SettingsStore} on the {@code app_setting} table. */
public final class SqliteSettingsStore implements SettingsStore {

    private final Database database;

    public SqliteSettingsStore(Database database) {
        this.database = database;
    }

    @Override
    public Optional<String> get(String key) {
        String sql = "SELECT value FROM app_setting WHERE key = ?";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(resultSet.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read setting '" + key + "'", e);
        }
    }

    @Override
    public void put(String key, String value) {
        String sql = """
                INSERT INTO app_setting (key, value) VALUES (?, ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                """;
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not write setting '" + key + "'", e);
        }
    }

    @Override
    public List<String> keys(String prefix) {
        String sql = "SELECT key FROM app_setting WHERE key LIKE ? ORDER BY key";
        List<String> keys = new ArrayList<>();
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, prefix + "%");
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    keys.add(resultSet.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not list settings", e);
        }
        return keys;
    }
}
