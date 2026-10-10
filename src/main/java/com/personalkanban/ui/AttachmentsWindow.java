package com.personalkanban.ui;

import com.personalkanban.Main;
import com.personalkanban.ui.theme.ThemeManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Non-modal window listing one card's attachments (Pure Fabrication over
 * {@link AttachmentStore}). Reference files live in
 * {@code <data dir>/attachments/<card id>/} — never in the database — so the
 * window also supports files dropped straight into that folder: "Open folder"
 * reveals it and the list refreshes whenever the window regains focus.
 *
 * <p>Double-clicking a row (or the Open button) hands the file to the
 * system's associated program.</p>
 */
final class AttachmentsWindow {

    private final String cardId;
    private final I18n i18n;
    private final Stage stage = new Stage();
    private final ListView<Path> files = new ListView<>();
    private final Label folderLabel = new Label();
    private final Label countLabel = new Label();

    private AttachmentsWindow(String cardId, String cardTitle, I18n i18n,
                              ThemeManager themeManager) {
        this.cardId = cardId;
        this.i18n = i18n;

        Label title = new Label(cardTitle);
        title.getStyleClass().add("detail-title");
        title.setWrapText(true);

        folderLabel.getStyleClass().add("detail-caption");
        folderLabel.setWrapText(true);

        files.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item.getFileName().toString());
                    setTooltip(new Tooltip(item.toAbsolutePath().toString()));
                }
            }
        });
        files.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                onOpenSelected();
            }
        });
        javafx.scene.layout.VBox.setVgrow(files, Priority.ALWAYS);

        Button add = toolButton("attachments.add", this::onAdd);
        Button open = toolButton("attachments.open", this::onOpenSelected);
        Button openFolder = toolButton("attachments.open.folder", this::onOpenFolder);
        Button delete = toolButton("attachments.delete", this::onDeleteSelected);
        Button refresh = toolButton("attachments.refresh", this::refresh);
        HBox actions = new HBox(8, add, open, openFolder, delete, refresh);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button close = new Button(i18n.text("dialog.cancel"));
        close.setOnAction(e -> stage.close());
        ButtonBar bar = new ButtonBar();
        bar.getButtons().add(close);
        bar.setPadding(new Insets(4, 0, 0, 0));

        BorderPane layout = new BorderPane();
        layout.setTop(new javafx.scene.layout.VBox(4, title, folderLabel));
        layout.setCenter(files);
        layout.setBottom(new javafx.scene.layout.VBox(6, actions, countLabel, bar));
        layout.setPadding(new Insets(12));
        layout.getStyleClass().add("detail-window");

        Scene scene = new Scene(layout, 680, 460);
        scene.getStylesheets().addAll(themeManager.stylesheets());
        stage.setScene(scene);
        stage.setTitle(i18n.text("attachments.title", cardTitle));

        // Files copied into the folder while the window is in the background
        // (Explorer, another app) show up as soon as it regains focus.
        stage.focusedProperty().addListener((observable, was, focused) -> {
            if (focused) {
                refresh();
            }
        });

        refresh();
    }

    /** Opens (or focuses) the attachments window of a card. */
    static void open(String cardId, String cardTitle, I18n i18n, ThemeManager themeManager) {
        AttachmentsWindow window = new AttachmentsWindow(cardId, cardTitle, i18n, themeManager);
        window.stage.show();
        window.stage.toFront();
    }

    private Button toolButton(String key, Runnable action) {
        Button button = new Button(i18n.text(key));
        button.getStyleClass().add("tool-button");
        button.setOnAction(e -> action.run());
        return button;
    }

    private Path directory() {
        return AttachmentStore.directory(Main.dataDirectory(), cardId);
    }

    /** Re-reads the folder; safe to call at any time. */
    private void refresh() {
        List<Path> stored = AttachmentStore.list(Main.dataDirectory(), cardId);
        Path previous = files.getSelectionModel().getSelectedItem();
        files.getItems().setAll(stored);
        if (previous != null && stored.contains(previous)) {
            files.getSelectionModel().select(previous);
        } else if (!stored.isEmpty()) {
            files.getSelectionModel().selectFirst();
        }
        folderLabel.setText(i18n.text("attachments.folder", directory().toAbsolutePath()));
        countLabel.setText(stored.isEmpty()
                ? i18n.text("attachments.none")
                : i18n.text("card.attachments.badge.tip", stored.size()));
        countLabel.getStyleClass().setAll("detail-caption");
    }

    private void onAdd() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.text("attachments.add"));
        List<java.io.File> chosen = chooser.showOpenMultipleDialog(stage);
        if (chosen == null) {
            return;
        }
        boolean failed = false;
        for (java.io.File file : chosen) {
            if (AttachmentStore.importFile(Main.dataDirectory(), cardId,
                    file.toPath()) == null) {
                failed = true;
            }
        }
        refresh();
        if (failed) {
            message(Alert.AlertType.WARNING, i18n.text("attachments.add.failed"));
        }
    }

    private void onOpenSelected() {
        Path selected = files.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        openWithSystem(selected);
    }

    private void onOpenFolder() {
        try {
            Files.createDirectories(directory());
        } catch (java.io.IOException e) {
            message(Alert.AlertType.WARNING, i18n.text("attachments.open.failed"));
            return;
        }
        openWithSystem(directory());
        refresh();
    }

    private void onDeleteSelected() {
        Path selected = files.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                i18n.text("attachments.delete.confirm", selected.getFileName().toString()),
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        confirm.initOwner(stage);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            AttachmentStore.delete(selected);
            refresh();
        }
    }

    /** Hands a file (or folder) to the OS's associated program. */
    private void openWithSystem(Path target) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                if (desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
                    desktop.open(target.toFile());
                    return;
                }
            }
        } catch (Exception e) {
            // fall through to the message below
        }
        message(Alert.AlertType.INFORMATION, i18n.text("attachments.open.failed"));
    }

    private void message(Alert.AlertType type, String text) {
        Alert alert = new Alert(type, text, ButtonType.OK);
        alert.setHeaderText(null);
        alert.initOwner(stage);
        alert.showAndWait();
    }
}
