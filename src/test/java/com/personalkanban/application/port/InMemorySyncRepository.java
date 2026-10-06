package com.personalkanban.application.port;

import com.personalkanban.application.sync.PushResult;
import com.personalkanban.application.sync.RemoteBoard;
import com.personalkanban.application.sync.RemoteBoardInfo;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@link SyncRepository} test double that mimics the server's
 * optimistic concurrency: a push is rejected with a {@link PushResult.Conflict}
 * when its base version is stale, unless {@code force} is asked.
 */
public final class InMemorySyncRepository implements SyncRepository {

    private record Stored(String name, long version, Instant updatedAt, BoardMemento memento) {
    }

    private final Map<BoardId, Stored> store = new LinkedHashMap<>();
    public int pushes;

    /** Seeds/overwrites a board on the server, bumping its version. */
    public void putRemote(BoardId boardId, String name, BoardMemento memento) {
        Stored previous = store.get(boardId);
        long version = previous == null ? 1 : previous.version() + 1;
        store.put(boardId, new Stored(name, version, Instant.now(), memento));
    }

    @Override
    public Optional<RemoteBoard> fetch(BoardId boardId) {
        Stored stored = store.get(boardId);
        return stored == null ? Optional.empty()
                : Optional.of(new RemoteBoard(boardId, stored.name(), stored.version(),
                        stored.updatedAt(), stored.memento()));
    }

    @Override
    public PushResult push(BoardId boardId, String name, BoardMemento memento,
                           long baseVersion, boolean force) {
        pushes++;
        Stored current = store.get(boardId);
        if (current == null) {
            if (baseVersion != 0 && baseVersion != -1 && !force) {
                return new PushResult.Conflict(Optional.empty());
            }
            store.put(boardId, new Stored(name, 1, Instant.now(), memento));
            return new PushResult.Ok(1, Instant.now());
        }
        if (!force && baseVersion != current.version()) {
            RemoteBoard remote = new RemoteBoard(boardId, current.name(), current.version(),
                    current.updatedAt(), current.memento());
            return new PushResult.Conflict(Optional.of(remote));
        }
        long next = current.version() + 1;
        store.put(boardId, new Stored(name, next, Instant.now(), memento));
        return new PushResult.Ok(next, Instant.now());
    }

    @Override
    public List<RemoteBoardInfo> catalog() {
        List<RemoteBoardInfo> infos = new ArrayList<>();
        store.forEach((id, stored) -> infos.add(
                new RemoteBoardInfo(id, stored.name(), stored.version(), stored.updatedAt())));
        return infos;
    }
}
