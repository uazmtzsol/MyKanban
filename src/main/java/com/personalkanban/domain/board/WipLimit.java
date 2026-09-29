package com.personalkanban.domain.board;

import java.util.Optional;

/**
 * Maximum number of cards a column may hold. {@code null}/empty means
 * "unlimited", which the original app treats as an empty WIP field.
 */
public record WipLimit(Integer value) {

    public WipLimit {
        if (value != null && value < 1) {
            throw new IllegalArgumentException("WIP limit must be >= 1 but was " + value);
        }
    }

    public static WipLimit unlimited() {
        return new WipLimit(null);
    }

    public static WipLimit of(int value) {
        return new WipLimit(value);
    }

    public boolean isUnlimited() {
        return value == null;
    }

    public boolean isExceededBy(int cardCount) {
        return !isUnlimited() && cardCount > value;
    }

    /** Human-readable form for captions, e.g. {@code 3/5} or {@code 3}. */
    public String describe(int cardCount) {
        return isUnlimited() ? Integer.toString(cardCount) : cardCount + "/" + value;
    }

    public Optional<Integer> asOptional() {
        return Optional.ofNullable(value);
    }
}
