package com.personalkanban.application;

import com.personalkanban.application.sync.ConflictCopy;
import com.personalkanban.domain.board.BoardId;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Read-only view of conflict copies stored on disk for the current database,
 * used by the conflict review UI. Keeps the snapshot payload off the hot path
 * unless a conflict is actually opened: list entries carry only the metadata
 * written into the file name plus the small JSON header.
 *
 * <p>Copies live under {@code <dataDir>/conflicts/} and are named
 * {@code <boardId>-<epochMillis>.json}. The list is filtered by board id, then
 * ordered newest-first so the most recent conflict is the one users reach
 * first.</p>
 */
public final class CurrentConflicts {

    private final Path conflictsDir;
    private final ObjectMapper mapper;

    public CurrentConflicts(Path dataDirectory) {
        this.conflictsDir = Objects.requireNonNull(dataDirectory).resolve("conflicts");
        this.mapper = BoardJsonMapper.create();
    }

    /** Conflict metadata without the full snapshot payload. */
    public record ConflictEntry(String boardId, String boardName, long savedAt,
                                int conflictCount, Path filePath) {
    }

    /** Conflicts for the given board, newest first, never null. */
    public List<ConflictEntry> listFor(BoardId boardId) {
        List<ConflictEntry> entries = new ArrayList<>();
        if (!Files.isDirectory(conflictsDir)) {
            return entries;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(conflictsDir,
                boardIdPattern(boardId))) {
            for (Path file : stream) {
                // readHeader already swallows malformed copies (returns null).
                ConflictEntry entry = readHeader(file, boardId);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        } catch (IOException ignored) {
            // Missing directory or read error: return empty.
        }
        entries.sort(Comparator.comparingLong(ConflictEntry::savedAt).reversed());
        return entries;
    }

    /**
     * Full conflict copy for review. Returns {@link ConflictCopyReview} so the
     * UI can show both the manifest and the preserved snapshot metadata without
     * the UI layer touching Jackson directly.
     */
    public record ConflictCopyReview(String boardId, String boardName,
                                     long savedAt, int conflictCount, Path filePath,
                                     List<ConflictField> fields) {
    }

    public record ConflictField(String entity, String entityId, String field,
                                String localValue, String remoteValue) {
    }

    /** Opens one conflict copy. Returns null when the file cannot be read. */
    public ConflictCopyReview review(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            ConflictCopy copy =
                    mapper.readValue(file.toFile(), ConflictCopy.class);
            List<ConflictField> fields = new ArrayList<>(copy.conflicts().size());
            for (var conflict : copy.conflicts()) {
                fields.add(new ConflictField(conflict.entity(), conflict.entityId(),
                        conflict.field(), conflict.localValue(), conflict.remoteValue()));
            }
            return new ConflictCopyReview(copy.boardId(), copy.boardName(),
                    copy.savedAt(), copy.conflicts().size(), file, fields);
        } catch (IOException e) {
            return null;
        }
    }

    private static String boardIdPattern(BoardId boardId) {
        // Only files that start with "<boardId>-" and end with ".json".
        return boardId.value() + "-*.json";
    }

    private ConflictEntry readHeader(Path file, BoardId expectedBoardId) {
        try {
            ConflictCopy copy = mapper.readValue(file.toFile(), ConflictCopy.class);
            if (!expectedBoardId.value().equals(copy.boardId())) {
                return null;
            }
            return new ConflictEntry(copy.boardId(), copy.boardName(),
                    copy.savedAt(), copy.conflicts().size(), file);
        } catch (IOException e) {
            return null;
        }
    }
}
