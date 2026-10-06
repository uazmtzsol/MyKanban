package com.personalkanban.application.sync;

import java.util.List;
import java.util.Optional;

/**
 * Result of synchronizing one board: what the local side did, whether any
 * conflict had to be resolved, and where the preserved losing copy lives.
 * The UI turns this into a discreet notice instead of a modal interruption
 * (design §4.1).
 */
public record SyncReport(String boardName, Action action, List<SyncConflict> conflicts,
                         Optional<String> conflictCopyReference) {

    /** How the local board reached agreement with the server. */
    public enum Action {
        /** The board was new remotely: the local snapshot was uploaded. */
        PUSHED_NEW,
        /** The server had not moved since the base: the local snapshot was uploaded. */
        PUSHED_LOCAL,
        /** The server had newer content and local was unchanged: pulled without a merge. */
        FAST_FORWARD,
        /** Both sides changed: a three-way merge combined them. */
        MERGED
    }

    public SyncReport {
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        conflictCopyReference = conflictCopyReference == null ? Optional.empty() : conflictCopyReference;
    }

    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }
}
