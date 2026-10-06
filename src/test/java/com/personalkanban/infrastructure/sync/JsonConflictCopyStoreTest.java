package com.personalkanban.infrastructure.sync;

import com.personalkanban.application.sync.SyncConflict;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.CardSnapshot;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.ColumnSnapshot;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JsonConflictCopyStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void writesTheLosingSnapshotAndManifestToAJsonFile() throws IOException {
        BoardMemento snapshot = new BoardMemento(List.of(
                new ColumnSnapshot(new ColumnId("c1"), "Todo", "", BoardColor.BLUE,
                        WipLimit.unlimited(), Instant.parse("2026-01-01T00:00:00Z"), false, null,
                        List.of(new CardSnapshot(new CardId("k1"), "Card", "d", BoardColor.BLUE,
                                null, List.of(), Instant.parse("2026-01-01T00:00:00Z"), "", List.of(), null)))));
        List<SyncConflict> conflicts = List.of(
                new SyncConflict("card", "k1", "description", "local", "remote"));

        JsonConflictCopyStore store = new JsonConflictCopyStore(tempDir.resolve("conflicts"));
        Optional<String> reference = store.save(new BoardId("board-1"), "Board", snapshot,
                conflicts, Instant.parse("2026-01-02T03:04:05Z"));

        assertThat(reference).isPresent();
        Path file = Path.of(reference.orElseThrow());
        assertThat(file).exists();
        String json = Files.readString(file);
        assertThat(json).contains("\"boardId\" : \"board-1\"");
        assertThat(json).contains("\"field\" : \"description\"");
        assertThat(json).contains("\"title\" : \"Card\"");
    }
}
