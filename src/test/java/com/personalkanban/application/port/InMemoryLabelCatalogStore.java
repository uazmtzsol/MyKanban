package com.personalkanban.application.port;

import com.personalkanban.application.port.LabelCatalogStore;
import com.personalkanban.domain.board.LabelCatalog;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory {@link LabelCatalogStore} test double. Like the SQLite adapter it
 * persists only the user label names and rebuilds the catalog on load, so a
 * test that recreates the store/service pair proves the round-trip.
 */
public final class InMemoryLabelCatalogStore implements LabelCatalogStore {

    private final List<String> persistedUserLabels = new ArrayList<>();
    private int saveCount;

    @Override
    public LabelCatalog load() {
        LabelCatalog catalog = new LabelCatalog();
        for (String name : persistedUserLabels) {
            try {
                catalog.add(name);
            } catch (IllegalArgumentException skipped) {
                // Same resilience as the SQLite adapter.
            }
        }
        return catalog;
    }

    @Override
    public void save(LabelCatalog catalog) {
        persistedUserLabels.clear();
        persistedUserLabels.addAll(catalog.userLabels());
        saveCount++;
    }

    /** How many times the catalog was persisted (assert persistence happened). */
    public int saveCount() {
        return saveCount;
    }
}
