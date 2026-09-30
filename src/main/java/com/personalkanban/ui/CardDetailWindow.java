package com.personalkanban.ui;

import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ChecklistItem;
import com.personalkanban.ui.markdown.Markdown;
import com.personalkanban.ui.theme.ThemeManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/**
 * Non-modal card detail window (Pure Fabrication): three tabs — the markdown
 * editor with live preview, the plain-text notes (session 4.4), and the flat
 * checklist (session 4.5, items convertible into cards via their context
 * menu). The preview WebView runs with JavaScript disabled, so note content
 * can never execute scripts.
 */
final class CardDetailWindow {

    private static final double SPLIT_RATIO = 0.5;

    private final CardId cardId;
    private final BoardController board;
    private final I18n i18n;
    private final ThemeManager themeManager;

    private final Stage stage = new Stage();
    private final TextArea editor = new TextArea();
    private final TextArea notesArea = new TextArea();
    private final WebView preview = new WebView();
    private final VBox checklistBox = new VBox(6);

    private CardDetailWindow(Card card, BoardController board, I18n i18n, ThemeManager themeManager) {
        this.cardId = card.id();
        this.board = board;
        this.i18n = i18n;
        this.themeManager = themeManager;

        editor.setText(card.description());
        editor.getStyleClass().add("md-editor");
        editor.setWrapText(true);
        editor.textProperty().addListener((obs, old, value) -> renderPreview(value));
        editor.setTooltip(new Tooltip(i18n.text("card.md.editor.tip")));

        notesArea.setText(card.notes());
        notesArea.getStyleClass().add("md-editor");
        notesArea.setWrapText(true);
        notesArea.setPromptText(i18n.text("card.notes.prompt"));
        notesArea.setTooltip(new Tooltip(i18n.text("card.notes.tip")));

        preview.getEngine().setJavaScriptEnabled(false);
        preview.setPrefHeight(300);

        Label titleLabel = new Label(card.title());
        titleLabel.getStyleClass().add("detail-title");
        titleLabel.setWrapText(true);

        SplitPane split = new SplitPane(wrapEditor(), preview);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(SPLIT_RATIO);
        VBox.setVgrow(split, Priority.ALWAYS);

        Tab descriptionTab = new Tab(i18n.text("card.tab.description"), split);
        Tab notesTab = new Tab(i18n.text("card.tab.notes"), buildNotesPane());
        Tab checklistTab = new Tab(i18n.text("card.tab.checklist"), buildChecklistPane());
        TabPane tabs = new TabPane(descriptionTab, notesTab, checklistTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        Button save = new Button(i18n.text("dialog.ok"));
        save.setOnAction(e -> onSave());
        Button cancel = new Button(i18n.text("dialog.cancel"));
        cancel.setOnAction(e -> stage.close());
        Button help = new Button("?");
        help.getStyleClass().add("md-help-button");
        help.setTooltip(new Tooltip(i18n.text("card.md.help.tip")));
        help.setOnAction(e -> openCheatSheet());
        Button copyAll = new Button("\u29C9");
        copyAll.getStyleClass().add("md-copy-button");
        copyAll.setTooltip(new Tooltip(i18n.text("card.md.copy.tip")));
        copyAll.setOnAction(e -> copyDescriptionToClipboard());
        copyAll.disableProperty().bind(editor.textProperty().isEmpty());
        javafx.scene.layout.Region helpGap = new javafx.scene.layout.Region();
        helpGap.setMinWidth(14);
        ButtonBar buttons = new ButtonBar();
        buttons.getButtons().addAll(help, copyAll, helpGap, save, cancel);
        buttons.setPadding(new Insets(8));

        BorderPane layout = new BorderPane();
        layout.setTop(titleLabel);
        layout.setCenter(tabs);
        layout.setBottom(buttons);
        layout.getStyleClass().add("detail-window");
        layout.setPadding(new Insets(10));

        Scene scene = new Scene(layout, 920, 600);
        scene.getStylesheets().add(themeManager.stylesheet());
        stage.setScene(scene);
        stage.setTitle(i18n.text("card.detail.title") + " \u2014 " + card.title());

        renderPreview(editor.getText());
        refreshChecklist();
    }

    /** Opens (or focuses) a detail window for the given card. */
    static void open(Card card, BoardController board, I18n i18n, ThemeManager themeManager) {
        CardDetailWindow window = new CardDetailWindow(card, board, i18n, themeManager);
        window.stage.show();
        window.stage.toFront();
        Platform.runLater(window.editor::requestFocus);
    }

    // ------------------------------------------------------------------
    // Tabs
    // ------------------------------------------------------------------

    private VBox wrapEditor() {
        Label caption = new Label(i18n.text("card.md.editor"));
        caption.getStyleClass().add("detail-caption");
        VBox editorBox = new VBox(4, caption, editor);
        VBox.setVgrow(editor, Priority.ALWAYS);
        return editorBox;
    }

    /** Notes tab: plain text + explicit save (kept separate from markdown). */
    private VBox buildNotesPane() {
        Label caption = new Label(i18n.text("card.notes.caption"));
        caption.getStyleClass().add("detail-caption");
        Button saveNotes = new Button(i18n.text("card.notes.save"));
        saveNotes.getStyleClass().add("tool-button");
        saveNotes.setOnAction(e -> board.onNotesSaved(cardId, notesArea.getText()));
        HBox header = new HBox(8, caption, saveNotes);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox pane = new VBox(4, header, notesArea);
        VBox.setVgrow(notesArea, Priority.ALWAYS);
        return pane;
    }

    /** Checklist tab: one add row plus one checkbox per item (flat list). */
    private VBox buildChecklistPane() {
        TextField newItem = new TextField();
        newItem.setPromptText(i18n.text("checklist.new.prompt"));
        newItem.setOnAction(e -> addChecklistItem(newItem));
        Button add = new Button(i18n.text("checklist.add"));
        add.getStyleClass().add("tool-button");
        add.setOnAction(e -> addChecklistItem(newItem));
        HBox addRow = new HBox(8, newItem, add);
        addRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(newItem, Priority.ALWAYS);

        VBox pane = new VBox(6, addRow, checklistBox);
        VBox.setVgrow(checklistBox, Priority.ALWAYS);
        return pane;
    }

    private void addChecklistItem(TextField newItem) {
        String text = newItem.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        board.onChecklistAdd(cardId, text);
        newItem.clear();
        refreshChecklist();
    }

    /** Re-renders the checklist from the card's current state. */
    private void refreshChecklist() {
        checklistBox.getChildren().clear();
        board.cardById(cardId).ifPresent(current -> {
            for (ChecklistItem item : current.checklist()) {
                CheckBox done = new CheckBox(item.text());
                done.setSelected(item.done());
                done.getStyleClass().add("checklist-item");
                done.setWrapText(true);
                done.setOnAction(e -> {
                    board.onChecklistToggle(cardId, item.id(), done.isSelected());
                    refreshChecklist();
                });
                ContextMenu menu = new ContextMenu(
                        menuItem(i18n.text("checklist.rename"), () -> renameItem(item)),
                        menuItem(i18n.text("checklist.convert"), () -> convertItem(item)),
                        menuItem(i18n.text("checklist.remove"), () -> removeItem(item)));
                done.setOnContextMenuRequested(e -> {
                    menu.show(done, e.getScreenX(), e.getScreenY());
                    e.consume();
                });
                checklistBox.getChildren().add(done);
            }
        });
    }

    private MenuItem menuItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> action.run());
        return item;
    }

    private void renameItem(ChecklistItem item) {
        TextInputDialog dialog = new TextInputDialog(item.text());
        dialog.setHeaderText(i18n.text("checklist.rename"));
        dialog.setContentText(i18n.text("dialog.title.label"));
        dialog.showAndWait().ifPresent(text -> {
            if (text != null && !text.isBlank()) {
                board.onChecklistRename(cardId, item.id(), text);
                refreshChecklist();
            }
        });
    }

    private void convertItem(ChecklistItem item) {
        board.onChecklistConvert(cardId, item.id());
        refreshChecklist();
    }

    private void removeItem(ChecklistItem item) {
        board.onChecklistRemove(cardId, item.id());
        refreshChecklist();
    }

    // ------------------------------------------------------------------
    // Description tab internals (unchanged from session 3)
    // ------------------------------------------------------------------

    private void renderPreview(String markdown) {
        boolean dark = themeManager.isDark();
        preview.getEngine().loadContent(Markdown.toStyledDocument(
                markdown,
                dark ? "#e5e7eb" : "#1f2937",
                dark ? "#16181d" : "#ffffff",
                dark ? "#262a33" : "#f0f2f5",
                dark ? "#343947" : "#d7dbe2",
                dark ? "#42a5f5" : "#1976d2"));
    }

    private void onSave() {
        String markdown = editor.getText();
        board.onDescriptionSaved(cardId, markdown);
        stage.close();
    }

    /** Copies the raw markdown text of the editor to the system clipboard. */
    private void copyDescriptionToClipboard() {
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(editor.getText());
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
    }

    /**
     * Quick syntax reference in a separate, resizable window: literal syntax
     * next to its live rendering. Stays open while the user keeps editing —
     * non-modal by design, like the detail window itself.
     */
    private void openCheatSheet() {
        boolean dark = themeManager.isDark();
        Stage sheet = new Stage();
        sheet.initOwner(stage);
        sheet.setTitle(i18n.text("card.md.help.title"));

        Label caption = new Label(i18n.text("card.md.help.caption"));
        caption.getStyleClass().add("detail-title");
        caption.setWrapText(true);
        BorderPane.setMargin(caption, new Insets(10, 10, 0, 10));

        WebView content = new WebView();
        content.getEngine().setJavaScriptEnabled(false);
        content.getEngine().loadContent(Markdown.cheatsheetDocument(
                i18n.text("card.md.help.write"),
                i18n.text("card.md.help.see"),
                i18n.text("card.md.editor.tip"),
                i18n.text("card.md.help.sample"),
                dark ? "#e5e7eb" : "#1f2937",
                dark ? "#16181d" : "#ffffff",
                dark ? "#262a33" : "#f0f2f5",
                dark ? "#343947" : "#d7dbe2",
                dark ? "#42a5f5" : "#1976d2"));

        BorderPane sheetLayout = new BorderPane(content);
        sheetLayout.setTop(caption);
        sheetLayout.getStyleClass().add("detail-window");
        Scene sheetScene = new Scene(sheetLayout, 640, 560);
        sheetScene.getStylesheets().add(themeManager.stylesheet());
        sheet.setScene(sheetScene);
        sheet.show();
    }
}
