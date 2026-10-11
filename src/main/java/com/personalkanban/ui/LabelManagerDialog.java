package com.personalkanban.ui;

import com.personalkanban.application.GlobalLabelsService;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

/**
 * Global label manager: the one place to add, rename and remove the labels of
 * the shared catalog. Labels are global, so renaming or removing one also
 * rewrites (or clears) that label on the cards of <em>every</em> board — the
 * confirmation dialogs spell that out before anything is touched.
 * <p>
 * Reserved system labels (urgent / important / archived) are listed with a
 * suffix and their rename/remove buttons stay disabled.
 */
final class LabelManagerDialog {

    private final I18n i18n;
    private final GlobalLabelsService labels;
    private final Dialogs dialogs;
    /** Called after any change that altered card labels (re-renders the board). */
    private final Runnable onChanged;

    private ListView<String> list;
    private Button rename;
    private Button remove;

    LabelManagerDialog(I18n i18n, GlobalLabelsService labels, Dialogs dialogs, Runnable onChanged) {
        this.i18n = i18n;
        this.labels = labels;
        this.dialogs = dialogs;
        this.onChanged = onChanged;
    }

    void show() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(i18n.text("labels.title"));
        dialog.setHeaderText(i18n.text("labels.header"));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        list = new ListView<>();
        list.setPrefHeight(280);
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(labels.isReserved(item)
                            ? item + i18n.text("labels.system.suffix")
                            : item);
                }
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener(
                (obs, was, selected) -> updateButtons());

        Button add = new Button(i18n.text("labels.add"));
        add.setOnAction(e -> onAdd());
        rename = new Button(i18n.text("labels.rename"));
        rename.setOnAction(e -> onRename());
        remove = new Button(i18n.text("labels.remove"));
        remove.setOnAction(e -> onRemove());
        updateButtons();

        Label hint = new Label(i18n.text("labels.hint"));
        hint.setWrapText(true);

        HBox buttons = new HBox(10, add, rename, remove);
        VBox content = new VBox(10, list, buttons, hint);
        VBox.setVgrow(list, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(420);

        reload();
        dialog.showAndWait();
    }

    /** Buttons act on the selection; reserved labels can only be listed. */
    private void updateButtons() {
        String selected = list.getSelectionModel().getSelectedItem();
        boolean editable = selected != null && !labels.isReserved(selected);
        rename.setDisable(!editable);
        remove.setDisable(!editable);
    }

    private void onAdd() {
        Optional<String> answer = dialogs.promptText(i18n.text("labels.add.prompt"), "");
        String name = answer.orElse("").strip();
        if (name.isEmpty()) {
            return;
        }
        try {
            labels.addLabel(name);
            reload();
        } catch (IllegalArgumentException rejected) {
            dialogs.error(i18n.text("labels.error", rejected.getMessage()));
        }
    }

    private void onRename() {
        String current = list.getSelectionModel().getSelectedItem();
        if (current == null) {
            dialogs.info(i18n.text("labels.select"));
            return;
        }
        Optional<String> answer = dialogs.promptText(
                i18n.text("labels.rename.prompt", current), current);
        String newName = answer.map(String::strip).orElse("");
        if (newName.isEmpty() || newName.equals(current)) {
            return;
        }
        if (!dialogs.confirm(i18n.text("labels.rename.confirm", current, newName))) {
            return;
        }
        try {
            labels.renameLabel(current, newName);
            reload();
            onChanged.run(); // the sweep rewrote cards of the active board too
        } catch (IllegalArgumentException rejected) {
            dialogs.error(i18n.text("labels.error", rejected.getMessage()));
        }
    }

    private void onRemove() {
        String current = list.getSelectionModel().getSelectedItem();
        if (current == null) {
            dialogs.info(i18n.text("labels.select"));
            return;
        }
        if (!dialogs.confirm(i18n.text("labels.remove.confirm", current))) {
            return;
        }
        try {
            labels.removeLabel(current);
            reload();
            onChanged.run();
        } catch (IllegalArgumentException rejected) {
            dialogs.error(i18n.text("labels.error", rejected.getMessage()));
        }
    }

    private void reload() {
        List<String> names = labels.names();
        list.getItems().setAll(names);
        updateButtons();
    }
}
