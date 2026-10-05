package com.personalkanban.application.port;

import com.personalkanban.application.sync.PushResult;
import com.personalkanban.application.sync.RemoteBoard;
import com.personalkanban.application.sync.RemoteBoardInfo;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for the online board store — the "Zotero-style"
 * sync target. The desktop app stays the source of truth locally;
 * this port describes the smallest protocol a sync backend must
 * speak: fetch one board's snapshot, push one snapshot under
 * optimistic concurrency, and list what the server knows.
 *
 * <p>Implementations range from a PHP/MySQL endpoint to a cloud
 * file; the application layer only sees this interface (DIP).</p>
 */
public interface SyncRepository {

    /**
     * The remote snapshot of a board; {@link Optional#empty()} when
     * the server has never seen this board.
     */
    Optional<RemoteBoard> fetch(BoardId boardId);

    /**
     * Pushes a complete snapshot. {@code baseVersion} is the version
     * the client last saw (0 when the board is new remotely); the
     * server rejects a stale push with a {@link PushResult.Conflict}
     * carrying the remote state, unless {@code force} overwrites it.
     */
    PushResult push(BoardId boardId, String name, BoardMemento memento,
                    long baseVersion, boolean force);

    /** Boards known to the server, most recently updated first. */
    List<RemoteBoardInfo> catalog();
}
