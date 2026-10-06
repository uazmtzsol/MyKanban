package com.personalkanban.infrastructure.sqlite;

import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardMemento;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.CardLink;
import com.personalkanban.domain.board.CardSnapshot;
import com.personalkanban.domain.board.ChecklistItem;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.ColumnSnapshot;
import com.personalkanban.domain.board.EntryId;
import com.personalkanban.domain.board.TimelineEntry;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Write-latency benchmark (user request after removing WAL): the
 * rollback journal (DELETE mode) pays one fsync per committed
 * transaction, so this measures the real per-commit cost of the
 * app's dominant write — {@code save()} of a full board (the
 * repository replaces the whole contents in one transaction).
 *
 * <p>The printed percentiles are the deliverable; the assertion is
 * only a smoke bound (a local fsync commit is milliseconds even on
 * a slow disk, so a p95 near a second would mean something is
 * deeply wrong).</p>
 */
class WriteLatencyBenchmarkTest {

    private static final int COLUMNS = 4;
    private static final int CARDS_PER_COLUMN = 12;
    private static final int WARMUP = 20;
    private static final int MEASURED = 200;

    @TempDir
    Path tempDir;

    @Test
    void commitLatencyIsImperceptibleWithoutWal() throws Exception {
        Path dbFile = tempDir.resolve("kanban.db");
        try (Database database = new Database(dbFile)) {
            new SchemaMigrator(database).migrate();

            // The benchmark measures DELETE-mode commit cost.
            try (var rs = database.connection().createStatement()
                    .executeQuery("PRAGMA journal_mode")) {
                assertThat(rs.getString(1)).isEqualTo("delete");
            }

            var repository = new SqliteBoardRepository(database);
            var boardId = repository.createBoard("Benchmark").id();
            BoardMemento memento = realisticBoard();

            // Warmup: JIT compilation and OS page cache, not measured.
            for (int i = 0; i < WARMUP; i++) {
                repository.save(boardId, memento);
            }

            long[] nanos = new long[MEASURED];
            for (int i = 0; i < MEASURED; i++) {
                long start = System.nanoTime();
                repository.save(boardId, memento);
                nanos[i] = System.nanoTime() - start;
            }
            Arrays.sort(nanos);
            double p50 = nanos[percentileIndex(0.50)] / 1_000_000.0;
            double p95 = nanos[percentileIndex(0.95)] / 1_000_000.0;
            double max = nanos[MEASURED - 1] / 1_000_000.0;

            System.out.printf("[WRITE-LATENCY] board with %d cards, %d measured saves%n",
                    COLUMNS * CARDS_PER_COLUMN, MEASURED);
            System.out.printf("[WRITE-LATENCY] per-commit ms: p50=%.2f p95=%.2f max=%.2f%n",
                    p50, p95, max);

            // Smoke bound only: one fsync per commit is milliseconds
            // even on a slow disk; see the printed percentiles.
            assertThat(p95).isLessThan(1000.0);

            // No commit ever created WAL sidecar files.
            assertThat(tempDir.resolve("kanban.db-wal")).doesNotExist();
            assertThat(tempDir.resolve("kanban.db-shm")).doesNotExist();
        }
    }

    private static int percentileIndex(double percentile) {
        return (int) Math.ceil(percentile * MEASURED) - 1;
    }

    /** A realistic "small data" board: 4 columns x 12 cards with notes,
     *  labels, checklists, one precedence link and one time entry. */
    private static BoardMemento realisticBoard() {
        Instant now = Instant.now();
        List<ColumnSnapshot> columns = new ArrayList<>();
        for (int c = 0; c < COLUMNS; c++) {
            List<CardSnapshot> cards = new ArrayList<>();
            for (int k = 0; k < CARDS_PER_COLUMN; k++) {
                cards.add(new CardSnapshot(
                        new CardId("card-" + c + "-" + k),
                        "Card " + c + "-" + k,
                        "Description of card " + c + "-" + k,
                        BoardColor.DEFAULT, null,
                        List.of("idea", "urgent"), now,
                        "Working notes for card " + c + "-" + k + " …".repeat(3),
                        List.of(
                                new ChecklistItem("chk-" + c + "-" + k + "-1", "First step", true),
                                new ChecklistItem("chk-" + c + "-" + k + "-2", "Second step", false)),
                        null));
            }
            columns.add(new ColumnSnapshot(
                    new ColumnId("col-" + c), "Column " + c, "",
                    BoardColor.DEFAULT, WipLimit.unlimited(), now, false, null, cards));
        }
        CardId first = new CardId("card-0-0");
        return new BoardMemento(columns, List.of(),
                List.of(new CardLink(first, new CardId("card-0-1"))),
                List.of(TimelineEntry.restore(first, EntryId.newId(),
                        now.minusSeconds(3600), now, "design session")));
    }
}
