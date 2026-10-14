package com.personalkanban.infrastructure.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rotation policy of the automatic backups (T17 · H-2): which copies are
 * pruned, decided purely by the timestamp in the name, so the same rule holds on
 * every filesystem and a renamed file cannot reshuffle the order.
 */
class BackupRotationTest {

    @TempDir
    Path dir;

    private static Path backup(String name, String stamp, Integer counter) {
        String suffix = counter == null ? "" : "-" + counter;
        return Path.of("backups", "kanban-" + stamp + suffix + ".db");
    }

    @Test
    void theOldestCopiesGoFirstWhenThereAreMoreThanTheLimit() {
        List<Path> existing = List.of(
                backup("a", "20261014-090000", null),
                backup("b", "20261014-120000", null),
                backup("c", "20261015-080000", null),
                backup("d", "20261014-235959", null));

        List<Path> doomed = BackupService.toDelete(existing, 2);

        assertThat(doomed).extracting(path -> path.getFileName().toString())
                .containsExactlyInAnyOrder("kanban-20261014-090000.db", "kanban-20261014-120000.db");
    }

    @Test
    void nothingIsDeletedWhileTheLimitIsNotReached() {
        List<Path> existing = List.of(
                backup("a", "20261014-090000", null),
                backup("b", "20261014-120000", null));

        assertThat(BackupService.toDelete(existing, BackupService.KEEP)).isEmpty();
        assertThat(BackupService.toDelete(List.of(), 3)).isEmpty();
        assertThat(BackupService.toDelete(existing, 0)).hasSize(2);
    }

    /** Same-second copies are ordered by their counter, not randomly. */
    @Test
    void copiesFromTheSameSecondKeepTheirOrder() {
        List<Path> existing = IntStream.rangeClosed(1, 4)
                .mapToObj(counter -> backup("x" + counter, "20261014-120000", counter == 1 ? null : counter))
                .toList();

        List<Path> doomed = BackupService.toDelete(existing, 2);

        assertThat(doomed).extracting(path -> path.getFileName().toString())
                .containsExactly("kanban-20261014-120000.db", "kanban-20261014-120000-2.db");
    }

    /** Files that are not ours are never touched. */
    @Test
    void foreignFilesAreIgnored() {
        List<Path> existing = List.of(
                backup("a", "20261014-090000", null),
                Path.of("backups", "kanban.db"),
                Path.of("backups", "notas.txt"),
                Path.of("backups", "kanban-2026.db"));

        List<Path> doomed = BackupService.toDelete(existing, 0);

        assertThat(doomed).containsExactly(backup("a", "20261014-090000", null));
    }

    @Test
    void theFolderOfADatabaseIsItsOwnBackupsFolder() {
        Path database = dir.resolve("kanban.db");
        BackupService service = new BackupService(database);

        assertThat(service.backupDirectory()).isEqualTo(dir.resolve("backups"));
        assertThat(service.list()).as("no copies yet").isEmpty();
        assertThat(service.backup()).as("nothing to copy yet").isEmpty();
        assertThat(service.newestHealthyBackup()).isEmpty();
    }
}
