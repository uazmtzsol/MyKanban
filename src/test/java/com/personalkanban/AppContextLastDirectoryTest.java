package com.personalkanban;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Last-used-folder memory for export/import: remembered per machine in the
 * launcher config, validated on every read, and lost (falling back to the
 * system default) when the folder disappears — e.g. an unplugged USB drive.
 */
class AppContextLastDirectoryTest {

    @TempDir
    Path tempDir;

    private Path configFile() {
        return tempDir.resolve("config.properties");
    }

    @Test
    void emptyWhenNeverRemembered() {
        try (AppContext context = AppContext.openAt(
                tempDir.resolve("a.db"), configFile())) {
            assertThat(context.lastTransferDirectory()).isEmpty();
        }
    }

    @Test
    void remembersAndReturnsLastDirectory() throws Exception {
        Path documents = tempDir.resolve("documents");
        Files.createDirectories(documents); // as if the user had saved there
        try (AppContext context = AppContext.openAt(
                tempDir.resolve("a.db"), configFile())) {
            context.rememberTransferDirectory(documents);
        }

        try (AppContext context = AppContext.openAt(
                tempDir.resolve("a.db"), configFile())) {
            assertThat(context.lastTransferDirectory())
                    .hasValue(documents.toAbsolutePath().normalize());
        }
    }

    @Test
    void laterUseOverwritesOlderDirectory() throws Exception {
        Files.createDirectories(tempDir.resolve("usb"));
        Files.createDirectories(tempDir.resolve("documents"));
        try (AppContext context = AppContext.openAt(
                tempDir.resolve("a.db"), configFile())) {
            context.rememberTransferDirectory(tempDir.resolve("usb"));
            context.rememberTransferDirectory(tempDir.resolve("documents"));
        }

        try (AppContext context = AppContext.openAt(
                tempDir.resolve("a.db"), configFile())) {
            assertThat(context.lastTransferDirectory())
                    .hasValue(tempDir.resolve("documents").toAbsolutePath().normalize());
        }
    }

    @Test
    void missingFolderDegradesToEmpty() throws Exception {
        Path usb = tempDir.resolve("usb");
        Files.createDirectories(usb);
        try (AppContext context = AppContext.openAt(
                tempDir.resolve("a.db"), configFile())) {
            context.rememberTransferDirectory(usb); // exported here once
        }
        Files.delete(usb); // ...then the drive went away
        // The config held the path, but the folder is gone now.
        try (AppContext context = AppContext.openAt(
                tempDir.resolve("a.db"), configFile())) {
            assertThat(context.lastTransferDirectory()).isEmpty();
        }
    }

    @Test
    void neverStoresNonexistentDirectory() {
        try (AppContext context = AppContext.openAt(
                tempDir.resolve("a.db"), configFile())) {
            context.rememberTransferDirectory(tempDir.resolve("does-not-exist"));
            assertThat(context.lastTransferDirectory()).isEmpty();
        }
    }

    @Test
    void configSurvivesWithDatabaseSwitchesAndBoardCatalogWrites() throws Exception {
        Path documents = tempDir.resolve("documents");
        Files.createDirectories(documents);
        try (AppContext context = AppContext.openAt(
                tempDir.resolve("a.db"), configFile())) {
            context.rememberTransferDirectory(documents);
        }
        // Switching databases rewrites the whole config file.
        try (AppContext context = AppContext.openAt(
                tempDir.resolve("b.db"), configFile())) {
            context.rememberTransferDirectory(documents);
        }
        try (AppContext context = AppContext.openAt(
                tempDir.resolve("a.db"), configFile())) {
            assertThat(context.lastTransferDirectory())
                    .hasValue(documents.toAbsolutePath().normalize());
        }
        assertThat(Files.isDirectory(tempDir.resolve("documents"))).isTrue();
    }

    @Test
    void nullPathIsIgnoredSafely() {
        try (AppContext context = AppContext.openAt(
                tempDir.resolve("a.db"), configFile())) {
            context.rememberTransferDirectory(null);
            assertThat(context.lastTransferDirectory()).isEmpty();
        }
    }
}
