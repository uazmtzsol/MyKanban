package com.personalkanban.ui;

import com.personalkanban.application.CurrentConflicts;
import com.personalkanban.application.CurrentConflicts.ConflictCopyReview;
import com.personalkanban.application.CurrentConflicts.ConflictEntry;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Review of conflict copies stored for the current board: a list of existing
 * copies on the left and, on selection, the field-level manifest of one copy
 * on the right. No business logic: it only reads the helper and renders the
 * conflict copy the sync layer already wrote.
 */
public final class ConflictReviewDialog {

    private final Window owner;
    private final I18n i18n;
    private final List<ConflictEntry> entries;
    /** Reader for the detail pane; bound to the database's conflicts dir. */
    private final CurrentConflicts conflicts;

    public ConflictReviewDialog(Window owner, I18n i18n, java.nio.file.Path dataDirectory,
                                List<ConflictEntry> entries) {
        this.owner = owner;
        this.i18n = i18n;
        this.entries = entries == null ? List.of() : new ArrayList<>(entries);
        this.conflicts = new CurrentConflicts(dataDirectory);
    }

    public Optional<Window> showAndWait() {
        Dialog<Window> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(i18n.text("view.conflicts.title"));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        dialog.setResultConverter(button -> {
            if (button == ButtonType.CLOSE) {
                return owner;
            }
            return null;
        });

        ScrollPane left = new ScrollPane();
        left.setFitToWidth(true);
        left.setPadding(new Insets(12));
        ListView<ConflictEntry> list = new ListView<>();
        list.setCellFactory(param -> new ConflictEntryCell(i18n));
        list.getItems().setAll(entries);
        left.setContent(list);

        ScrollPane right = new ScrollPane();
        right.setFitToWidth(true);
        right.setPadding(new Insets(12));
        VBox detail = new VBox(8);
        detail.setPadding(new Insets(12));
        Label empty = new Label(i18n.text("view.conflict.detail.empty"));
        empty.setWrapText(true);
        detail.getChildren().add(empty);
        right.setContent(detail);

        HBox layout = new HBox(12);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        layout.getChildren().addAll(left, right);

        dialog.getDialogPane().setContent(layout);

        list.getSelectionModel().selectedItemProperty().addListener(
                (obs, was, now) -> updateDetail(detail, now));

        if (!entries.isEmpty()) {
            list.getSelectionModel().select(0);
        }

        return dialog.showAndWait();
    }

    private void updateDetail(VBox detail, ConflictEntry entry) {
        detail.getChildren().clear();
        if (entry == null) {
            detail.getChildren().add(new Label(i18n.text("view.conflict.detail.empty")));
            return;
        }

        ConflictCopyReview review;
        try {
            review = conflicts.review(entry.filePath());
        } catch (RuntimeException e) {
            review = null;
        }
        if (review == null) {
            detail.getChildren().add(new Label(i18n.text("view.conflict.detail.error")));
            return;
        }

        Label title = new Label(formatTitle(review));
        title.getStyleClass().add("detail-title");

        Label meta = new Label(formatMeta(review));
        meta.setWrapText(true);
        meta.getStyleClass().add("detail-caption");

        VBox fields = new VBox(6);
        fields.setPadding(new Insets(4));
        fields.getStyleClass().add("conflict-fields");
        for (CurrentConflicts.ConflictField field : review.fields()) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            Label label = new Label(field.field());
            label.setMinWidth(120);
            label.getStyleClass().add("conflict-field-name");
            Label local = new Label(field.localValue());
            local.setWrapText(true);
            local.getStyleClass().add("conflict-value");
            local.setTooltip(new Tooltip(i18n.text("view.conflict.local.value")));
            Label arrow = new Label("→");
            arrow.getStyleClass().add("conflict-arrow");
            Label remote = new Label(field.remoteValue());
            remote.setWrapText(true);
            remote.getStyleClass().add("conflict-value");
            remote.setTooltip(new Tooltip(i18n.text("view.conflict.remote.value")));
            row.getChildren().addAll(label, local, arrow, remote);
            fields.getChildren().add(row);
        }

        detail.getChildren().addAll(title, meta, fields);
    }

    private static String formatTitle(CurrentConflicts.ConflictCopyReview review) {
        return review.boardName() + " · " + formatDate(review.savedAt());
    }

    private static String formatMeta(CurrentConflicts.ConflictCopyReview review) {
        return review.boardId() + " · "
                + review.conflictCount() + " "
                + (review.conflictCount() == 1
                        ? "conflict" : "conflicts")
                + " · " + review.filePath().getFileName().toString();
    }

    private static String formatDate(long epochMilli) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(epochMilli));
    }

    private static class ConflictEntryCell extends javafx.scene.control.ListCell<ConflictEntry> {
        private final I18n i18n;

        ConflictEntryCell(I18n i18n) {
            this.i18n = i18n;
        }

        @Override
        protected void updateItem(ConflictEntry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item.boardName() + " · " + formatDate(item.savedAt()));
                setTooltip(new Tooltip(item.filePath().getFileName().toString()));
            }
        }

        private static String formatDate(long epochMilli) {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(epochMilli));
        }
    }

}
