package com.personalkanban.application.sync;

import com.personalkanban.domain.board.BoardId;

import java.time.Instant;

/**
 * Catalog entry of the online board store: identity, display name
 * and sync metadata. Pure metadata — the board's contents travel
 * separately as a {@link com.personalkanban.domain.board.BoardMemento}.
 */
public record RemoteBoardInfo(BoardId id, String name, long version, Instant updatedAt) {

    public RemoteBoardInfo {
        if (id == null || name == null || updatedAt == null) {
            throw new IllegalArgumentException("Remote board info fields must not be null");
        }
    }
}
