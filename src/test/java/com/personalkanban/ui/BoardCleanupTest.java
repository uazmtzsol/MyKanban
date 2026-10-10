package com.personalkanban.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Session 10 (board cleanup) wiring guards.
 *
 * <p>Archiving and file deletion are spread over the controller's deletion
 * intents, so — like the other source-level guards in this package — the
 * source itself is checked: every path that removes cards must send the
 * attachment files to the trash first, the trash must be restored after an
 * undo, and the archive toggle must sit in the single filter method that
 * both the renderer and the keyboard navigator go through.</p>
 */
class BoardCleanupTest {

    private static Path uiRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 4 && candidate != null; depth++) {
            Path probe = candidate.resolve("src/main/java/com/personalkanban/ui");
            if (Files.isDirectory(probe)) {
                return probe;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate the ui sources");
    }

    private static String source(String fileName) throws IOException {
        return Files.readString(uiRoot().resolve(fileName), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ archive

    @Test
    void archivedCardsAreHiddenInTheOneFilterEveryViewGoesThrough() throws IOException {
        String controller = source("BoardController.java");

        assertThat(controller)
                .as("the archive gate must live where rendering and navigation meet")
                .contains("if (!showArchived && card.hasLabelIgnoreCase(")
                .contains("Card.LABEL_ARCHIVED");
        assertThat(controller)
                .as("the toggle must exist and repaint on change")
                .contains("showArchived = archivedFilter.isSelected();");

        assertThat(source("ColumnViewBuilder.java"))
                .as("the renderer decides visibility through that filter")
                .contains("board.matchesProcessFilter(card)");
    }

    @Test
    void theCardMenuOffersArchivingAndExporting() throws IOException {
        String cardView = source("CardViewBuilder.java");

        assertThat(cardView).contains("i18n.text(\"card.archive.menu\")");
        assertThat(cardView).contains("Card.LABEL_ARCHIVED");
        assertThat(cardView).contains("i18n.text(\"card.export.menu\")");
    }

    // ------------------------------------------------------------------ deletion

    @Test
    void everyDeletionPathSendsTheAttachmentFilesToTheTrash() throws IOException {
        String controller = source("BoardController.java");

        assertThat(controller).contains("private void trashAttachments(CardId cardId)");
        assertThat(controller)
                .as("single-card delete")
                .contains("trashAttachments(cardId);");
        assertThat(controller)
                .as("bulk delete")
                .contains("ids.forEach(this::trashAttachments)");
        assertThat(controller)
                .as("clear column and delete column")
                .contains("cards().forEach(card -> trashAttachments(card.id()))");
        assertThat(controller)
                .as("delete board")
                .contains("trashAttachments(card.id()))");
    }

    @Test
    void undoCanBringTheFilesBackAndCleanupCanRemoveThemForGood() throws IOException {
        String controller = source("BoardController.java");

        assertThat(controller)
                .as("refresh() restores the trash of any card that exists again")
                .contains("restoreTrashedAttachments();")
                .contains("private void restoreTrashedAttachments()");
        assertThat(controller)
                .as("the cleanup entry only deletes folders of MISSING cards")
                .contains("filter(id -> !cardExistsAnywhere(id))")
                .contains("i18n.text(\"cleanup.attachments.confirm\"")
                .contains("itemOf(\"cleanup.attachments\"");
    }

    @Test
    void theTrashDirectoryIsDocumentedAsUndoProtection() throws IOException {
        String store = Files.readString(
                uiRoot().resolve("AttachmentStore.java"), StandardCharsets.UTF_8);

        assertThat(store).contains("TRASH_DIRECTORY_NAME");
        assertThat(store).contains("static boolean moveToTrash(");
        assertThat(store).contains("static boolean restoreFromTrash(");
        assertThat(store).contains("static boolean deleteCardFolder(");
    }
}
