package com.personalkanban.infrastructure.sqlite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.personalkanban.application.BoardJsonMapper;
import com.personalkanban.application.port.LabelCatalogStore;
import com.personalkanban.application.port.SettingsStore;
import com.personalkanban.domain.board.LabelCatalog;

import java.util.List;
import java.util.Objects;

/**
 * Adapter implementing {@link LabelCatalogStore} on the {@code app_setting}
 * table: the global catalog is one JSON array of user label names under the
 * {@value #SETTING_KEY} key. The reserved system labels are never stored —
 * they are bootstrapped by {@link LabelCatalog}'s constructor, so a database
 * that predates the registry simply loads as "reserved only".
 * <p>
 * Malformed stored values degrade to the default (reserved-only) catalog
 * instead of blocking startup; the user can re-create labels at any time.
 */
public final class SqliteLabelCatalogStore implements LabelCatalogStore {

    /** Settings key holding the JSON array of user label names. */
    public static final String SETTING_KEY = "labels.catalog";

    private final SettingsStore settings;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = BoardJsonMapper.create();

    public SqliteLabelCatalogStore(Database database) {
        Objects.requireNonNull(database, "database");
        this.settings = new SqliteSettingsStore(database);
    }

    /** Visible for tests: the same behavior on any settings-backed store. */
    SqliteLabelCatalogStore(SettingsStore settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public LabelCatalog load() {
        LabelCatalog catalog = new LabelCatalog();
        String json = settings.get(SETTING_KEY).orElse("");
        if (json.isBlank()) {
            return catalog;
        }
        List<String> names;
        try {
            names = mapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException | IllegalArgumentException unreadable) {
            // Corrupt value: fall back to the reserved-only catalog.
            return catalog;
        }
        for (String name : names) {
            try {
                catalog.add(name);
            } catch (IllegalArgumentException skipped) {
                // Reserved duplicate, blank or over-long: not adoptable.
            }
        }
        return catalog;
    }

    @Override
    public void save(LabelCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        try {
            settings.put(SETTING_KEY, mapper.writeValueAsString(catalog.userLabels()));
        } catch (JsonProcessingException e) {
            throw new DataAccessException("Could not serialize the label catalog", e);
        }
    }
}
