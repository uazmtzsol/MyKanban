package com.personalkanban.ui;

import com.personalkanban.application.BoardService;
import com.personalkanban.application.GlobalShortcuts;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Editable global keyboard shortcuts dialog.
 *
 * <p>Opens on top. The user edits bindings and accepts or cancels. On accept,
 * the dialog persists the updated {@link GlobalShortcuts} via
 * {@link BoardService#saveGlobalShortcuts(GlobalShortcuts)}.</p>
 *
 * <p>Each row carries the {@link GlobalShortcuts.Action} itself, never a string
 * to be parsed back: earlier this dialog round-tripped the row through
 * {@code Action.fromStorageNameOrThrow} with names that were actually i18n
 * keys, so opening it threw {@link IllegalArgumentException} (bug S11-6).</p>
 */
final class KeyboardShortcutsDialog {

    /** One editable row: the action and the key combination currently bound. */
    record RowItem(GlobalShortcuts.Action action, String currentKey) {
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

    /** The rows to show: one per configurable action, in enum order. */
    static List<RowItem> rowsOf(GlobalShortcuts shortcuts) {
        List<RowItem> rows = new ArrayList<>();
        for (GlobalShortcuts.Action action : GlobalShortcuts.Action.values()) {
            rows.add(new RowItem(action, shortcuts.get(action)));
        }
        return rows;
    }

    /** Restores every action to its default binding (the «Restaurar» button). */
    static void applyDefaults(GlobalShortcuts shortcuts) {
        GlobalShortcuts defaults = new GlobalShortcuts();
        for (GlobalShortcuts.Action action : GlobalShortcuts.Action.values()) {
            shortcuts.set(action, defaults.get(action));
        }
    }

    /** Visible text of a row: localized action name — current binding. */
    static String rowLabel(I18n i18n, GlobalShortcuts shortcuts, RowItem row) {
        String actionLabel = i18n.text(shortcuts.storageLabelKey(row.action()));
        String keyLabel = row.currentKey() == null || row.currentKey().isBlank()
                ? i18n.text("shortcut.key.unset")
                : row.currentKey();
        return actionLabel + " — " + keyLabel;
    }

    Optional<GlobalShortcuts> show() {
        items.setAll(rowsOf(shortcuts));

        ListView<RowItem> list = new ListView<>(items);
        list.setCellFactory(param -> new ComboListCell(i18n, shortcuts));
        list.setPrefHeight(180);

        Button reset = new Button(i18n.text("shortcut.reset"));
        reset.setOnAction(e -> {
            applyDefaults(shortcuts);
            items.setAll(rowsOf(shortcuts));
            list.refresh();
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
            shortcuts.set(selected.action(), proposed);
            int idx = items.indexOf(selected);
            items.set(idx, new RowItem(selected.action(), shortcuts.get(selected.action())));
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

    private String readProposedKey(I18n i18n) {
        TextCaptureDialog capture = new TextCaptureDialog(i18n,
                i18n.text("shortcut.capture.title"),
                i18n.text("shortcut.capture.prompt"));
        return capture.showAndWait().orElse("");
    }

    /** ListView row that renders each shortcut action and its binding. */
    private static final class ComboListCell extends ListCell<RowItem> {
        private final I18n i18n;
        private final GlobalShortcuts shortcuts;

        ComboListCell(I18n i18n, GlobalShortcuts shortcuts) {
            this.i18n = i18n;
            this.shortcuts = shortcuts;
        }

        @Override
        public void updateItem(RowItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(rowLabel(i18n, shortcuts, item));
            }
        }
    }
}
