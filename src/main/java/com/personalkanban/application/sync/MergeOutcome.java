package com.personalkanban.application.sync;

import com.personalkanban.domain.board.BoardMemento;

import java.util.List;

/**
 * Outcome of a three-way merge: the merged snapshot to apply and keep, plus
 * every field-level {@link SyncConflict} the merge had to resolve by
 * dropping one of the two edited values. A conflict copy is saved for the
 * losing side when {@link #hasConflicts()} is true (sync design §4.1).
 */
public record MergeOutcome(BoardMemento merged, List<SyncConflict> conflicts) {

    public MergeOutcome {
        if (merged == null) {
            throw new IllegalArgumentException("merged memento must not be null");
        }
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }

    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }
}
