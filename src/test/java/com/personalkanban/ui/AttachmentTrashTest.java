package com.personalkanban.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Session 10 (board cleanup): a deleted card takes its attachment files to
 * the trash — never straight to `rm`, because the row itself is undoable and
 * the files live outside the database — and the cleanup tool is what finally
 * removes folders whose card no longer exists anywhere.
 */
class AttachmentTrashTest {

    @TempDir
    Path dataDir;

    private Path givenCardWithFile(String cardId, String fileName) throws IOException {
        Path folder = AttachmentStore.directory(dataDir, cardId);
        Files.createDirectories(folder);
        Files.writeString(folder.resolve(fileName), "content of " + fileName);
        return folder;
    }

    @Test
    void deletingACardMovesItsFilesToTheTrash() throws IOException {
        Path live = givenCardWithFile("c1", "invoice.pdf");

        assertThat(AttachmentStore.moveToTrash(dataDir, "c1")).isTrue();

        assertThat(Files.exists(live)).isFalse();
        assertThat(AttachmentStore.list(dataDir, "c1")).isEmpty();
        assertThat(Files.isRegularFile(
                AttachmentStore.trashDirectory(dataDir, "c1").resolve("invoice.pdf"))).isTrue();
        assertThat(AttachmentStore.trashedCardIds(dataDir)).containsExactly("c1");
    }

    @Test
    void aCardWithoutFilesLeavesNothingBehind() {
        assertThat(AttachmentStore.moveToTrash(dataDir, "ghost")).isFalse();
        assertThat(AttachmentStore.trashedCardIds(dataDir)).isEmpty();
    }

    @Test
    void undoRestoresTheFilesWhenTheCardComesBack() throws IOException {
        givenCardWithFile("c2", "photo.png");
        AttachmentStore.moveToTrash(dataDir, "c2");

        assertThat(AttachmentStore.restoreFromTrash(dataDir, "c2")).isTrue();

        assertThat(AttachmentStore.list(dataDir, "c2")).hasSize(1);
        assertThat(AttachmentStore.trashedCardIds(dataDir)).isEmpty();
    }

    @Test
    void restoringNeverOverwritesFilesThatAlreadyExist() throws IOException {
        Path live = givenCardWithFile("c3", "kept.txt");
        givenCardWithFile("c3", "trashed.txt");
        AttachmentStore.moveToTrash(dataDir, "c3");
        Files.createDirectories(live);
        Files.writeString(live.resolve("kept.txt"), "still here");

        assertThat(AttachmentStore.restoreFromTrash(dataDir, "c3")).isFalse();
        assertThat(AttachmentStore.list(dataDir, "c3")).hasSize(1);
        assertThat(AttachmentStore.trashedCardIds(dataDir)).containsExactly("c3");
    }

    @Test
    void storedIdsCoverLiveAndTrashedFolders() throws IOException {
        givenCardWithFile("live1", "a.txt");
        givenCardWithFile("gone1", "b.txt");
        AttachmentStore.moveToTrash(dataDir, "gone1");

        List<String> ids = AttachmentStore.storedCardIds(dataDir);

        assertThat(ids).containsExactlyInAnyOrder("live1", "gone1");
    }

    @Test
    void cleanupDeletesBothTheLiveAndTheTrashedFolder() throws IOException {
        givenCardWithFile("dead", "a.txt");
        AttachmentStore.moveToTrash(dataDir, "dead");
        givenCardWithFile("dead2", "b.txt");

        assertThat(AttachmentStore.deleteCardFolder(dataDir, "dead")).isTrue();
        assertThat(AttachmentStore.deleteCardFolder(dataDir, "dead2")).isTrue();
        assertThat(AttachmentStore.deleteCardFolder(dataDir, "never-existed")).isFalse();

        assertThat(AttachmentStore.storedCardIds(dataDir)).isEmpty();
    }
}
