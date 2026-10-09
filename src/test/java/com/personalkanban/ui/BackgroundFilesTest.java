package com.personalkanban.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Import/repair behaviour of the background image copy in the data folder. */
class BackgroundFilesTest {

    @TempDir
    Path dataDir;

    @TempDir
    Path elsewhere;

    private Path writeImage(Path dir, String name) throws Exception {
        Path file = dir.resolve(name);
        Files.write(file, new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        return file;
    }

    @Test
    void importCopiesTheImageIntoTheProgramDataDirectory() throws Exception {
        Path chosen = writeImage(elsewhere, "fondo.png");
        String spec = chosen + "|0.45";

        String imported = BackgroundFiles.importSpec(spec, dataDir);

        Path expected = BackgroundFiles.directory(dataDir).resolve("fondo.png");
        assertThat(expected).exists();
        assertThat(imported).isEqualTo(expected + "|0.45");
        assertThat(imported).contains(dataDir.toString());
    }

    @Test
    void importKeepsSpecWhenAlreadyInsideTheDataDirectory() throws Exception {
        Path first = writeImage(elsewhere, "fondo.png");
        String once = BackgroundFiles.importSpec(first + "|0.30", dataDir);

        String twice = BackgroundFiles.importSpec(once, dataDir);

        assertThat(twice).isEqualTo(once);
    }

    @Test
    void importKeepsSpecWhenTheFileIsMissing() {
        String spec = elsewhere.resolve("no-existe.png") + "|0.45";

        assertThat(BackgroundFiles.importSpec(spec, dataDir)).isEqualTo(spec);
    }

    @Test
    void importKeepsNullAndBlankSpecs() {
        assertThat(BackgroundFiles.importSpec(null, dataDir)).isNull();
        assertThat(BackgroundFiles.importSpec("  ", dataDir)).isEqualTo("  ");
    }

    @Test
    void repairImportsAnExistingOriginalFromOutside() throws Exception {
        Path chosen = writeImage(elsewhere, "fondo.png");
        String spec = chosen + "|0.45";

        String repaired = BackgroundFiles.repairSpec(spec, dataDir);

        Path expected = BackgroundFiles.directory(dataDir).resolve("fondo.png");
        assertThat(repaired).isEqualTo(expected + "|0.45");
        assertThat(expected).exists();
    }

    @Test
    void repairFallsBackToLocalCopyWhenOriginalDisappeared() throws Exception {
        Path chosen = writeImage(elsewhere, "fondo.png");
        String once = BackgroundFiles.importSpec(chosen + "|0.45", dataDir);
        Files.delete(chosen); // Downloads cleanup: the original is gone

        String repaired = BackgroundFiles.repairSpec(once, dataDir);

        assertThat(repaired).isEqualTo(once); // the local copy is the target
        // Now the danger case: the stored spec still points at the copy,
        // while a *stale* spec pointing at the deleted original is repaired:
        String stale = chosen + "|0.45";
        String resolved = BackgroundFiles.repairSpec(stale, dataDir);
        assertThat(resolved).isEqualTo(
                BackgroundFiles.directory(dataDir).resolve("fondo.png") + "|0.45");
    }

    @Test
    void repairLeavesSpecUntouchedWhenNothingExists() {
        String spec = elsewhere.resolve("perdido.png") + "|0.45";

        assertThat(BackgroundFiles.repairSpec(spec, dataDir)).isEqualTo(spec);
        assertThat(BackgroundFiles.repairSpec(null, dataDir)).isNull();
    }

    @Test
    void repairKeepsDimSuffixOnFallback() throws Exception {
        Path chosen = writeImage(elsewhere, "fondo.png");
        BackgroundFiles.importSpec(chosen + "|0.60", dataDir);
        Files.delete(chosen);

        String repaired = BackgroundFiles.repairSpec(chosen + "|0.60", dataDir);

        assertThat(repaired).endsWith("|0.60");
    }
}
