package com.personalkanban.application.sync;

import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;

import java.time.Instant;

/**
 * A board as it exists on the sync server: catalog metadata plus
 * the full snapshot. {@code version} is the server's revision
 * counter and {@code updatedAt} the server-clock stamp of the last
 * accepted write — together they decide which side is newer, never
 * client clocks.
 */
public record RemoteBoard(BoardId id, String name, long version,
                            Instant updatedAt, BoardMemento memento) {

    public RemoteBoard {
        if (id == null || name == null || updatedAt == null || memento == null) {
            throw new IllegalArgumentException("Remote board fields must not be null");
        }
    }
}
