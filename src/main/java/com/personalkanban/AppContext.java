package com.personalkanban;

import com.personalkanban.application.BoardService;
import com.personalkanban.application.port.SettingsStore;
import com.personalkanban.infrastructure.history.JsonUndoHistory;
import com.personalkanban.infrastructure.sqlite.Database;
import com.personalkanban.infrastructure.sqlite.SchemaMigrator;
import com.personalkanban.infrastructure.sqlite.SqliteBoardRepository;
import com.personalkanban.infrastructure.sqlite.SqliteSettingsStore;
import com.personalkanban.ui.theme.ThemeManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * Composition root (Pure Fabrication): the single place where concrete classes
 * of every layer are wired together, and the owner of the <em>database
 * file</em> lifecycle. The user may create a new database file or open an
 * existing one at any moment ({@link #openDatabase}); everything is re-wired
 * atomically, and the chosen file is remembered in a small machine-local
 * config so the next launch reopens it. Undo history always lives in a
 * {@code history/} folder next to the database file, so a database is a
 * self-contained unit one can put on a USB drive or a synced cloud folder.
 */
public final class AppContext implements AutoCloseable {

    private static final String SETTING_LANGUAGE = "ui.language";
    private static final String SETTING_THEME = "ui.theme";
    private static final String CONFIG_DB_PATH = "db.path";

    private final Path configFile;

    private Database database;
    private BoardService boardService;
    private SettingsStore settings;
    private ThemeManager themeManager;
    private Path databasePath;

    private AppContext(Path configFile) {
        this.configFile = configFile;
    }

    /** Wires the last-used database (if it still exists) or the default one. */
    public static AppContext create() {
        Path dataDir = Main.dataDirectory();
        return createWith(dataDir.resolve("config.properties"), dataDir.resolve("kanban.db"));
    }

    /** Test seam: explicit config file and fallback database. */
    public static AppContext createWith(Path configFile, Path fallbackDb) {
        Path configured = readConfiguredDatabase(configFile);
        Path dbFile = configured != null && Files.isRegularFile(configured)
                ? configured
                : fallbackDb;
        return openAt(dbFile, configFile);
    }

    /** Wires a specific database file (created with its schema when absent). */
    public static AppContext openAt(Path dbFile, Path configFile) {
        AppContext context = new AppContext(configFile);
        context.switchDatabase(dbFile.toAbsolutePath().normalize());
        return context;
    }

    /**
     * Switches the whole application to the database at the given path,
     * creating it when absent. If the file is not a valid kanban database the
     * current one stays open and the failure propagates to the caller.
     */
    public void openDatabase(Path dbFile) {
        Path target = dbFile.toAbsolutePath().normalize();
        switchDatabase(target); // may throw; current state stays intact then
        writeConfiguredDatabase(target);
    }

    /** The SQLite file currently in use. */
    public Path databasePath() {
        return databasePath;
    }

    private void switchDatabase(Path dbFile) {
        Database newDatabase = new Database(dbFile);
        try {
            new SchemaMigrator(newDatabase).migrate();
            BoardService newService = new BoardService(
                    new SqliteBoardRepository(newDatabase),
                    new JsonUndoHistory(dbFile.getParent().resolve("history")),
                    new SqliteSettingsStore(newDatabase));
            SettingsStore newSettings = new SqliteSettingsStore(newDatabase);
            ThemeManager newTheme = new ThemeManager(newSettings.get(SETTING_THEME)
                    .map(ThemeManager.Theme::valueOf)
                    .orElse(ThemeManager.Theme.LIGHT));

            // Commit point: everything built; now swap the live wiring.
            if (database != null) {
                database.close();
            }
            database = newDatabase;
            boardService = newService;
            settings = newSettings;
            themeManager = newTheme;
            databasePath = dbFile;
        } catch (RuntimeException failure) {
            try {
                newDatabase.close();
            } catch (RuntimeException ignored) {
                // the original failure is more interesting than this one
            }
            throw failure;
        }
    }

    // ------------------------------------------------------------------
    // Machine-local memory of the last database ("Open recent" on launch)
    // ------------------------------------------------------------------

    private static Path readConfiguredDatabase(Path configFile) {
        if (!Files.isRegularFile(configFile)) {
            return null;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(configFile)) {
            properties.load(in);
        } catch (IOException e) {
            return null; // unreadable config falls back to the default database
        }
        String value = properties.getProperty(CONFIG_DB_PATH);
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    private void writeConfiguredDatabase(Path dbFile) {
        Properties properties = new Properties();
        properties.setProperty(CONFIG_DB_PATH, dbFile.toString());
        try {
            Files.createDirectories(configFile.getParent());
            try (OutputStream out = Files.newOutputStream(configFile)) {
                properties.store(out, "Personal Kanban launcher config");
            }
        } catch (IOException e) {
            // Remembering the path is best-effort; the app still works.
        }
    }

    // ------------------------------------------------------------------
    // Accessors for the UI
    // ------------------------------------------------------------------

    public BoardService boardService() {
        return boardService;
    }

    public ThemeManager themeManager() {
        return themeManager;
    }

    public Locale savedLocale() {
        return settings.get(SETTING_LANGUAGE)
                .map(Locale::forLanguageTag)
                .orElseGet(() -> Locale.of("en"));
    }

    public void saveLocale(Locale locale) {
        settings.put(SETTING_LANGUAGE, locale.toLanguageTag());
    }

    public void saveTheme(ThemeManager.Theme theme) {
        settings.put(SETTING_THEME, theme.name());
    }

    @Override
    public void close() {
        if (database != null) {
            database.close();
        }
    }
}
