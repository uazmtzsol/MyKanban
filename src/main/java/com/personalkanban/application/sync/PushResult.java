package com.personalkanban.application.sync;

import java.time.Instant;
import java.util.Optional;

/**
 * Outcome of a
 * {@link com.personalkanban.application.port.SyncRepository#push}.
 * Sealed so callers must handle both cases: the server accepted the
 * snapshot ({@link Ok}) or refused it because another device wrote
 * first ({@link Conflict}, carrying the remote state to merge with).
 */
public sealed interface PushResult {

    /** The server accepted the snapshot and bumped its version. */
    record Ok(long version, Instant updatedAt) implements PushResult {

        public Ok {
            if (updatedAt == null) {
                throw new IllegalArgumentException("updatedAt must not be null");
            }
        }
    }

    /**
     * The server kept its own version. {@code remote} is the state it
     * kept, when the server sent it back — otherwise the caller can
     * {@link com.personalkanban.application.port.SyncRepository#fetch}
     * it to decide how to merge.
     */
    record Conflict(Optional<RemoteBoard> remote) implements PushResult {

        public Conflict {
            remote = remote == null ? Optional.empty() : remote;
        }
    }
}
