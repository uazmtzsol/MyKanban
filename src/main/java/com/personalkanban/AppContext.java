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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private static final String CONFIG_RECENT_PREFIX = "db.recent.";
    private static final String CONFIG_LAST_DIR = "io.lastdir";
    private static final int RECENT_LIMIT = 5;

    private final Path configFile;
    private final List<Path> recentDatabases = new ArrayList<>();

    private Database database;
    private BoardService boardService;
    private SettingsStore settings;
    private ThemeManager themeManager;
    private Path databasePath;

    /**
     * Periodic checkpoint timer (user request: persist to the main file
     * every N minutes). One daemon scheduler per context, ticking against
     * whichever database is CURRENTLY open — safe across database switches.
     * Daemon on purpose: a forgotten scheduler must never block JVM exit.
     */
    private java.util.concurrent.ScheduledExecutorService checkpointScheduler;

    private AppContext(Path configFile) {
        this.configFile = configFile;
        this.recentDatabases.addAll(readRecentDatabases(configFile));
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
        // switchDatabase persists the choice and updates the recents list.
        switchDatabase(dbFile.toAbsolutePath().normalize());
    }

    /** The SQLite file currently in use. */
    public Path databasePath() {
        return databasePath;
    }

    /** Most recently opened databases, most recent first (for the menu). */
    public List<Path> recentDatabases() {
        return List.copyOf(recentDatabases);
    }

    /**
     * The directory last used for export/import (machine-local memory so the
     * file chooser opens where the user last worked). Shared by export and
     * import on purpose: both are "where do my board files live".
     * {@code empty} when never set, when the memory is unreadable, or — per
     * the requirement — when the remembered folder no longer exists (e.g. a
     * removed USB drive): the chooser then falls back to its system default.
     */
    public java.util.Optional<Path> lastTransferDirectory() {
        Properties properties = new Properties();
        if (!Files.isRegularFile(configFile)) {
            return java.util.Optional.empty();
        }
        try (InputStream in = Files.newInputStream(configFile)) {
            properties.load(in);
        } catch (IOException e) {
            return java.util.Optional.empty();
        }
        String value = properties.getProperty(CONFIG_LAST_DIR);
        if (value == null || value.isBlank()) {
            return java.util.Optional.empty();
        }
        Path dir = Path.of(value);
        return Files.isDirectory(dir) ? java.util.Optional.of(dir) : java.util.Optional.empty();
    }

    /**
     * Remember {@code dir} as the last export/import folder. Only directories
     * that exist at this moment are stored; the read side re-validates on
     * every use, so a folder that vanishes later (USB unplugged) degrades to
     * the system default chooser location.
     */
    public void rememberTransferDirectory(Path dir) {
        if (dir == null) {
            return;
        }
        Path directory = dir.toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) {
            return; // never remember a non-existent folder
        }
        Properties properties = new Properties();
        if (Files.isRegularFile(configFile)) {
            try (InputStream in = Files.newInputStream(configFile)) {
                properties.load(in);
            } catch (IOException ignored) {
                // start from scratch rather than fail the transfer
            }
        }
        properties.setProperty(CONFIG_LAST_DIR, directory.toString());
        try (OutputStream out = Files.newOutputStream(configFile)) {
            properties.store(out, "Personal Kanban launcher config");
        } catch (IOException e) {
            // best-effort memory; the transfer itself is unaffected
        }
    }

    /** Moves the target to the front of the recents list, capping the size. */
    private void rememberRecent(Path target) {
        recentDatabases.removeIf(path -> path.equals(target));
        recentDatabases.add(0, target);
        while (recentDatabases.size() > RECENT_LIMIT) {
            recentDatabases.removeLast();
        }
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
            rememberRecent(dbFile);
            writeConfiguredDatabase(dbFile);
            startPeriodicCheckpoint();
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
        for (int i = 0; i < recentDatabases.size(); i++) {
            properties.setProperty(CONFIG_RECENT_PREFIX + i, recentDatabases.get(i).toString());
        }
        Properties previous = new Properties();
        if (Files.isRegularFile(configFile)) {
            try (InputStream in = Files.newInputStream(configFile)) {
                previous.load(in);
            } catch (IOException ignored) {
                // unreadable old config: rewrite from scratch
            }
        }
        String lastDir = previous.getProperty(CONFIG_LAST_DIR);
        if (lastDir != null && !lastDir.isBlank()) {
            properties.setProperty(CONFIG_LAST_DIR, lastDir);
            }
        try {
            Files.createDirectories(configFile.getParent());
            try (OutputStream out = Files.newOutputStream(configFile)) {
                properties.store(out, "Personal Kanban launcher config");
            }
        } catch (IOException e) {
            // Remembering paths is best-effort; the app still works.
        }
    }

    private static List<Path> readRecentDatabases(Path configFile) {
        if (!Files.isRegularFile(configFile)) {
            return List.of();
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(configFile)) {
            properties.load(in);
        } catch (IOException e) {
            return List.of();
        }
        List<Path> recents = new ArrayList<>();
        for (int i = 0; i < RECENT_LIMIT; i++) {
            String value = properties.getProperty(CONFIG_RECENT_PREFIX + i);
            if (value != null && !value.isBlank()) {
                Path path = Path.of(value);
                if (Files.isRegularFile(path) && recents.stream().noneMatch(path::equals)) {
                    recents.add(path);
                }
            }
        }
        return recents;
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

    // ------------------------------------------------------------------
    // Persistence hygiene: periodic WAL checkpoint + clean shutdown
    // ------------------------------------------------------------------

    /** How often the WAL is folded into the main file (user asked ~5 min). */
    private static final int CHECKPOINT_PERIOD_MINUTES = 5;

    private void startPeriodicCheckpoint() {
        if (checkpointScheduler == null) {
            checkpointScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "pk-checkpoint");
                thread.setDaemon(true);
                return thread;
            });
            checkpointScheduler.scheduleWithFixedDelay(
                    this::checkpointQuietly,
                    CHECKPOINT_PERIOD_MINUTES, CHECKPOINT_PERIOD_MINUTES,
                    java.util.concurrent.TimeUnit.MINUTES);
        }
    }

    /** Checkpoint whatever database is open; never throws. */
    public void checkpointQuietly() {
        if (database != null) {
            database.checkpoint();
        }
    }

    /** Ctrl+S / "Guardar": checkpoint that reports success to the UI. */
    public boolean saveCheckpoint() {
        return database != null && database.checkpoint();
    }

    @Override
    public void close() {
        if (checkpointScheduler != null) {
            checkpointScheduler.shutdownNow();
            checkpointScheduler = null;
        }
        // A clean connection close is itself the final checkpoint: SQLite
        // folds the WAL into the file and deletes the -wal / -shm leftovers.
        if (database != null) {
            database.close();
        }
    }
}
