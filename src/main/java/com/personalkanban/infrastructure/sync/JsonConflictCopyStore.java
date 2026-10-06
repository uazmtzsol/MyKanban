package com.personalkanban.infrastructure.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalkanban.application.BoardJsonMapper;
import com.personalkanban.application.port.ConflictCopyStore;
import com.personalkanban.application.sync.SyncConflict;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Writes conflict copies as JSON files under {@code <dbDir>/conflicts/}, next
 * to the database and its undo history so a database folder keeps every
 * artifact together. The file holds the full losing snapshot plus the manifest
 * of fields that were in conflict, so a user can recover a dropped edit by
 * hand. Failures are swallowed (returning empty): a missing copy must never
 * abort the sync itself.
 */
public final class JsonConflictCopyStore implements ConflictCopyStore {

    private final Path conflictsDir;
    private final ObjectMapper mapper;

    public JsonConflictCopyStore(Path conflictsDir) {
        this.conflictsDir = Objects.requireNonNull(conflictsDir);
        this.mapper = BoardJsonMapper.create();
    }

    @Override
    public Optional<String> save(BoardId boardId, String boardName, BoardMemento snapshot,
                                 List<SyncConflict> conflicts, Instant when) {
        Instant stamp = when == null ? Instant.now() : when;
        ConflictCopy copy = new ConflictCopy(
                boardId.value(), boardName, stamp.toEpochMilli(), conflicts, snapshot);
        Path target = conflictsDir.resolve(boardId.value() + "-" + stamp.toEpochMilli() + ".json");
        try {
            Files.createDirectories(conflictsDir);
            try (OutputStream out = Files.newOutputStream(target)) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(out, copy);
            }
            return Optional.of(target.toString());
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** On-disk shape of a conflict copy: metadata + manifest + full snapshot. */
    public record ConflictCopy(String boardId, String boardName, long savedAt,
                               List<SyncConflict> conflicts, BoardMemento snapshot) {
    }
}
