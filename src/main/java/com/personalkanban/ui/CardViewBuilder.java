package com.personalkanban.ui;

import com.personalkanban.application.BoardService;
import com.personalkanban.application.CardViewSettings;
import com.personalkanban.application.StylePrefs;
import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.CardNotes;
import com.personalkanban.domain.board.ChecklistItem;
import com.personalkanban.ui.markdown.MarkdownSummary;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Map;

/**
 * Builds one card UI (Pure Fabrication): title, optional description,
 * due-date badge (red when overdue), label chips, edit/delete affordances,
 * and drag-source behavior for cross-column moves.
 */
final class CardViewBuilder {

    /** Preview mode: how many rows of the description the card front shows. */
    static final int PREVIEW_LINES = 3;

    /**
     * Preview mode: soft character cap — the description area is a fixed
     * 220px wide at 12px (≈36 chars/row → 3 rows ≈ 120 chars), so a paragraph
     * without line breaks would otherwise render in full and look identical
     * to the FULL mode (session 10, F2).
     */
    static final int PREVIEW_MAX_CHARS = 120;

    /**
     * Notes content (N-14): the card front shows a short digest, not the whole
     * note — a long note would otherwise make one card taller than the column.
     */
    static final int NOTES_PREVIEW_MAX_CHARS = 160;

    private static final DateTimeFormatter DUE_FORMAT =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

    private CardViewBuilder() {
    }

    static VBox build(BoardService service, I18n i18n, Dialogs dialogs, BoardController board,
                      Card card, boolean selectionMode, boolean dark) {
        var settings = board.currentCardViewSettings();
        var mode = settings.effectiveMode(card.id().value());
        var content = settings.effectiveContent(card.id().value());
        // One folder listing per card render feeds both the button state and
        // the attachment badge.
        int attachmentCount = board.attachmentCountOf(card.id());

        Label title = new Label(card.title());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        VBox view = new VBox(4);
        view.getStyleClass().addAll("card", ColorCss.styleClass(card.color()));
        // Keyboard navigation (arrow keys / Enter) locates the card node by
        // its user data and needs it focusable, so the focused card shows the
        // .card:focused highlight.
        view.setUserData(card.id());
        view.setFocusTraversable(true);
        // One inline style: the surface tint first, then the custom
        // flag/label colors (a later declaration wins), so the
        // priority highlight and the label rules never overwrite
        // each other or the card's own color.
        // Inline colors win over the stylesheet, so a SELECTED card must not
        // receive the surface / flag / label overrides: the .card-selected
        // rule is what makes the selection unmistakable (session 13: on a
        // colored card, whose accent arrives inline, the selection used to be
        // invisible).
        boolean selected = board.isCardSelected(card.id());
        if (!selected) {
            StringBuilder style = new StringBuilder(ColorCss.surfaceStyle(card.color(), dark));
            StylePrefs.StyleColors flagColors = flagColors(service, card, dark);
            StylePrefs.StyleColors labelColors =
                    service.resolvedLabelStyle(card.labels(), dark).orElse(null);
            appendColors(style, flagColors != null ? flagColors : labelColors);
            view.setStyle(style.toString());
        }
        // Quick-flag highlight classes (user request: (i)/(u)/(iu) must stand
        // out); CSS owns the look per theme.
        if (card.isUrgentAndImportantCard()) {
            view.getStyleClass().add("card-priority-urgent-important");
        } else if (card.isUrgentOnlyCard()) {
            view.getStyleClass().add("card-priority-urgent");
        } else if (card.isImportantOnlyCard()) {
            view.getStyleClass().add("card-priority-important");
        }
        if (selected) {
            view.getStyleClass().add("card-selected");
        }
        // The "current" card (session 13): the one a mouse click or the arrow
        // keys left selected. Only one card in the whole board carries it.
        if (board.isCardCurrent(card.id())) {
            view.getStyleClass().add("card-current");
        }

        // --- Title row: title + quick flags (★ Importante, ! Urgente) ---
        HBox titleRow = new HBox(6);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, javafx.scene.layout.Priority.ALWAYS);
        titleRow.getChildren().add(title);
        if (!selectionMode) {
            titleRow.getChildren().add(flagButton(i18n, board, card, Card.LABEL_IMPORTANT,
                    "\u2605", "card.flag.important", "card-flag-important"));
            titleRow.getChildren().add(flagButton(i18n, board, card, Card.LABEL_URGENT,
                    "!", "card.flag.urgent", "card-flag-urgent"));
            titleRow.getChildren().add(contentButton(i18n, board, card, content));
            titleRow.getChildren().add(notesButton(i18n, board, card));
            titleRow.getChildren().add(attachmentsButton(i18n, board, card, attachmentCount));
        }
        view.getChildren().add(titleRow);

        addContentArea(board, i18n, card, mode, content, view);

        addDueBadgeIfPresent(i18n, card, view);
        addReminderBadgeIfPresent(i18n, card, view);
        addChecklistProgressIfPresent(i18n, card, view);
        addNotesIndicatorIfPresent(i18n, card, view);
        addAttachmentsIndicatorIfPresent(i18n, attachmentCount, view);
        addProcessChipIfPresent(board, card, view);
        addRelationBadgesIfPresent(i18n, board, card, view);
        addLabelChipsIfPresent(i18n, board, card, view, dark);

        HBox actions = new HBox(4,
                cardButton(i18n, "\u270E", "card.edit", board, card.id(), true),
                cardButton(i18n, "\u2715", "card.delete", board, card.id(), false));
        actions.setAlignment(Pos.CENTER_RIGHT);
        if (!selectionMode) {
            view.getChildren().add(actions);
        } else {
            view.getStyleClass().add("card-selectable");
        }

        // Double-click opens the markdown detail window (single click only
        // toggles selection while the selection mode is active); right click
        // offers the per-card view-mode override.
        view.setOnMouseClicked(event -> {
            if (selectionMode) {
                if (event.getClickCount() == 1) {
                    board.onToggleCardSelection(card.id());
                    event.consume();
                }
                return;
            }
            if (event.getClickCount() == 1) {
                // Session 13 (user request): a plain click makes this the
                // current card — the same state the arrow keys move — so the
                // card is selected on sight and the shortcuts that act on the
                // current card work right after clicking.
                board.onCardSelected(card.id());
            }
            if (event.getClickCount() == 2) {
                board.onOpenCardDetail(card.id());
                event.consume();
            }
        });
        if (!selectionMode) {
            CardViewSettings.Mode currentMode = mode;
            javafx.scene.control.ContextMenu viewMenu = new javafx.scene.control.ContextMenu();
            for (CardViewSettings.Mode modeOption : CardViewSettings.Mode.values()) {
                javafx.scene.control.RadioMenuItem item = new javafx.scene.control.RadioMenuItem(
                        board.cardViewModeText(modeOption));
                item.setUserData(modeOption);
                item.setSelected(modeOption == currentMode);
                item.setOnAction(e -> board.onSetCardViewMode(card.id(), modeOption));
                viewMenu.getItems().add(item);
            }
            viewMenu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
            javafx.scene.control.MenuItem reset = new javafx.scene.control.MenuItem(
                    board.cardViewModeText(null));
            reset.setVisible(settings.hasOverride(card.id().value()));
            reset.setOnAction(e -> board.onSetCardViewMode(card.id(), null));
            viewMenu.getItems().add(reset);
            viewMenu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
            javafx.scene.control.MenuItem transfer = new javafx.scene.control.MenuItem(
                    i18n.text("card.transfer.menu"));
            transfer.setOnAction(e -> board.onTransferCardToBoard(card.id()));
            viewMenu.getItems().add(transfer);
            // Session 10 (F3): export this one card as txt / markdown / pdf.
            javafx.scene.control.MenuItem exportItem = new javafx.scene.control.MenuItem(
                    i18n.text("card.export.menu"));
            exportItem.setOnAction(e -> board.onExportCard(card.id()));
            viewMenu.getItems().add(exportItem);
            // Session 10 (board cleanup): archive = keep the card (and every
            // feature it travels with) but hide it until "show archived".
            javafx.scene.control.MenuItem archiveItem = new javafx.scene.control.MenuItem(
                    i18n.text("card.archive.menu"));
            archiveItem.setOnAction(e -> board.onToggleCardLabel(card.id(),
                    com.personalkanban.domain.board.Card.LABEL_ARCHIVED));
            viewMenu.getItems().add(archiveItem);
            // Session 15 (T5/B3): quick reschedule without opening the card.
            // Each entry computes its date when it is used, so a window left
            // open overnight still reschedules from the real "today".
            Menu reschedule = new Menu(i18n.text("card.reschedule.menu"));
            reschedule.getItems().add(rescheduleItem(i18n, board, card,
                    "card.reschedule.today", day -> day));
            reschedule.getItems().add(rescheduleItem(i18n, board, card,
                    "card.reschedule.tomorrow", day -> day.plusDays(1)));
            reschedule.getItems().add(rescheduleItem(i18n, board, card,
                    "card.reschedule.next.week", day -> day.plusDays(7)));
            reschedule.getItems().add(rescheduleItem(i18n, board, card,
                    "card.reschedule.next.monday",
                    day -> day.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY))));
            reschedule.getItems().add(new javafx.scene.control.SeparatorMenuItem());
            reschedule.getItems().add(rescheduleItem(i18n, board, card,
                    "card.reschedule.clear", day -> null));
            viewMenu.getItems().add(reschedule);
            // T23 · B10: the reminder calendar of this card. Only cards with a
            // deadline can carry reminders, so the entry explains itself as
            // disabled instead of opening a calendar with nothing to pick.
            javafx.scene.control.MenuItem reminders = new javafx.scene.control.MenuItem(
                    i18n.text("card.reminders.menu"));
            reminders.setDisable(card.dueDate() == null);
            reminders.setOnAction(e -> board.onEditReminders(card.id()));
            viewMenu.getItems().add(reminders);
            // T9 · N-5 (user request): the card's process, right here, without
            // entering selection mode. A radio menu, so the checked entry is the
            // process the card is in; «Sin proceso» takes it out of it.
            viewMenu.getItems().add(processMenu(i18n, board, card));
            view.setOnContextMenuRequested(event -> {
                CardViewSettings.Mode effective = settings.effectiveMode(card.id().value());
                // Only the "reset override" entry depends on the card having
                // an override. The old loop hid EVERY plain menu item —
                // separators and "move/copy to another board" included — until
                // one existed, which also would have hidden this export entry.
                reset.setVisible(settings.hasOverride(card.id().value()));
                for (javafx.scene.control.MenuItem item : viewMenu.getItems()) {
                    if (item instanceof javafx.scene.control.RadioMenuItem radio) {
                        radio.setSelected(radio.getUserData() == effective);
                    }
                }
                viewMenu.show(view, event.getScreenX(), event.getScreenY());
                event.consume();
            });
        }

        installDragSource(view, card.id());
        return view;
    }

    /**
     * The card's process as a submenu of its context menu (T9 · N-5): the
     * board's processes as radio entries plus «Sin proceso». Choosing one
     * assigns this single card — the same list and the same labels as the bulk
     * bar, so both routes cannot drift apart.
     */
    static Menu processMenu(I18n i18n, BoardController board, Card card) {
        Menu menu = new Menu(i18n.text("card.process.label"));
        List<com.personalkanban.domain.board.Process> processes = board.currentProcesses();

        javafx.scene.control.RadioMenuItem none = processItem(
                i18n.text("card.process.none"), card.processId() == null,
                () -> board.onAssignProcessToCard(card.id(), null));
        menu.getItems().add(none);

        for (com.personalkanban.domain.board.Process process : processes) {
            menu.getItems().add(processItem("\u26AD " + process.name(),
                    process.id().equals(card.processId()),
                    () -> board.onAssignProcessToCard(card.id(), process.id())));
        }
        if (processes.isEmpty()) {
            none.setDisable(true);
        }
        return menu;
    }

    /** Radio entry of the process submenu: label + checked state + action. */
    private static javafx.scene.control.RadioMenuItem processItem(
            String label, boolean selected, Runnable action) {
        javafx.scene.control.RadioMenuItem item = new javafx.scene.control.RadioMenuItem(label);
        item.setSelected(selected);
        item.setOnAction(e -> action.run());
        return item;
    }

    /**
     * One entry of the reschedule submenu: {@code offset} turns the day the user
     * clicks on into the new due date (null clears the date).
     */
    private static MenuItem rescheduleItem(I18n i18n, BoardController board, Card card,
                                           String labelKey,
                                           java.util.function.UnaryOperator<LocalDate> offset) {
        MenuItem item = new MenuItem(i18n.text(labelKey));
        item.setOnAction(e -> board.onRescheduleCard(card.id(), offset.apply(LocalDate.now())));
        return item;
    }

    /** Custom highlight colors of the card's quick-flag state, if any. */
    private static StylePrefs.StyleColors flagColors(BoardService service, Card card, boolean dark) {
        if (card.isUrgentAndImportantCard()) {
            return service.priorityStyle(StylePrefs.URGENT_IMPORTANT, dark);
        }
        if (card.isUrgentOnlyCard()) {
            return service.priorityStyle(StylePrefs.URGENT, dark);
        }
        if (card.isImportantOnlyCard()) {
            return service.priorityStyle(StylePrefs.IMPORTANT, dark);
        }
        return null;
    }

    /** Appends background/text declarations; null fields are skipped. */
    private static void appendColors(StringBuilder style, StylePrefs.StyleColors colors) {
        if (colors == null) {
            return;
        }
        if (colors.background() != null) {
            style.append("-fx-background-color: ").append(colors.background()).append(";");
        }
        if (colors.text() != null) {
            style.append("-fx-text-fill: ").append(colors.text()).append(";");
        }
    }

    /** "☑ n/m" progress badge when the card carries a checklist. */
    private static void addChecklistProgressIfPresent(I18n i18n, Card card, VBox view) {
        if (card.checklistCount() == 0) {
            return;
        }
        long done = card.doneChecklistCount();
        Label badge = new Label("\u2611 " + done + "/" + card.checklistCount());
        badge.getStyleClass().add(done == card.checklistCount()
                ? "card-checklist-done" : "card-checklist");
        badge.setTooltip(new Tooltip(i18n.text("checklist.progress.tip")));
        view.getChildren().add(badge);
    }

    /**
     * "\uD83D\uDCDD n" hint when the card has notes: the tooltip shows the text
     * for a single note and the list of titles once there are several.
     */
    private static void addNotesIndicatorIfPresent(I18n i18n, Card card, VBox view) {
        CardNotes notes = CardNotes.parse(card.notes());
        if (notes.isEmpty()) {
            return;
        }
        Label badge = new Label(notes.size() == 1
                ? "\uD83D\uDCDD" : "\uD83D\uDCDD " + notes.size());
        badge.getStyleClass().add("card-notes-indicator");
        badge.setTooltip(new Tooltip(notesTooltip(i18n, notes)));
        view.getChildren().add(badge);
    }

    private static String notesTooltip(I18n i18n, CardNotes notes) {
        if (notes.size() == 1) {
            String body = notes.notes().get(0).body();
            return body.length() > 120 ? body.substring(0, 120) + "\u2026" : body;
        }
        String titles = notes.notes().stream()
                .map(note -> note.title().isEmpty()
                        ? i18n.text("card.notes.untitled") : note.title())
                .collect(java.util.stream.Collectors.joining("\n\u2022 ", "\u2022 ", ""));
        return i18n.text("card.notes.count.tip", notes.size()) + "\n" + titles;
    }

    /** "\uD83D\uDCCE n" badge when the card folder holds reference files. */
    private static void addAttachmentsIndicatorIfPresent(I18n i18n, int count, VBox view) {
        if (count == 0) {
            return;
        }
        Label badge = new Label("\uD83D\uDCCE " + count);
        badge.getStyleClass().add("card-attachments");
        badge.setTooltip(new Tooltip(i18n.text("card.attachments.badge.tip", count)));
        view.getChildren().add(badge);
    }

    /** Small chip naming the process the card belongs to (session 4.6). */
    private static void addProcessChipIfPresent(BoardController board, Card card, VBox view) {
        if (card.processId() == null) {
            return;
        }
        board.currentProcesses().stream()
                .filter(process -> process.id().equals(card.processId()))
                .findFirst()
                .ifPresent(process -> {
                    Label chip = new Label("\u26AD " + process.name());
                    chip.getStyleClass().add("card-process-chip");
                    view.getChildren().add(chip);
                });
    }

    /** "\u2190n \u2192n" badges counting predecessors/successors (session 4.6). */
    private static void addRelationBadgesIfPresent(I18n i18n, BoardController board,
                                                   Card card, VBox view) {
        int predecessors = board.predecessorCountOf(card.id());
        int successors = board.successorCountOf(card.id());
        if (predecessors == 0 && successors == 0) {
            return;
        }
        Label badge = new Label("\u2190" + predecessors + " \u2192" + successors);
        badge.getStyleClass().add("card-relations");
        badge.setTooltip(new Tooltip(i18n.text("card.relations.tip")));
        view.getChildren().add(badge);
    }

    /**
     * "\uD83D\uDD14 n" badge when the card carries reminders: the number of
     * moments, muted while they are all attended and in the alert style while
     * any of them is still pending (T23 · B10).
     */
    private static void addReminderBadgeIfPresent(I18n i18n, Card card, VBox view) {
        int total = card.reminders().size();
        if (total == 0) {
            return;
        }
        long pending = card.pendingReminderCount();
        Label badge = new Label("\uD83D\uDD14 " + total);
        badge.getStyleClass().add(pending > 0 ? "card-reminders-pending" : "card-reminders");
        badge.setTooltip(new Tooltip(i18n.text("card.reminders.badge.tip", total, pending)));
        view.getChildren().add(badge);
    }

    /** \u26A0-style badge with the localized date; red styling when overdue. */
    private static void addDueBadgeIfPresent(I18n i18n, Card card, VBox view) {
        if (card.dueDate() == null) {
            return;
        }
        boolean overdue = card.isOverdueOn(LocalDate.now());
        Label badge = new Label("\u2691 " + card.dueDate().format(DUE_FORMAT));
        badge.getStyleClass().add(overdue ? "card-due-overdue" : "card-due");
        String tooltip = overdue
                ? i18n.text("card.due.overdue")
                : i18n.text("card.due");
        Tooltip.install(badge, new Tooltip(tooltip));
        view.getChildren().add(badge);
    }

    /**
     * Small colored chips per label, e.g. [work] [urgent], on a wrapping
     * pane. Color is the one the user chose for the label (T15 · B7) when a
     * rule exists, else the deterministic hash of the label — so a label with
     * no rule always looks the same everywhere.
     */
    private static void addLabelChipsIfPresent(I18n i18n, BoardController board, Card card,
                                               VBox view, boolean dark) {
        if (card.labels().isEmpty()) {
            return;
        }
        javafx.scene.layout.FlowPane chips = new javafx.scene.layout.FlowPane(4, 3);
        for (String label : card.labels()) {
            Label chip = new Label(label);
            chip.getStyleClass().add("card-label-chip");
            LabelChipStyle.Chip style =
                    LabelChipStyle.of(label, key -> board.labelChipColors(key, dark));
            if (style.styleClass() != null) {
                chip.getStyleClass().add(style.styleClass());
            }
            if (style.inlineStyle() != null && !style.inlineStyle().isEmpty()) {
                chip.setStyle(style.inlineStyle());
            }
            // Clicking a chip filters the board by that label; clicking the
            // same chip again clears the filter (user request).
            chip.setTooltip(new Tooltip(i18n.text("card.label.chip.tip")));
            chip.setCursor(javafx.scene.Cursor.HAND);
            chip.setOnMouseClicked(event -> {
                board.onFilterByLabel(label);
                event.consume();
            });
            chips.getChildren().add(chip);
        }
        view.getChildren().add(chips);
    }

    /**
     * Description area per the card's effective view mode: nothing in
     * TITLE_ONLY, the first {@link #PREVIEW_LINES} rows in TITLE_PREVIEW
     * (cut with an ellipsis), everything in FULL.
     * (Full tables/images only render in the markdown window; the card
     * front stays native text by design.)
     *
     * <p>Session 10 (F2): the preview also caps characters, because a
     * paragraph without line breaks wraps to many rows inside the 220px
     * front and would otherwise look exactly like the full mode.</p>
     */
    private static void addDescriptionByMode(VBox view, Card card, CardViewSettings.Mode mode) {
        if (card.description().isBlank() || mode == CardViewSettings.Mode.TITLE_ONLY) {
            return;
        }
        boolean full = mode == CardViewSettings.Mode.FULL;
        var summaryLines = MarkdownSummary.render(card.description(),
                full ? Integer.MAX_VALUE : PREVIEW_LINES,
                full ? Integer.MAX_VALUE : PREVIEW_MAX_CHARS);
        if (!summaryLines.isEmpty()) {
            javafx.scene.text.TextFlow summary = new javafx.scene.text.TextFlow(
                    summaryLines.toArray(new javafx.scene.text.Text[0]));
            summary.getStyleClass().add("card-description");
            summary.setPrefWidth(220);
            view.getChildren().add(summary);
        }
    }

    /**
     * Content menu of the card (user request N-14): one button per card to
     * choose what the column shows for it — the description (default), the
     * task list (checkable in place) or the notes. The glyph already tells
     * which one is on, and double-click still opens the whole detail window.
     */
    private static MenuButton contentButton(I18n i18n, BoardController board, Card card,
                                            CardViewSettings.Content content) {
        MenuButton button = new MenuButton(contentGlyph(content));
        button.getStyleClass().addAll("tool-button", "pk-card-content");
        button.setTooltip(new Tooltip(i18n.text("card.content.button.tip")));
        button.setFocusTraversable(false);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setMinHeight(Region.USE_PREF_SIZE);
        for (CardViewSettings.Content option : CardViewSettings.Content.values()) {
            RadioMenuItem item = new RadioMenuItem(
                    i18n.text(BoardController.cardContentTextKey(option)));
            item.setUserData(option);
            item.setSelected(option == content);
            item.setOnAction(e -> board.onSetCardContent(card.id(), option));
            button.getItems().add(item);
        }
        return button;
    }

    /** Glyph naming the content a card currently shows. */
    private static String contentGlyph(CardViewSettings.Content content) {
        return switch (content) {
            case CHECKLIST -> "\u2611";
            case NOTES -> "\uD83D\uDCDD";
            default -> "\u25A4";
        };
    }

    /**
     * The area under the title: whatever the card's content choice says.
     * Only DESCRIPTION follows the view mode; tasks and notes are shown
     * because the user asked for them on this card, in every mode.
     */
    private static void addContentArea(BoardController board, I18n i18n, Card card,
                                       CardViewSettings.Mode mode,
                                       CardViewSettings.Content content, VBox view) {
        switch (content) {
            case CHECKLIST -> addChecklistIfPresent(board, card, view);
            case NOTES -> addNotesPreviewIfPresent(i18n, card, view);
            default -> addDescriptionByMode(view, card, mode);
        }
    }

    /**
     * The card's tasks, checkable without opening the detail window
     * (user request N-14: «tareas (revisar/marcar)»). One click is one
     * undoable domain transaction, like the checklist tab's checkboxes.
     */
    private static void addChecklistIfPresent(BoardController board, Card card, VBox view) {
        if (card.checklist().isEmpty()) {
            return;
        }
        VBox box = new VBox(2);
        box.getStyleClass().add("card-checklist-preview");
        for (ChecklistItem item : card.checklist()) {
            CheckBox done = new CheckBox(item.text());
            done.setSelected(item.done());
            done.setWrapText(true);
            done.getStyleClass().add("checklist-item");
            done.setFocusTraversable(false);
            done.setOnAction(e -> board.onChecklistToggle(card.id(), item.id(), done.isSelected()));
            box.getChildren().add(done);
        }
        view.getChildren().add(box);
    }

    /**
     * The card's notes as a short digest (user request N-14, «comentarios» =
     * notas): each note's title plus the beginning of its body. The whole
     * text is still one double-click away, and the notes button opens the
     * editor.
     */
    private static void addNotesPreviewIfPresent(I18n i18n, Card card, VBox view) {
        CardNotes notes = CardNotes.parse(card.notes());
        if (notes.isEmpty()) {
            return;
        }
        VBox box = new VBox(2);
        box.getStyleClass().add("card-notes-preview");
        for (CardNotes.Note note : notes.notes()) {
            String title = note.title().isEmpty()
                    ? i18n.text("card.notes.untitled") : note.title();
            String body = abbreviate(note.body(), NOTES_PREVIEW_MAX_CHARS);
            Label text = new Label(body.isEmpty() ? title : title + "\n" + body);
            text.setWrapText(true);
            text.setPrefWidth(220);
            text.getStyleClass().add("card-notes-preview-text");
            box.getChildren().add(text);
        }
        view.getChildren().add(box);
    }

    /** Cuts text at a character bound, marking the cut with an ellipsis. */
    private static String abbreviate(String text, int maxChars) {
        String flat = text == null ? "" : text.strip();
        if (flat.length() <= maxChars) {
            return flat;
        }
        int cut = flat.lastIndexOf(' ', maxChars);
        return flat.substring(0, cut < maxChars / 2 ? maxChars : cut).strip() + "\u2026";
    }

    /**
     * Quick-flag toggle (★/!): dimmed when the card lacks the label, vivid
     * when set. One click = one undoable domain transaction.
     *
     * <p>The tooltip shows the TRANSLATED name (session 13: it used to show
     * the domain label itself — "Importante" / "Urgente" — in every language,
     * because the {@code tipKey} argument was never read).</p>
     */
    private static Button flagButton(I18n i18n, BoardController board, Card card, String label,
                                     String glyph, String tipKey, String styleClass) {
        Button button = new Button(glyph);
        button.getStyleClass().addAll("card-flag", styleClass);
        if (card.hasLabelIgnoreCase(label)) {
            button.getStyleClass().add("card-flag-on");
        } else {
            button.getStyleClass().add("card-flag-off");
        }
        button.setTooltip(new Tooltip(i18n.text(tipKey)));
        button.setFocusTraversable(false);
        // Narrow columns must never squeeze these into ellipsis/garbled glyphs.
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setMinHeight(Region.USE_PREF_SIZE);
        button.setOnAction(e -> board.onToggleCardLabel(card.id(), label));
        return button;
    }

    /**
     * Notes button (user request): sits next to ★/!, dimmed when the card
     * has no notes, vivid when it does; always opens the detail window.
     */
    private static Button notesButton(I18n i18n, BoardController board, Card card) {
        Button button = new Button("\uD83D\uDCDD");
        button.getStyleClass().add(card.notes().isBlank()
                ? "card-flag-off" : "card-flag-on");
        button.setTooltip(new Tooltip(i18n.text("card.notes.button.tip")));
        button.setFocusTraversable(false);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setMinHeight(Region.USE_PREF_SIZE);
        button.setOnAction(e -> board.onOpenCardNotes(card.id()));
        return button;
    }

    /**
     * Opens the card's attachments window (reference files in the program's
     * data folder); vivid once the folder holds at least one file.
     */
    private static Button attachmentsButton(I18n i18n, BoardController board, Card card,
                                            int count) {
        Button button = new Button("\uD83D\uDCCE");
        button.getStyleClass().add(count == 0 ? "card-flag-off" : "card-flag-on");
        button.setTooltip(new Tooltip(count == 0
                ? i18n.text("card.attachments.button.tip")
                : i18n.text("card.attachments.badge.tip", count)));
        button.setFocusTraversable(false);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setMinHeight(Region.USE_PREF_SIZE);
        button.setOnAction(e -> board.onOpenCardAttachments(card.id()));
        return button;
    }

    private static Button cardButton(I18n i18n, String glyph, String tipKey,
                                     BoardController board, CardId cardId, boolean isEdit) {
        Button button = new Button(glyph);
        button.getStyleClass().addAll("tool-button");
        button.setTooltip(new Tooltip(i18n.text(tipKey)));
        button.setOnAction(e -> {
            if (isEdit) {
                board.onEditCard(cardId);
            } else {
                board.onRemoveCard(cardId);
            }
        });
        return button;
    }

    private static void installDragSource(VBox view, CardId cardId) {
        view.setOnDragDetected(event -> {
            Dragboard dragboard = view.startDragAndDrop(TransferMode.MOVE);
            dragboard.setContent(Map.of(ColumnViewBuilder.CARD_FORMAT, cardId.value()));
            event.consume();
        });
    }
}
