package com.personalkanban.infrastructure.sqlite;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal versioned schema migrator (Pure Fabrication): applies classpath
 * scripts named {@code V<version>__description.sql} in order, recording each
 * applied version in a {@code schema_version} table. Scripts are registered
 * explicitly below — migrations are code artifacts, and an explicit list
 * beats fragile classpath scanning inside jars. Hand-rolled to avoid dragging
 * a migration framework into an offline desktop app.
 */
public final class SchemaMigrator {

    private static final Pattern SCRIPT_NAME = Pattern.compile("/db/migration/V(\\d+)__(.+)\\.sql");

    /** Append new migrations here, in order: "/db/migration/V2__short_description.sql", ... */
    private static final List<String> REGISTERED_SCRIPTS = List.of(
            "/db/migration/V1__init.sql",
            "/db/migration/V2__app_settings.sql",
            "/db/migration/V3__card_details.sql",
            "/db/migration/V4__boards.sql"
    );

    private final Database database;

    public SchemaMigrator(Database database) {
        this.database = database;
    }

    public void migrate() {
        ensureVersionTable();
        int current = currentVersion();
        for (RegisteredScript script : parseScripts()) {
            if (script.version() > current) {
                apply(script);
            }
        }
    }

    private void ensureVersionTable() {
        try (Statement statement = database.connection().createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version     INTEGER PRIMARY KEY,
                        description TEXT NOT NULL,
                        applied_at  TEXT NOT NULL DEFAULT (datetime('now'))
                    )
                    """);
        } catch (SQLException e) {
            throw new DataAccessException("Could not create schema_version table", e);
        }
    }

    private int currentVersion() {
        try (var statement = database.connection().createStatement();
             var resultSet = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            resultSet.next();
            return resultSet.getInt(1);
        } catch (SQLException e) {
            throw new DataAccessException("Could not read current schema version", e);
        }
    }

    private List<RegisteredScript> parseScripts() {
        List<RegisteredScript> scripts = new ArrayList<>();
        for (String path : REGISTERED_SCRIPTS) {
            Matcher matcher = SCRIPT_NAME.matcher(path);
            if (!matcher.matches()) {
                throw new DataAccessException("Migration script name does not match V<n>__<desc>.sql: " + path);
            }
            scripts.add(new RegisteredScript(Integer.parseInt(matcher.group(1)),
                    matcher.group(2), readResource(path)));
        }
        return scripts;
    }

    private void apply(RegisteredScript script) {
        try (Statement statement = database.connection().createStatement()) {
            for (String sql : splitStatements(script.body())) {
                statement.execute(sql);
            }
            try (var insert = database.connection().prepareStatement(
                    "INSERT INTO schema_version (version, description) VALUES (?, ?)")) {
                insert.setInt(1, script.version());
                insert.setString(2, script.description());
                insert.executeUpdate();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Migration V" + script.version() + " failed", e);
        }
    }

    /** Splits on semicolons at line ends; sufficient for our own scripts. */
    static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            current.append(line).append('\n');
            if (trimmed.endsWith(";")) {
                statements.add(current.toString().strip());
                current.setLength(0);
            }
        }
        String remainder = current.toString().strip();
        if (!remainder.isEmpty()) {
            statements.add(remainder);
        }
        return statements;
    }

    private record RegisteredScript(int version, String description, String body) {
    }

    private static String readResource(String path) {
        try (InputStream in = SchemaMigrator.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new DataAccessException("Migration script not found on classpath: " + path);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().reduce((a, b) -> a + "\n" + b).orElse("");
            }
        } catch (IOException e) {
            throw new DataAccessException("Could not read migration script " + path, e);
        }
    }
}
