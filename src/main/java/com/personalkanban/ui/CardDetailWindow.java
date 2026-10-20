package com.personalkanban.ui;

import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.CardNotes;
import com.personalkanban.domain.board.CardReminder;
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
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
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
import java.time.LocalDate;
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
    // Multi-note support (user request): the list holds one titled note per
    // row, the editor shows the selected one.
    private final ListView<CardNotes.Note> notesList = new ListView<>();
    private final TextField noteTitleField = new TextField();
    private CardNotes notesModel = CardNotes.empty();
    private boolean notesLoading;
    private final VBox checklistBox = new VBox(6);
    // Reminder moments (T23 · B10): the tab's list, re-rendered from the board
    // after every change so the window matches the aggregate.
    private final VBox reminderBox = new VBox(6);
    private final Label reminderError = new Label();

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
        notesEditor = new MarkdownEditor("", "card.notes.prompt", 300, i18n, themeManager);
        timeCommentEditor = new MarkdownEditor("", "time.track.comment.prompt", 70, i18n, themeManager);

        Label titleLabel = new Label(card.title());
        titleLabel.getStyleClass().add("detail-title");
        titleLabel.setWrapText(true);

        // B4 (T24): the due date (and the day it was finished, T24 · B11) is
        // part of the card's headline — it used to be gated on the reminders
        // tab but never actually shown, so checking a deadline meant opening
        // the edit dialog.
        Label metaLabel = new Label(describeDates(card));
        metaLabel.getStyleClass().add("detail-caption");
        VBox top = new VBox(2, titleLabel, metaLabel);

        Tab descriptionTab = new Tab(i18n.text("card.tab.description"), buildDescriptionPane());
        Tab notesTab = new Tab(i18n.text("card.tab.notes"), buildNotesPane());
        Tab checklistTab = new Tab(i18n.text("card.tab.checklist"), buildChecklistPane());
        Tab remindersTab = new Tab(i18n.text("card.tab.reminders"), buildRemindersPane());
        Tab timeTab = new Tab(i18n.text("card.tab.time"), buildTimePane());
        TabPane tabs = new TabPane(descriptionTab, notesTab, checklistTab, remindersTab, timeTab);
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
        layout.setTop(top);
        layout.setCenter(tabs);
        layout.setBottom(buttons);
        layout.getStyleClass().add("detail-window");
        layout.setPadding(new Insets(10));

        Scene scene = new Scene(layout, 920, 620);
        scene.getStylesheets().addAll(themeManager.stylesheets());
        stage.setScene(scene);
        stage.setTitle(i18n.text("card.detail.title") + " \u2014 " + card.title());

        // Esc behaves like «Cancelar» (user request, 2026-10-13): it closes the
        // window without saving. It is a bubbling handler, so a control that
        // consumes the key first — a text field, an open autocomplete list —
        // keeps it and the window stays open.
        scene.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                stage.close();
            }
        });

        refreshChecklist();
        refreshTime();
        refreshReminders();
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

    /**
     * Notes as a list of titled entries (user request): instead of one long
     * note, several smaller ones, each with a title to pick from the list and
     * a body edited on the right. Everything is persisted through the card's
     * single notes text (so undo keeps working) via {@code CardNotes}.
     */
    private VBox buildNotesPane() {
        Label caption = new Label(i18n.text("card.notes.caption"));
        caption.getStyleClass().add("detail-caption");

        notesList.setPrefWidth(210);
        notesList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(CardNotes.Note item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : noteLabel(item));
            }
        });
        notesList.getSelectionModel().selectedIndexProperty().addListener(
                (observable, was, now) -> onNoteSelected(now.intValue()));

        noteTitleField.setPromptText(i18n.text("card.notes.title.prompt"));

        // User request (2026-10-13): «Añadir nota» and «Guardar nota» belong on
        // the editor's own header row, next to the edit/render toggle; only
        // «Eliminar nota» stays at the bottom of the pane.
        Button remove = noteButton("card.notes.remove", this::removeNote);
        HBox actions = new HBox(8, remove);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox editor = new VBox(4, noteTitleField, notesEditor.node());
        VBox.setVgrow(notesEditor.node(), Priority.ALWAYS);
        HBox.setHgrow(editor, Priority.ALWAYS);

        HBox split = new HBox(10, notesList, editor);
        VBox.setVgrow(split, Priority.ALWAYS);
        VBox pane = new VBox(6, caption, split, actions);
        // A window is built once per open, so these land on the header once.
        notesEditor.addHeaderButton(noteButton("card.notes.add", this::addNote));
        notesEditor.addHeaderButton(noteButton("card.notes.save", this::saveCurrentNote));
        reloadNotes();
        return pane;
    }

    private Button noteButton(String key, Runnable action) {
        Button button = new Button(i18n.text(key));
        button.getStyleClass().add("tool-button");
        button.setOnAction(e -> action.run());
        return button;
    }

    /** Re-reads the notes of the card and keeps the previous selection. */
    private void reloadNotes() {
        notesModel = board.cardById(cardId)
                .map(card -> CardNotes.parse(card.notes()))
                .orElseGet(CardNotes::empty);
        int previous = notesList.getSelectionModel().getSelectedIndex();
        notesLoading = true;
        notesList.getItems().setAll(notesModel.notes());
        if (notesModel.isEmpty()) {
            notesList.getSelectionModel().clearSelection();
            noteTitleField.setText("");
            notesEditor.setText("");
        } else {
            int index = previous >= 0 && previous < notesModel.size()
                    ? previous : notesModel.size() - 1;
            notesList.getSelectionModel().select(index);
            loadNote(index);
        }
        notesLoading = false;
    }

    private void loadNote(int index) {
        if (index < 0 || index >= notesModel.size()) {
            return;
        }
        CardNotes.Note note = notesModel.notes().get(index);
        noteTitleField.setText(note.title());
        notesEditor.setText(note.body());
    }

    private void onNoteSelected(int index) {
        if (notesLoading || index < 0) {
            return;
        }
        loadNote(index);
    }

    /** List label: the title, or the first body line for untitled notes. */
    private String noteLabel(CardNotes.Note note) {
        if (!note.title().isEmpty()) {
            return note.title();
        }
        String firstLine = note.body().lines().findFirst().orElse("").strip();
        if (firstLine.isEmpty()) {
            return i18n.text("card.notes.untitled");
        }
        return firstLine.length() > 40 ? firstLine.substring(0, 40) + "\u2026" : firstLine;
    }

    /** Adds an empty titled note, selects it and lets the user name it. */
    private void addNote() {
        persistNotes(notesModel.withNote(nextDefaultTitle(), ""));
        notesList.getSelectionModel().selectLast();
        noteTitleField.requestFocus();
        noteTitleField.selectAll();
    }

    private String nextDefaultTitle() {
        String base = i18n.text("card.notes.default.title");
        String candidate = base;
        int suffix = 2;
        while (hasNoteTitled(candidate)) {
            candidate = base + " " + suffix++;
        }
        return candidate;
    }

    private boolean hasNoteTitled(String title) {
        return notesModel.notes().stream().anyMatch(note -> note.title().equals(title));
    }

    /**
     * Writes the title field and the editor into the selected note (or a new
     * one when nothing is selected) and persists the card's notes text.
     */
    private void saveCurrentNote() {
        int index = notesList.getSelectionModel().getSelectedIndex();
        String title = noteTitleField.getText() == null ? "" : noteTitleField.getText();
        String body = notesEditor.text();
        CardNotes updated = index >= 0
                ? notesModel.replaced(index, title, body)
                : notesModel.withNote(title, body);
        persistNotes(updated);
    }

    private void removeNote() {
        int index = notesList.getSelectionModel().getSelectedIndex();
        if (index < 0 || index >= notesModel.size()) {
            return;
        }
        String label = noteLabel(notesModel.notes().get(index));
        if (!board.confirm(i18n.text("card.notes.delete.confirm", label))) {
            return;
        }
        persistNotes(notesModel.removed(index));
    }

    private void persistNotes(CardNotes updated) {
        notesModel = updated;
        board.onNotesSaved(cardId, updated.serialize());
        reloadNotes();
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

    // ------------------------------------------------------------------
    // Reminders (T23 · B10)
    // ------------------------------------------------------------------

    /**
     * The card's reminder moments: the calendar of days between today and the
     * deadline (what the user asked for), plus a one-day-at-a-time row for a
     * precise moment, and the list of what is already scheduled. Reminders only
     * exist on cards with a due date, so a card without one explains what to do
     * instead of offering buttons that cannot work.
     */
    private VBox buildRemindersPane() {
        Label caption = new Label(i18n.text("card.reminders.caption"));
        caption.getStyleClass().add("detail-caption");
        caption.setWrapText(true);

        Button calendar = new Button(i18n.text("reminders.calendar"));
        calendar.getStyleClass().add("tool-button");
        calendar.setOnAction(e -> {
            board.onEditReminders(cardId);
            refreshReminders();
        });

        DatePicker day = new DatePicker(LocalDate.now());
        day.setPrefWidth(150);
        TextField hour = new TextField();
        hour.setPrefColumnCount(5);
        hour.setPromptText(ReminderCalendar.formatTime(ReminderCalendar.DEFAULT_TIME));
        Label hourHint = new Label(i18n.text("reminders.time.hint"));
        hourHint.getStyleClass().add("detail-caption");
        Button add = new Button(i18n.text("reminders.add"));
        add.getStyleClass().add("tool-button");
        add.setOnAction(e -> addReminder(day, hour));

        HBox addRow = new HBox(8, day, hour, add, hourHint);
        addRow.setAlignment(Pos.CENTER_LEFT);

        reminderError.getStyleClass().add("detail-caption");
        reminderError.setWrapText(true);

        ScrollPane scroll = new ScrollPane(reminderBox);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox pane = new VBox(8, caption, calendar, addRow, reminderError, scroll);
        if (!hasDueDate()) {
            calendar.setDisable(true);
            day.setDisable(true);
            hour.setDisable(true);
            add.setDisable(true);
            reminderError.setText(i18n.text("reminders.no.due"));
        }
        return pane;
    }

    private boolean hasDueDate() {
        return board.cardById(cardId).map(card -> card.dueDate() != null).orElse(false);
    }

    /**
     * B4 (T24): the window's headline meta line — "Vence: … · Terminada: …",
     * dates shown in the user's locale. Blank when the card has neither, so
     * the title keeps its compact look.
     */
    private String describeDates(Card card) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (card.dueDate() != null) {
            parts.add(i18n.text("card.detail.meta.due",
                    card.dueDate().format(java.time.format.DateTimeFormatter
                            .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))));
        }
        if (card.isDone() && card.completedAt() != null) {
            parts.add(i18n.text("card.detail.meta.completed",
                    card.completedAt().format(java.time.format.DateTimeFormatter
                            .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))));
        }
        return String.join("  \u00B7  ", parts);
    }

    /** Adds the day the picker shows, with the (optional) hour of the field. */
    private void addReminder(DatePicker day, TextField hour) {
        LocalDate date = day.getValue();
        if (date == null) {
            reminderError.setText(i18n.text("reminders.pick.day"));
            return;
        }
        ReminderCalendar.TimeInput time = ReminderCalendar.parseTime(hour.getText());
        if (!time.valid()) {
            reminderError.setText(i18n.text("reminders.time.invalid"));
            return;
        }
        LocalDate due = board.cardById(cardId).map(card -> card.dueDate()).orElse(null);
        if (due == null) {
            reminderError.setText(i18n.text("reminders.no.due"));
            return;
        }
        if (date.isAfter(due)) {
            reminderError.setText(i18n.text("reminders.after.due", due.toString()));
            return;
        }
        reminderError.setText("");
        board.onAddReminder(cardId, date, time.time());
        refreshReminders();
    }

    /** Re-renders the list of scheduled moments from the card's current state. */
    private void refreshReminders() {
        if (!hasDueDate()) {
            reminderBox.getChildren().setAll(captionLabel(i18n.text("reminders.none")));
            return;
        }
        List<CardReminder> reminders = board.remindersOf(cardId);
        if (reminders.isEmpty()) {
            reminderBox.getChildren().setAll(captionLabel(i18n.text("reminders.none")));
            return;
        }
        List<javafx.scene.Node> rows = new java.util.ArrayList<>();
        for (CardReminder reminder : reminders) {
            rows.add(reminderRow(reminder));
        }
        reminderBox.getChildren().setAll(rows);
    }

    private Label captionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("detail-caption");
        return label;
    }

    /** One scheduled moment: attended checkbox, moment, and a remove button. */
    private HBox reminderRow(CardReminder reminder) {
        CheckBox attended = new CheckBox();
        attended.setSelected(reminder.acknowledged());
        attended.setTooltip(new Tooltip(i18n.text("reminders.ack.one")));
        attended.setOnAction(e -> {
            board.onAcknowledgeReminder(cardId, reminder.id(), attended.isSelected());
            refreshReminders();
        });

        Label moment = new Label(reminder.describe());
        moment.getStyleClass().add(reminder.acknowledged()
                ? "reminder-moment-done" : "reminder-moment");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button remove = new Button("\u2716");
        remove.getStyleClass().add("time-entry-delete");
        remove.setTooltip(new Tooltip(i18n.text("reminders.remove")));
        remove.setOnAction(e -> {
            board.onRemoveReminder(cardId, reminder.id());
            refreshReminders();
        });

        HBox row = new HBox(8, attended, moment, spacer, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
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
            // T16 · B8: subtasks render indented right where they sit in the
            // list, and the menu offers their own actions (outdent, no
            // nesting); a parent cannot be indented under another item.
            String previousTopLevelId = null;
            for (ChecklistItem item : current.checklist()) {
                boolean hasChildren = current.subtaskCount(item.id()) > 0;
                CheckBox done = new CheckBox(item.text());
                done.setSelected(item.done());
                done.getStyleClass().add("checklist-item");
                if (item.isSubtask()) {
                    done.getStyleClass().add("checklist-subtask");
                    VBox.setMargin(done, new javafx.geometry.Insets(0, 0, 0, 26));
                }
                done.setWrapText(true);
                done.setOnAction(e -> {
                    board.onChecklistToggle(cardId, item.id(), done.isSelected());
                    refreshChecklist();
                });
                MenuItem addSubtask = menuItem(i18n.text("checklist.add.subtask"),
                        () -> addSubtask(item));
                final String indentUnder = previousTopLevelId;
                MenuItem indent = menuItem(i18n.text("checklist.indent"),
                        () -> indentItem(item, indentUnder));
                indent.setDisable(previousTopLevelId == null || hasChildren);
                MenuItem outdent = menuItem(i18n.text("checklist.outdent"),
                        () -> outdentItem(item));
                MenuItem rename = menuItem(i18n.text("checklist.rename"),
                        () -> renameItem(item));
                MenuItem convert = menuItem(i18n.text("checklist.convert"),
                        () -> convertItem(item));
                MenuItem remove = menuItem(i18n.text("checklist.remove"),
                        () -> removeItem(item));
                ContextMenu menu = item.isSubtask()
                        ? new ContextMenu(outdent, rename, convert, remove)
                        : new ContextMenu(addSubtask, indent, rename, convert, remove);
                done.setOnContextMenuRequested(e -> {
                    menu.show(done, e.getScreenX(), e.getScreenY());
                    e.consume();
                });
                checklistBox.getChildren().add(done);
                if (!item.isSubtask()) {
                    previousTopLevelId = item.id();
                }
            }
        });
    }

    /** Asks for the text of a new subtask of {@code parent} (T16 · B8). */
    private void addSubtask(ChecklistItem parent) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText(i18n.text("checklist.add.subtask"));
        dialog.setContentText(i18n.text("checklist.subtask.prompt"));
        dialog.showAndWait().ifPresent(text -> {
            if (text != null && !text.isBlank()) {
                board.onChecklistAddSubtask(cardId, parent.id(), text);
                refreshChecklist();
            }
        });
    }

    /** Indents {@code item} under the previous top-level item. */
    private void indentItem(ChecklistItem item, String parentId) {
        if (parentId == null) {
            return;
        }
        board.onChecklistIndent(cardId, item.id(), parentId);
        refreshChecklist();
    }

    /** Sends a subtask back to the top level. */
    private void outdentItem(ChecklistItem item) {
        board.onChecklistOutdent(cardId, item.id());
        refreshChecklist();
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
        // Committing the note in progress here too: closing with an edited
        // note must never drop it.
        int index = notesList.getSelectionModel().getSelectedIndex();
        String title = noteTitleField.getText() == null ? "" : noteTitleField.getText();
        String body = notesEditor.text();
        CardNotes updated = index >= 0
                ? notesModel.replaced(index, title, body)
                : notesModel.withNote(title, body);
        if (!updated.serialize().equals(notesModel.serialize())) {
            board.onNotesSaved(cardId, updated.serialize());
        }
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
        sheetScene.getStylesheets().addAll(themeManager.stylesheets());
        sheet.setScene(sheetScene);
        sheet.show();
    }
}
