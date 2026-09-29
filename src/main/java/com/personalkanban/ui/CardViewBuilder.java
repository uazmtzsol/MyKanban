package com.personalkanban.ui;

import com.personalkanban.application.BoardService;
import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.CardId;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
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

    static VBox build(BoardService service, I18n i18n, Dialogs dialogs, BoardController board, Card card) {
        Label title = new Label(card.title());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        VBox view = new VBox(4, title);
        view.getStyleClass().addAll("card", ColorCss.styleClass(card.color()));

        if (!card.description().isBlank()) {
            Label description = new Label(card.description());
            description.getStyleClass().add("card-description");
            description.setWrapText(true);
            view.getChildren().add(description);
        }

        addDueBadgeIfPresent(i18n, card, view);
        addLabelChipsIfPresent(card, view);

        HBox actions = new HBox(4,
                cardButton(i18n, "\u270E", "card.edit", board, card.id(), true),
                cardButton(i18n, "\u2715", "card.delete", board, card.id(), false));
        actions.setAlignment(Pos.CENTER_RIGHT);
        view.getChildren().add(actions);

        installDragSource(view, card.id());
        return view;
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

    /** Small chips per label, e.g. [work] [urgent]. */
    private static void addLabelChipsIfPresent(Card card, VBox view) {
        if (card.labels().isEmpty()) {
            return;
        }
        HBox chips = new HBox(4);
        for (String label : card.labels()) {
            Label chip = new Label(label);
            chip.getStyleClass().add("card-label-chip");
            chips.getChildren().add(chip);
        }
        view.getChildren().add(chips);
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
