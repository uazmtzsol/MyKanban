package com.personalkanban.ui;

import com.personalkanban.application.GlobalShortcuts;

import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.List;

/**
 * Non-modal shortcuts reference (session 4.6, user decision: fixed
 * shortcuts + a help window instead of user-configurable ones). Styled like
 * the markdown cheatsheet: one shared window per app, opening it again just
 * brings it to the front.
 */
final class ShortcutsHelpWindow {

    private static Stage openWindow;

    private ShortcutsHelpWindow() {
    }

    private record Row(String keys, String descriptionKey) {
    }

    private static Row shortcutSettingRow(GlobalShortcuts.ShortcutSetting setting, GlobalShortcuts shortcuts, I18n i18n) {
        String actionLabel = i18n.text(new GlobalShortcuts().storageLabelKey(GlobalShortcuts.Action.fromStorageNameOrThrow(setting.action())));
        String keys = setting.keyCombination().isBlank()
                ? i18n.text("shortcut.key.unset")
                : setting.keyCombination();
        return new Row(keys, actionLabel);
    }

    /** The full catalog; keep in sync with bindScene()/toolbar tooltips. */
    private static final List<Row> ROWS = List.of(
            new Row("Ctrl+Z", "help.undo"),
            new Row("Ctrl+Shift+Z", "help.redo"),
            new Row("Ctrl+1 / Ctrl+2 / Ctrl+3", "help.cardview"),
            new Row("Ctrl+N", "help.new.board"),
            new Row("Ctrl+Shift+P", "help.process.filter.all"),
            new Row("Ctrl+Shift+N", "help.process.filter.none"),
            new Row("Ctrl+D", "help.dark.mode"),
            new Row("Ctrl+S", "help.save"),
            new Row("Ctrl+Q", "help.exit"),
            new Row("F1", "help.show.shortcuts"));

    static void show(I18n i18n, java.util.List<String> stylesheets) {
        if (openWindow != null) {
            openWindow.toFront();
            openWindow.requestFocus();
            return;
        }
        Stage stage = new Stage();
        openWindow = stage;
        stage.setOnHidden(e -> openWindow = null);

        GridPane grid = new GridPane();
        grid.setHgap(24);
        grid.setVgap(10);
        grid.getStyleClass().add("shortcuts-grid");

        Label keysHeader = new Label(i18n.text("help.shortcuts.keys"));
        keysHeader.getStyleClass().add("detail-title");
        Label actionHeader = new Label(i18n.text("help.shortcuts.action"));
        actionHeader.getStyleClass().add("detail-title");
        grid.add(keysHeader, 0, 0);
        grid.add(actionHeader, 1, 0);

        int row = 1;
        for (Row entry : ROWS) {
            Label keys = new Label(entry.keys());
            keys.getStyleClass().add("shortcut-keys");
            keys.setFont(Font.font("Consolas", FontWeight.BOLD, 13));
            Label action = new Label(i18n.text(entry.descriptionKey()));
            action.getStyleClass().add("shortcut-action");
            action.setWrapText(true);
            grid.add(keys, 0, row);
            grid.add(action, 1, row);
            row++;
        }

        ScrollPane scroller = new ScrollPane(grid);
        scroller.setFitToWidth(true);
        scroller.setPadding(new javafx.geometry.Insets(12));

        BorderPane layout = new BorderPane(scroller);
        layout.getStyleClass().add("detail-window");

        javafx.scene.Scene scene = new javafx.scene.Scene(layout, 460, 420);
        scene.getStylesheets().addAll(stylesheets);
        stage.setScene(scene);
        stage.setTitle(i18n.text("help.shortcuts.title"));
        stage.show();
    }

    static void showGlobalShortcuts(GlobalShortcuts shortcuts, I18n i18n, java.util.List<String> stylesheets) {
        if (openWindow != null) {
            openWindow.toFront();
            openWindow.requestFocus();
            return;
        }
        Stage stage = new Stage();
        openWindow = stage;
        stage.setOnHidden(e -> openWindow = null);

        GridPane grid = new GridPane();
        grid.setHgap(24);
        grid.setVgap(10);
        grid.getStyleClass().add("shortcuts-grid");

        Label keysHeader = new Label(i18n.text("help.shortcuts.keys"));
        keysHeader.getStyleClass().add("detail-title");
        Label actionHeader = new Label(i18n.text("help.shortcuts.action"));
        actionHeader.getStyleClass().add("detail-title");
        grid.add(keysHeader, 0, 0);
        grid.add(actionHeader, 1, 0);

        int row = 1;
        for (GlobalShortcuts.ShortcutSetting setting : shortcuts.settings()) {
            Row entry = shortcutSettingRow(setting, shortcuts, i18n);
            Label keys = new Label(entry.keys());
            keys.getStyleClass().add("shortcut-keys");
            keys.setFont(Font.font("Consolas", FontWeight.BOLD, 13));
            Label action = new Label(i18n.text(entry.descriptionKey()));
            action.getStyleClass().add("shortcut-action");
            action.setWrapText(true);
            grid.add(keys, 0, row);
            grid.add(action, 1, row);
            row++;
        }

        ScrollPane scroller = new ScrollPane(grid);
        scroller.setFitToWidth(true);
        scroller.setPadding(new javafx.geometry.Insets(12));

        BorderPane layout = new BorderPane(scroller);
        layout.getStyleClass().add("detail-window");

        javafx.scene.Scene scene = new javafx.scene.Scene(layout, 460, 420);
        scene.getStylesheets().addAll(stylesheets);
        stage.setScene(scene);
        stage.setTitle(i18n.text("shortcut.settings.title"));
        stage.show();
    }
}
