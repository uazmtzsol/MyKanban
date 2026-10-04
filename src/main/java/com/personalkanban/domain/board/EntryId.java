package com.personalkanban.domain.board;

import java.util.Objects;
import java.util.UUID;

/** Typed identifier for a {@link TimelineEntry} (same style as CardId/ColumnId). */
public record EntryId(String value) {

    public EntryId {
        Objects.requireNonNull(value, "Entry id value must not be null");
    }

    public static EntryId newId() {
        return new EntryId(UUID.randomUUID().toString());
    }
}
