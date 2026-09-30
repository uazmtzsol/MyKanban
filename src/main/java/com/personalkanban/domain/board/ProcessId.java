package com.personalkanban.domain.board;

import java.util.Objects;

/** Typed identifier for a {@link Process} (same style as CardId/ColumnId). */
public record ProcessId(String value) {

    public ProcessId {
        Objects.requireNonNull(value, "Process id value must not be null");
    }
}
