package com.personalkanban;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Recent-databases behavior: order, cap at 5, dedup, persistence across restarts. */
class AppContextRecentsTest {

    @TempDir
    Path tempDir;

    private Path configFile() {
        return tempDir.resolve("config.properties");
    }

    private Path db(String name) {
        try (AppContext context = AppContext.openAt(tempDir.resolve(name), configFile())) {
            return context.databasePath();
        }
    }

    @Test
    void openedDatabasesAreListedMostRecentFirst() {
        Path a = db("a.db");
        Path b = db("b.db");

        try (AppContext context = AppContext.openAt(b, configFile())) {
            assertThat(context.recentDatabases()).containsExactly(b, a);
        }
    }

    @Test
    void reopeningMovesToFrontWithoutDuplicates() {
        Path a = db("a.db");
        Path b = db("b.db");
        Path c = db("c.db");

        try (AppContext context = AppContext.openAt(a, configFile())) {
            assertThat(context.recentDatabases()).containsExactly(a, c, b);
        }
    }

    @Test
    void recentsAreCappedAtFive() {
        Path a = db("a.db");
        Path b = db("b.db");
        Path c = db("c.db");
        Path d = db("d.db");
        Path e = db("e.db");
        Path f = db("f.db");

        try (AppContext context = AppContext.openAt(f, configFile())) {
            assertThat(context.recentDatabases()).hasSize(5);
            assertThat(context.recentDatabases()).doesNotContain(a); // oldest dropped
            assertThat(context.recentDatabases()).containsExactly(f, e, d, c, b);
        }
    }

    @Test
    void recentsSurviveRestartViaConfig() {
        Path a = db("a.db");
        Path b = db("b.db");

        try (AppContext context = AppContext.createWith(configFile(), tempDir.resolve("fallback.db"))) {
            assertThat(context.recentDatabases()).containsExactly(b, a);
        }
    }
}
