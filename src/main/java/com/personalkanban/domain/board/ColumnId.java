package com.personalkanban.domain.board;

/**
 * Typed identifier for a {@link BoardColumn}. Wrapping raw strings avoids the
 * primitive-obsession smell and makes signatures self-documenting.
 */
public record ColumnId(String value) {

    public ColumnId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ColumnId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
