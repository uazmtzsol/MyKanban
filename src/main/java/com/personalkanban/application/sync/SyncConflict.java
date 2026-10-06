package com.personalkanban.application.sync;

/**
 * One field-level conflict detected while merging (sync design §4.1): both
 * sides changed the same scalar field of the same object since the common
 * base, so one value had to lose. The loser is preserved verbatim so the
 * UI can offer the user a conflict copy instead of discarding it silently.
 *
 * <p>Values are the canonical renderings used by the merge engine (e.g. a
 * color as {@code "name|#rrggbb"}), stable across machines so the conflict
 * is reported identically no matter which device ran the merge.</p>
 */
public record SyncConflict(String entity, String entityId, String field,
                           String localValue, String remoteValue) {

    public SyncConflict {
        if (entity == null || entityId == null || field == null) {
            throw new IllegalArgumentException("Conflict fields must not be null");
        }
        localValue = localValue == null ? "" : localValue;
        remoteValue = remoteValue == null ? "" : remoteValue;
    }

    /** Human-readable location of the conflict, used in the notice dialog. */
    public String describe() {
        return entity + " " + entityId + " · " + field;
    }
}
