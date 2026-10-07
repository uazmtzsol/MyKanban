package com.personalkanban.ui;

import com.personalkanban.application.BoardService;
import com.personalkanban.application.GlobalShortcuts;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/***
 * Editable global keyboard shortcuts dialog.
 *
 * <p>Opens on top. The user edits bindings and accepts or cancels. On accept,
 * the dialog persists the updated {@link GlobalShortcuts} via
 * {@link BoardService#saveGlobalShortcuts(GlobalShortcuts)}.
 */
final class KeyboardShortcutsDialog {

    private record RowItem(GlobalShortcuts.Action action,
                           String currentKey,
                           String storageKey,
                           String displayKey) {
    }

    private final I18n i18n;
    private final BoardService service;
    private final GlobalShortcuts shortcuts;

    private final ObservableList<RowItem> items = FXCollections.observableArrayList();

    KeyboardShortcutsDialog(I18n i18n, BoardService service, GlobalShortcuts shortcuts) {
        this.i18n = i18n;
        this.service = service;
        this.shortcuts = shortcuts;
    }

    Optional<GlobalShortcuts> show() {        GlobalShortcuts locator = new GlobalShortcuts();
        for (GlobalShortcuts.Action action : GlobalShortcuts.Action.values()) {
            String current = shortcuts.get(action);
            items.add(new RowItem(action, current,
                    GlobalShortcuts.Action.isKnownActionName(action.name()) ? locator.storageLabelKey(action)
                            : action.name(),
                    buildRowLabel(action, current)));
        }

        ListView<RowItem> list = new ListView<>(items);
        list.setCellFactory(param -> new ComboListCell(i18n, locator));
        list.setPrefHeight(180);

        Button reset = new Button(i18n.text("shortcut.reset"));
        reset.setOnAction(e -> {
            GlobalShortcuts defaults = new GlobalShortcuts();
            for (GlobalShortcuts.ShortcutSetting setting : defaults.settings()) {
                if (GlobalShortcuts.Action.isKnownActionName(setting.action())) {
                    shortcuts.set(GlobalShortcuts.Action.fromStorageNameOrThrow(setting.action()),
                            setting.keyCombination());
                }
            }
            for (int i = 0; i < items.size(); i++) {
                GlobalShortcuts.Action action = items.get(i).action();
                String key = shortcuts.get(action);
                items.set(i, new RowItem(action, key,
                        items.get(i).storageKey,
                        buildRowLabel(action, key)));
            }
        });

        Button chooseKey = new Button(i18n.text("shortcut.choose"));
        chooseKey.setOnAction(e -> {
            RowItem selected = list.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return;
            }
            String proposed = readProposedKey(i18n);
            if (proposed.isBlank()) {
                return;
            }
            if (GlobalShortcuts.Action.isKnownActionName(selected.action())) {
                shortcuts.set(GlobalShortcuts.Action.fromStorageNameOrThrow(selected.action()), proposed);
            }
            int idx = items.indexOf(selected);
            items.set(idx, new RowItem(selected.action(), proposed,
                    selected.storageKey, buildRowLabel(selected.action(), proposed)));
            list.refresh();
        });

        VBox content = new VBox(12, list, new HBox(8, chooseKey, reset));
        content.getStyleClass().add("shortcuts-list");

        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle(i18n.text("shortcut.settings.title"));
        dialog.setHeaderText(i18n.text("shortcut.settings.header"));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(content);
        Dialogs.makeResizable(dialog, 440, 360);

        dialog.setResultConverter(button -> button == ButtonType.OK);

        if (dialog.showAndWait().orElse(false)) {
            service.saveGlobalShortcuts(shortcuts);
            return Optional.of(shortcuts);
        }
        return Optional.empty();
    }

    private String buildRowLabel(GlobalShortcuts.Action action, String current) {
        GlobalShortcuts locator = new GlobalShortcuts();
        String actionLabel = i18n.text(locator.storageLabelKey(action));
        String keyLabel = current.isBlank()
                ? i18n.text("shortcut.key.unset")
                : current;
        return actionLabel + " — " + keyLabel;
    }

    private String readProposedKey(I18n i18n) {
        TextCaptureDialog capture = new TextCaptureDialog(i18n,
                i18n.text("shortcut.capture.title"),
                i18n.text("shortcut.capture.prompt"));
        return capture.showAndWait().orElse("");
    }

    /** ListView row that renders each shortcut action and its binding. */
    private static final class ComboListCell extends javafx.scene.control.ListCell<RowItem> {
        private final I18n i18n;
        private final GlobalShortcuts locator;

        ComboListCell(I18n i18n, GlobalShortcuts locator) {
            this.i18n = i18n;
            this.locator = locator;
        }

        @Override
        public void updateItem(RowItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                String actionLabel = i18n.text(locator.storageLabelKey(item.action()));
                String keyLabel = item.currentKey.isBlank()
                        ? i18n.text("shortcut.key.unset")
                        : item.currentKey;
                setText(actionLabel + " — " + keyLabel);
            }
        }
    }
}
