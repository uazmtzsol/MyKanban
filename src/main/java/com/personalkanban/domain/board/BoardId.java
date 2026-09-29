package com.personalkanban.domain.board;

/**
 * Typed identifier for a {@code Board}. A single database can hold several
 * independent boards (personal, work, ...); this id names one of them.
 */
public record BoardId(String value) {

    public BoardId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("BoardId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
