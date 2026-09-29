package com.personalkanban;

import com.personalkanban.application.BoardService;
import com.personalkanban.application.port.SettingsStore;
import com.personalkanban.application.port.UndoHistory;
import com.personalkanban.infrastructure.history.JsonUndoHistory;
import com.personalkanban.infrastructure.sqlite.Database;
import com.personalkanban.infrastructure.sqlite.SchemaMigrator;
import com.personalkanban.infrastructure.sqlite.SqliteBoardRepository;
import com.personalkanban.infrastructure.sqlite.SqliteSettingsStore;
import com.personalkanban.ui.theme.ThemeManager;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Composition root (Pure Fabrication): the single place where concrete classes
 * of every layer are wired together at startup. Everything else depends only
 * on interfaces and the domain. It lives outside every layer package so the
 * architecture rules stay clean: only the root may know all layers.
 */
public final class AppContext implements AutoCloseable {

    private static final String SETTING_LANGUAGE = "ui.language";
    private static final String SETTING_THEME = "ui.theme";

    private final BoardService boardService;
    private final SettingsStore settings;
    private final ThemeManager themeManager;

    private AppContext(BoardService boardService, SettingsStore settings, ThemeManager themeManager) {
        this.boardService = boardService;
        this.settings = settings;
        this.themeManager = themeManager;
    }

    public static AppContext create() {
        Path dataDir = Main.dataDirectory();
        Database database = new Database(dataDir.resolve("kanban.db"));
        new SchemaMigrator(database).migrate();
        BoardService boardService = new BoardService(
                new SqliteBoardRepository(database),
                new JsonUndoHistory(dataDir.resolve("history")),
                new SqliteSettingsStore(database));
        SettingsStore settings = new SqliteSettingsStore(database);
        ThemeManager themeManager = new ThemeManager(
                settings.get(SETTING_THEME).map(ThemeManager.Theme::valueOf).orElse(ThemeManager.Theme.LIGHT));
        return new AppContext(boardService, settings, themeManager);
    }

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
        // SQLite flushes on JVM exit; WAL recovery handles the rest.
    }
}
