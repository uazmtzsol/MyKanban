package com.personalkanban.application.port;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for application-level settings (theme, language). Keeping it
 * a port lets the UI read/write preferences without knowing they end up in
 * SQLite (DIP).
 */
public interface SettingsStore {

    Optional<String> get(String key);

    void put(String key, String value);

    /**
     * Every stored key that starts with {@code prefix} (e.g. {@code
     * "ui.label."}), so the preferences UI can enumerate the rules a
     * user has already defined. Sorted for stable display. Implementations
     * that cannot enumerate may return an empty list.
     */
    default List<String> keys(String prefix) {
        return List.of();
    }
}
