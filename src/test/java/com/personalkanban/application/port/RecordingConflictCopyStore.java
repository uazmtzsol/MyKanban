package com.personalkanban.application.port;

import com.personalkanban.application.sync.SyncConflict;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** In-memory {@link ConflictCopyStore} double that records the last save. */
public final class RecordingConflictCopyStore implements ConflictCopyStore {

    public int saves;
    public BoardMemento lastSnapshot;
    public List<SyncConflict> lastConflicts = List.of();

    @Override
    public Optional<String> save(BoardId boardId, String boardName, BoardMemento snapshot,
                                 List<SyncConflict> conflicts, Instant when) {
        saves++;
        lastSnapshot = snapshot;
        lastConflicts = List.copyOf(conflicts);
        return Optional.of("copy-" + saves + ".json");
    }
}
