package com.personalkanban.domain.board;

/**
 * Typed identifier for a {@link Card}. Wrapping raw strings avoids the
 * primitive-obsession smell and makes signatures self-documenting.
 */
public record CardId(String value) {

    public CardId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CardId must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
