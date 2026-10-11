package com.personalkanban.application;

import com.personalkanban.application.sync.ConflictCopy;
import com.personalkanban.domain.board.BoardId;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentConflictsCompileTest {

    /**
     * Verifies only that CurrentConflicts compiles and that listFor/ review
     * are callable from application code. Not a behavioral test.
     */
    @Test
    void compilesAndIsCallable() throws Exception {
        Path dir = Files.createTempDirectory("currentConflicts");
        try {
            Path conflictsDir = dir.resolve("conflicts");
            Files.createDirectories(conflictsDir);
            Path file = conflictsDir.resolve("board-1-1000.json");
            ConflictCopy copy = new ConflictCopy(
                    "board-1", "My board", 1000,
                    List.of(new com.personalkanban.application.sync.SyncConflict(
                            "Card", "c1", "title", "Local title", "Remote title")),
                    null);
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    com.personalkanban.application.BoardJsonMapper.create();
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), copy);

            CurrentConflicts conflicts = new CurrentConflicts(dir);
            List<CurrentConflicts.ConflictEntry> entries = conflicts.listFor(new BoardId("board-1"));
            assertThat(entries).hasSize(1);

            CurrentConflicts.ConflictCopyReview review = conflicts.review(file);
            assertThat(review).isNotNull();
            assertThat(review.boardId()).isEqualTo("board-1");
            assertThat(review.conflictCount()).isEqualTo(1);
        } finally {
            deleteRecursively(dir);
        }
    }

    private static void deleteRecursively(Path root) throws java.io.IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
