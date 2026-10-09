package com.personalkanban.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Per-card attachment folder behaviour (no database involved). */
class AttachmentStoreTest {

    @TempDir
    Path dataDir;

    @TempDir
    Path sourceDir;

    private Path source(String name, String content) throws Exception {
        Path file = sourceDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    @Test
    void cardFoldersLiveUnderTheAttachmentsDirectory() {
        Path folder = AttachmentStore.directory(dataDir, "card-42");
        assertThat(folder).isEqualTo(
                dataDir.resolve("attachments").resolve("card-42"));
    }

    @Test
    void cardIdIsSanitizedForTheFileSystem() {
        Path folder = AttachmentStore.directory(dataDir, "a/b:c*d");
        assertThat(folder.getFileName()).isEqualTo(Path.of("a_b_c_d"));
    }

    @Test
    void importCopiesTheFileIntoTheCardFolder() throws Exception {
        Path chosen = source("informe.pdf", "pdf-bytes");

        Path stored = AttachmentStore.importFile(dataDir, "card-1", chosen);

        assertThat(stored).isNotNull();
        assertThat(stored.getParent()).isEqualTo(
                AttachmentStore.directory(dataDir, "card-1"));
        assertThat(Files.readString(stored)).isEqualTo("pdf-bytes");
        // The original stays untouched.
        assertThat(chosen).exists();
    }

    @Test
    void importNeverOverwritesAnExistingAttachment() throws Exception {
        Path first = source("notas.txt", "primero");
        AttachmentStore.importFile(dataDir, "card-1", first);
        Path second = sourceDir.resolve("otro").resolve("notas.txt");
        Files.createDirectories(second.getParent());
        Files.writeString(second, "segundo");

        Path stored = AttachmentStore.importFile(dataDir, "card-1", second);

        assertThat(stored.getFileName().toString()).isEqualTo("notas (2).txt");
        assertThat(AttachmentStore.list(dataDir, "card-1")).hasSize(2);
        assertThat(Files.readString(
                AttachmentStore.directory(dataDir, "card-1").resolve("notas.txt")))
                .isEqualTo("primero");
    }

    @Test
    void importReturnsNullForAMissingSource() {
        assertThat(AttachmentStore.importFile(dataDir, "card-1",
                sourceDir.resolve("no-existe.bin"))).isNull();
        assertThat(AttachmentStore.importFile(dataDir, "card-1", null)).isNull();
    }

    @Test
    void listIsSortedAndIgnoresNestedDirectories() throws Exception {
        AttachmentStore.importFile(dataDir, "card-9", source("zeta.txt", "z"));
        AttachmentStore.importFile(dataDir, "card-9", source("alfa.txt", "a"));
        Files.createDirectory(AttachmentStore.directory(dataDir, "card-9").resolve("subdir"));

        List<Path> files = AttachmentStore.list(dataDir, "card-9");

        assertThat(files).extracting(path -> path.getFileName().toString())
                .containsExactly("alfa.txt", "zeta.txt");
        assertThat(AttachmentStore.count(dataDir, "card-9")).isEqualTo(2);
    }

    @Test
    void listIsEmptyForACardWithoutFolder() {
        assertThat(AttachmentStore.list(dataDir, "sin-adjuntos")).isEmpty();
        assertThat(AttachmentStore.count(dataDir, "sin-adjuntos")).isZero();
        assertThat(AttachmentStore.directory(dataDir, "sin-adjuntos")).doesNotExist();
    }

    @Test
    void filesDroppedDirectlyIntoTheFolderAreListed() throws Exception {
        // The user may open the folder in Explorer and drop files there.
        Path folder = AttachmentStore.directory(dataDir, "card-7");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("manual.pdf"), "contenido");

        assertThat(AttachmentStore.list(dataDir, "card-7"))
                .extracting(path -> path.getFileName().toString())
                .containsExactly("manual.pdf");
    }

    @Test
    void deleteRemovesOnlyTheGivenAttachment() throws Exception {
        Path kept = AttachmentStore.importFile(dataDir, "card-3", source("a.txt", "a"));
        Path removed = AttachmentStore.importFile(dataDir, "card-3", source("b.txt", "b"));

        assertThat(AttachmentStore.delete(removed)).isTrue();

        assertThat(AttachmentStore.list(dataDir, "card-3")).containsExactly(kept);
        assertThat(AttachmentStore.delete(null)).isFalse();
    }
}
