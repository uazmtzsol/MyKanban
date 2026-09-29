package com.personalkanban.domain.board;

import java.time.Instant;

/**
 * Catalog entry describing one board: identity, display name and creation
 * time. Pure metadata — the board's contents travel separately as a
 * {@link BoardMemento}.
 */
public record BoardDescriptor(BoardId id, String name, Instant createdAt) {

    public BoardDescriptor {
        if (id == null) {
            throw new IllegalArgumentException("Board id must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Board name must not be blank");
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        name = name.strip();
    }
}
