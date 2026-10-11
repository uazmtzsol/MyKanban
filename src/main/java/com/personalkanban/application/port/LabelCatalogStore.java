package com.personalkanban.application.port;

import com.personalkanban.domain.board.LabelCatalog;

/**
 * Persistence port for the global label catalog. The catalog is global to the
 * database, not to a single board, so it lives in its own store rather than in
 * the per-board repository.
 * <p>
 * Contract mirrors the repository pattern used by {@link com.personalkanban.application.port.BoardRepository}:
 * a mutable catalog can be loaded, mutated in memory, and saved back as a unit.
 * Missing data is represented as an empty-but-valid catalog, never null.
 */
public interface LabelCatalogStore {

    /** Current global label catalog for this database (empty if never customized). */
    LabelCatalog load();

    /** Persists the given catalog. Idempotent. */
    void save(LabelCatalog catalog);
}
