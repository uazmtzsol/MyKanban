package com.personalkanban.application.port;

import com.personalkanban.application.sync.SyncConflict;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port for preserving the losing version of a sync conflict (design
 * §4.1, "Zotero-style"): when a merge has to drop one of two competing edits,
 * the losing snapshot is written aside verbatim so nothing is discarded
 * silently. Returns a human-readable reference (typically a file path) the UI
 * can show, or empty when the copy could not be stored.
 */
public interface ConflictCopyStore {

    /**
     * Writes the losing snapshot plus the manifest of conflicting fields.
     *
     * @return a displayable reference to the stored copy, or empty on failure.
     */
    java.util.Optional<String> save(BoardId boardId, String boardName, BoardMemento snapshot,
                                    List<SyncConflict> conflicts, Instant when);
}
