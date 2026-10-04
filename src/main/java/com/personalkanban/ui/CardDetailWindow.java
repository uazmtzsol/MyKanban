package com.personalkanban.ui;

import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ChecklistItem;
import com.personalkanban.domain.board.TimelineEntry;
import com.personalkanban.ui.theme.ThemeManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Non-modal card detail window (Pure Fabrication): four tabs — the markdown
 * description editor, the markdown notes, the flat checklist, and the
 * time-tracking log (session 6). The stopwatch toggle creates a record on the
 * first click and closes it on the second; a record's start/end/comment are
 * never edited, only deleted. Every multiline field reuses {@link MarkdownEditor}.
 */
final class CardDetailWindow {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final CardId cardId;
    private final BoardController board;
    private final I18n i18n;
    private final ThemeManager themeManager;

    private final Stage stage = new Stage();
    private final MarkdownEditor descriptionEditor;
    private final MarkdownEditor notesEditor;
    private final MarkdownEditor timeCommentEditor;
    private final VBox checklistBox = new VBox(6);

    private final ToggleButton stopwatch = new ToggleButton("\u23F1");
    private final Label timeStatus = new Label();
    private final Label timeTotal = new Label();
    private final VBox timeEntriesBox = new VBox(6);

    private CardDetailWindow(Card card, BoardController board, I18n i18n, ThemeManager themeManager) {
        this.cardId = card.id();
        this.board = board;
        this.i18n = i18n;
        this.themeManager = themeManager;

        descriptionEditor = new MarkdownEditor(card.description(), null, 300, i18n, themeManager);
        notesEditor = new MarkdownEditor(card.notes(), "card.notes.prompt", 300, i18n, themeManager);
        timeCommentEditor = new MarkdownEditor("", "time.track.comment.prompt", 70, i18n, themeManager);

        Label titleLabel = new Label(card.title());
        titleLabel.getStyleClass().add("detail-title");
        titleLabel.setWrapText(true);

        Tab descriptionTab = new Tab(i18n.text("card.tab.description"), buildDescriptionPane());
        Tab notesTab = new Tab(i18n.text("card.tab.notes"), buildNotesPane());
        Tab checklistTab = new Tab(i18n.text("card.tab.checklist"), buildChecklistPane());
        Tab timeTab = new Tab(i18n.text("card.tab.time"), buildTimePane());
        TabPane tabs = new TabPane(descriptionTab, notesTab, checklistTab, timeTab);
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
        ButtonBar buttons = new ButtonBar();
        buttons.getButtons().addAll(help, copyAll, save, cancel);
        buttons.setPadding(new Insets(8));

        BorderPane layout = new BorderPane();
        layout.setTop(titleLabel);
        layout.setCenter(tabs);
        layout.setBottom(buttons);
        layout.getStyleClass().add("detail-window");
        layout.setPadding(new Insets(10));

        Scene scene = new Scene(layout, 920, 620);
        scene.getStylesheets().add(themeManager.stylesheet());
        stage.setScene(scene);
        stage.setTitle(i18n.text("card.detail.title") + " \u2014 " + card.title());

        refreshChecklist();
        refreshTime();
    }

    /** Opens (or focuses) a detail window for the given card. */
    static void open(Card card, BoardController board, I18n i18n, ThemeManager themeManager) {
        CardDetailWindow window = new CardDetailWindow(card, board, i18n, themeManager);
        window.stage.show();
        window.stage.toFront();
        Platform.runLater(window.descriptionEditor::focusEditor);
    }

    // ------------------------------------------------------------------
    // Tabs
    // ------------------------------------------------------------------

    private VBox buildDescriptionPane() {
        Label caption = new Label(i18n.text("card.md.editor"));
        caption.getStyleClass().add("detail-caption");
        VBox pane = new VBox(4, caption, descriptionEditor.node());
        VBox.setVgrow(descriptionEditor.node(), Priority.ALWAYS);
        return pane;
    }

    private VBox buildNotesPane() {
        Label caption = new Label(i18n.text("card.notes.caption"));
        caption.getStyleClass().add("detail-caption");
        Button saveNotes = new Button(i18n.text("card.notes.save"));
        saveNotes.getStyleClass().add("tool-button");
        saveNotes.setOnAction(e -> board.onNotesSaved(cardId, notesEditor.text()));
        HBox header = new HBox(8, caption, saveNotes);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox pane = new VBox(4, header, notesEditor.node());
        VBox.setVgrow(notesEditor.node(), Priority.ALWAYS);
        return pane;
    }

    private VBox buildChecklistPane() {
        TextField newItem = new TextField();
        newItem.setPromptText(i18n.text("checklist.new.prompt"));
        newItem.setOnAction(e -> addChecklistItem(newItem));
        Button add = new Button(i18n.text("checklist.add"));
        add.getStyleClass().add("tool-button");
        add.setOnAction(e -> addChecklistItem(newItem));
        HBox addRow = new HBox(8, newItem, add);
        addRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(newItem, Priority.ALWAYS);

        VBox pane = new VBox(6, addRow, checklistBox);
        VBox.setVgrow(checklistBox, Priority.ALWAYS);
        return pane;
    }

    private VBox buildTimePane() {
        stopwatch.getStyleClass().add("time-stopwatch");
        stopwatch.setTooltip(new Tooltip(i18n.text("time.track.start")));
        stopwatch.setOnAction(e -> onStopwatchToggled());

        Button saveComment = new Button(i18n.text("time.track.saveComment"));
        saveComment.getStyleClass().add("tool-button");
        saveComment.setOnAction(e -> board.onCommentTimeEntry(cardId, timeCommentEditor.text()));

        VBox commentBox = new VBox(4,
                new Label(i18n.text("time.track.comment.caption")), timeCommentEditor.node());
        HBox controls = new HBox(10, stopwatch, timeStatus, saveComment);
        controls.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(timeStatus, Priority.ALWAYS);

        ScrollPane scroll = new ScrollPane(timeEntriesBox);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox pane = new VBox(8, controls, commentBox, new Separator(), timeTotal, scroll);
        return pane;
    }

    private void onStopwatchToggled() {
        if (stopwatch.isSelected()) {
            // Starting a new record: replace any previous comment text.
            timeCommentEditor.setText("");
            board.onStartTimeTracking(cardId);
        } else {
            String comment = timeCommentEditor.text();
            if (comment != null && !comment.isBlank()) {
                board.onCommentTimeEntry(cardId, comment);
            }
            board.onStopTimeTracking(cardId);
        }
        refreshTime();
    }

    /** Re-renders the time log from the card's current state. */
    private void refreshTime() {
        boolean tracking = board.isTracking(cardId);
        stopwatch.setSelected(tracking);
        timeStatus.setText(tracking ? i18n.text("time.track.status.running", currentStart())
                : i18n.text("time.track.status.idle"));
        stopwatch.setTooltip(new Tooltip(i18n.text(tracking ? "time.track.stop" : "time.track.start")));

        timeEntriesBox.getChildren().clear();
        List<TimelineEntry> entries = board.cardById(cardId)
                .map(card -> card.timeline().entries())
                .orElse(List.of());
        if (entries.isEmpty()) {
            Label empty = new Label(i18n.text("time.track.none"));
            empty.getStyleClass().add("detail-caption");
            timeEntriesBox.getChildren().add(empty);
        } else {
            Instant now = Instant.now();
            long totalMillis = 0;
            for (TimelineEntry entry : entries) {
                totalMillis += entry.durationMillis(now);
                timeEntriesBox.getChildren().add(timeEntryRow(entry, now));
            }
            timeTotal.setText(i18n.text("time.track.total", formatDuration(Duration.ofMillis(totalMillis))));
        }
        if (entries.isEmpty()) {
            timeTotal.setText(i18n.text("time.track.total", formatDuration(Duration.ZERO)));
        }
    }

    private String currentStart() {
        return board.cardById(cardId)
                .map(card -> card.timeline().runningEntry())
                .map(entry -> entry.start() == null ? "" : STAMP.format(entry.start()))
                .orElse("");
    }

    private HBox timeEntryRow(TimelineEntry entry, Instant now) {
        String start = entry.start() == null ? "?" : STAMP.format(entry.start());
        String end = entry.end() == null ? "\u2026" : STAMP.format(entry.end());
        String duration = formatDuration(entry.duration(now));
        Label main = new Label(start + "  \u2192  " + end + "   (" + duration + ")");
        main.getStyleClass().add("time-entry");
        VBox textBox = new VBox(2, main);
        if (entry.hasComment()) {
            Label comment = new Label(entry.comment());
            comment.getStyleClass().add("time-entry-comment");
            comment.setWrapText(true);
            textBox.getChildren().add(comment);
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button delete = new Button("\u2716");
        delete.getStyleClass().add("time-entry-delete");
        delete.setTooltip(new Tooltip(i18n.text("time.track.delete")));
        delete.setOnAction(e -> {
            if (board.confirm(i18n.text("time.track.delete.confirm"))) {
                board.onRemoveTimeEntry(cardId, entry.id());
                refreshTime();
            }
        });
        HBox row = new HBox(8, textBox, spacer, delete);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static String formatDuration(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format("%d:%02d", minutes, secs);
    }

    // ------------------------------------------------------------------
    // Checklist
    // ------------------------------------------------------------------

    private void addChecklistItem(TextField newItem) {
        String text = newItem.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        board.onChecklistAdd(cardId, text);
        newItem.clear();
        refreshChecklist();
    }

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
    // Save / helpers
    // ------------------------------------------------------------------

    private void onSave() {
        board.onDescriptionSaved(cardId, descriptionEditor.text());
        stage.close();
    }

    private void copyDescriptionToClipboard() {
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(descriptionEditor.text());
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
    }

    /**
     * Quick syntax reference in a separate, resizable window: literal syntax
     * next to its live rendering.
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

        javafx.scene.web.WebView content = new javafx.scene.web.WebView();
        content.getEngine().setJavaScriptEnabled(false);
        content.getEngine().loadContent(com.personalkanban.ui.markdown.Markdown.cheatsheetDocument(
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
