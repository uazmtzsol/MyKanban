package com.personalkanban.ui;

import com.personalkanban.application.BoardService;
import com.personalkanban.application.CardViewSettings;
import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.ui.markdown.MarkdownSummary;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Map;

/**
 * Builds one card UI (Pure Fabrication): title, optional description,
 * due-date badge (red when overdue), label chips, edit/delete affordances,
 * and drag-source behavior for cross-column moves.
 */
final class CardViewBuilder {

    private static final DateTimeFormatter DUE_FORMAT =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

    private CardViewBuilder() {
    }

    static VBox build(BoardService service, I18n i18n, Dialogs dialogs, BoardController board,
                      Card card, boolean selectionMode, boolean dark) {
        var settings = board.currentCardViewSettings();
        var mode = settings.effectiveMode(card.id().value());

        Label title = new Label(card.title());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        VBox view = new VBox(4);
        view.getStyleClass().addAll("card", ColorCss.styleClass(card.color()));
        ColorCss.applySurface(view, card.color(), dark);
        // Quick-flag highlight classes (user request: (i)/(u)/(iu) must stand
        // out); CSS owns the look per theme.
        if (card.isUrgentAndImportantCard()) {
            view.getStyleClass().add("card-priority-urgent-important");
        } else if (card.isUrgentOnlyCard()) {
            view.getStyleClass().add("card-priority-urgent");
        } else if (card.isImportantOnlyCard()) {
            view.getStyleClass().add("card-priority-important");
        }
        if (board.isCardSelected(card.id())) {
            view.getStyleClass().add("card-selected");
        }

        // --- Title row: title + quick flags (★ Importante, ! Urgente) ---
        HBox titleRow = new HBox(6);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, javafx.scene.layout.Priority.ALWAYS);
        titleRow.getChildren().add(title);
        if (!selectionMode) {
            titleRow.getChildren().add(flagButton(board, card, Card.LABEL_IMPORTANT,
                    "\u2605", "card.flag.important", "card-flag-important"));
            titleRow.getChildren().add(flagButton(board, card, Card.LABEL_URGENT,
                    "!", "card.flag.urgent", "card-flag-urgent"));
            titleRow.getChildren().add(notesButton(i18n, board, card));
        }
        view.getChildren().add(titleRow);

        addDescriptionByMode(view, card, mode);

        addDueBadgeIfPresent(i18n, card, view);
        addChecklistProgressIfPresent(i18n, card, view);
        addNotesIndicatorIfPresent(i18n, card, view);
        addProcessChipIfPresent(board, card, view);
        addRelationBadgesIfPresent(i18n, board, card, view);
        addLabelChipsIfPresent(card, view);

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
            view.setOnContextMenuRequested(event -> {
                CardViewSettings.Mode effective = settings.effectiveMode(card.id().value());
                for (javafx.scene.control.MenuItem item : viewMenu.getItems()) {
                    if (item instanceof javafx.scene.control.RadioMenuItem radio) {
                        radio.setSelected(radio.getUserData() == effective);
                    } else if (item instanceof javafx.scene.control.MenuItem) {
                        item.setVisible(settings.hasOverride(card.id().value()));
                    }
                }
                viewMenu.show(view, event.getScreenX(), event.getScreenY());
                event.consume();
            });
        }

        installDragSource(view, card.id());
        return view;
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

    /** "\uD83D\uDCDD Notes" hint when the card has plain-text notes. */
    private static void addNotesIndicatorIfPresent(I18n i18n, Card card, VBox view) {
        if (card.notes().isBlank()) {
            return;
        }
        Label badge = new Label("\uD83D\uDCDD");
        badge.getStyleClass().add("card-notes-indicator");
        String notes = card.notes();
        String extract = notes.length() > 120 ? notes.substring(0, 120) + "\u2026" : notes;
        badge.setTooltip(new Tooltip(extract));
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
     * pane. Color is deterministic (hash of the label) over the chip
     * palette, so the same label always looks the same everywhere.
     */
    private static void addLabelChipsIfPresent(Card card, VBox view) {
        if (card.labels().isEmpty()) {
            return;
        }
        javafx.scene.layout.FlowPane chips = new javafx.scene.layout.FlowPane(4, 3);
        for (String label : card.labels()) {
            Label chip = new Label(label);
            chip.getStyleClass().addAll("card-label-chip", chipColorClass(label));
            chips.getChildren().add(chip);
        }
        view.getChildren().add(chips);
    }

    /** Deterministic chip color class: label hash → one of 8 palette slots. */
    private static String chipColorClass(String label) {
        int index = Math.abs(label.toLowerCase(java.util.Locale.ROOT).hashCode()) % 8;
        return "chip-color-" + index;
    }

    /**
     * Description area per the card's effective view mode: nothing in
     * TITLE_ONLY, the first 3 lines in TITLE_PREVIEW, everything in FULL.
     * (Full tables/images only render in the markdown window; the card
     * front stays native text by design.)
     */
    private static void addDescriptionByMode(VBox view, Card card, CardViewSettings.Mode mode) {
        if (card.description().isBlank() || mode == CardViewSettings.Mode.TITLE_ONLY) {
            return;
        }
        int maxLines = mode == CardViewSettings.Mode.FULL ? Integer.MAX_VALUE : 3;
        var summaryLines = MarkdownSummary.render(card.description(), maxLines);
        if (!summaryLines.isEmpty()) {
            javafx.scene.text.TextFlow summary = new javafx.scene.text.TextFlow(
                    summaryLines.toArray(new javafx.scene.text.Text[0]));
            summary.getStyleClass().add("card-description");
            summary.setPrefWidth(220);
            view.getChildren().add(summary);
        }
    }

    /**
     * Quick-flag toggle (★/!): dimmed when the card lacks the label, vivid
     * when set. One click = one undoable domain transaction.
     */
    private static Button flagButton(BoardController board, Card card, String label,
                                     String glyph, String tipKey, String styleClass) {
        Button button = new Button(glyph);
        button.getStyleClass().addAll("card-flag", styleClass);
        if (card.hasLabelIgnoreCase(label)) {
            button.getStyleClass().add("card-flag-on");
        } else {
            button.getStyleClass().add("card-flag-off");
        }
        button.setTooltip(new Tooltip(label));
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
