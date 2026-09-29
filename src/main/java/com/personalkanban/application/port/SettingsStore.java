package com.personalkanban.application.port;

import java.util.Optional;

/**
 * Outbound port for application-level settings (theme, language). Keeping it
 * a port lets the UI read/write preferences without knowing they end up in
 * SQLite (DIP).
 */
public interface SettingsStore {

    Optional<String> get(String key);

    void put(String key, String value);
}
