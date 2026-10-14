package com.personalkanban.infrastructure.sqlite;

import com.personalkanban.AppContext;
import com.personalkanban.application.CorruptDatabaseException;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The integrity check and the guided recovery (T17 · H-3): a broken database is
 * never opened in silence, the app points at the newest healthy copy, and the
 * repair never deletes or overwrites the damaged file.
 */
class IntegrityCheckTest {

    @TempDir
    Path dir;

    private Path database() {
        return dir.resolve("kanban.db");
    }

    private Path config() {
        return dir.resolve("config.properties");
    }

    /** A database with one board, closed again (its backup folder is populated). */
    private void createDatabaseWithBoard() {
        AppContext context = AppContext.openAt(database(), config());
        context.boardService().addColumn("Trabajo", "", BoardColor.BLUE, WipLimit.unlimited());
        context.close();
    }

    @Test
    void aHealthyDatabasePassesAndAMissingOneIsNotCalledCorrupt() {
        createDatabaseWithBoard();

        assertThat(BackupService.integrity(database())).isEqualTo(BackupService.Integrity.OK);
        assertThat(BackupService.integrity(dir.resolve("no-existe.db")))
                .as("a file that is not there is unreadable, not corrupt")
                .isEqualTo(BackupService.Integrity.UNREADABLE);
        assertThat(BackupService.integrity(dir))
                .as("a directory is not a database file either")
                .isEqualTo(BackupService.Integrity.UNREADABLE);
    }

    @Test
    void openingABrandNewPathStillCreatesTheDatabase() {
        AppContext context = AppContext.openAt(database(), config());

        assertThat(context.boardService().board()).isNotNull();
        context.close();
        assertThat(database()).exists();
    }

    @Test
    void aFileThatIsNotADatabaseIsReportedAsCorrupt() throws Exception {
        createDatabaseWithBoard();
        Files.writeString(database(), "esto no es una base de datos",
                StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);

        assertThat(BackupService.integrity(database())).isEqualTo(BackupService.Integrity.CORRUPT);

        assertThatThrownBy(() -> AppContext.openAt(database(), config()))
                .isInstanceOf(CorruptDatabaseException.class)
                .hasMessageContaining("integrity check");
    }

    /** A damaged header (the realistic case) is caught too. */
    @Test
    void aDatabaseWithADamagedHeaderIsReportedAsCorrupt() throws Exception {
        createDatabaseWithBoard();
        byte[] bytes = Files.readAllBytes(database());
        for (int i = 0; i < 24; i++) {
            bytes[i] = (byte) 0xFF;
        }
        Files.write(database(), bytes);

        assertThat(BackupService.integrity(database())).isEqualTo(BackupService.Integrity.CORRUPT);
    }

    /**
     * The guided repair: the app points at the newest healthy copy, and opening
     * that copy (restored as a new file) gives the user a working board again —
     * with the damaged file and the copy both still on disk.
     */
    @Test
    void theNewestHealthyCopyIsOfferedAndRestoringItWorks() throws Exception {
        createDatabaseWithBoard();
        // Second launch: that leaves a copy of the state created above.
        AppContext second = AppContext.openAt(database(), config());
        second.boardService().addColumn("Extra", "", BoardColor.GREEN, WipLimit.unlimited());
        second.close();

        Files.writeString(database(), "cabecera rota", StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING);

        CorruptDatabaseException failure = catchCorrupt();
        Path backup = failure.backup().orElseThrow();
        assertThat(backup).exists();
        assertThat(BackupService.integrity(backup)).isEqualTo(BackupService.Integrity.OK);

        Path restored = AppContext.restoreBackup(backup, failure.databaseFile());

        assertThat(restored.getFileName().toString()).startsWith("kanban-restored-");
        assertThat(Files.readString(database(), StandardCharsets.UTF_8))
                .as("the damaged file is left exactly as it was").isEqualTo("cabecera rota");
        assertThat(backup).as("and the copy it was restored from is still there").exists();

        AppContext recovered = AppContext.openAt(restored, config());
        assertThat(recovered.boardService().board().columns()).as("the restored board is usable").isNotEmpty();
        recovered.close();
    }

    @Test
    void aDamagedFileWithoutCopiesSaysSoInsteadOfInventingOne() throws Exception {
        Files.writeString(database(), "roto desde el principio", StandardCharsets.UTF_8);

        CorruptDatabaseException failure = catchCorrupt();

        assertThat(failure.backup()).isEmpty();
        assertThat(failure.getMessage()).contains("no healthy backup");
        assertThat(new BackupService(database()).list())
                .as("a file that never passed the check is never copied over the good ones").isEmpty();
    }

    private CorruptDatabaseException catchCorrupt() {
        try {
            AppContext.openAt(database(), config());
            throw new AssertionError("expected the damaged database to be refused");
        } catch (CorruptDatabaseException expected) {
            return expected;
        }
    }
}
