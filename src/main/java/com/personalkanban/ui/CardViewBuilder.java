package com.personalkanban.ui;

import com.personalkanban.application.BoardService;
import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.CardId;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Map;

/**
 * Builds one card UI (Pure Fabrication): title, optional description, edit and
 * delete affordances, and drag-source behavior for cross-column moves.
 */
final class CardViewBuilder {

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

        HBox actions = new HBox(4,
                cardButton(i18n, "\u270E", "pk-edit-card", "card.edit", board, card.id(), true),
                cardButton(i18n, "\u2715", "pk-delete-card", "card.delete", board, card.id(), false));
        actions.setAlignment(Pos.CENTER_RIGHT);
        view.getChildren().add(actions);

        installDragSource(view, card.id());
        return view;
    }

    private static Button cardButton(I18n i18n, String glyph, String styleClass, String tipKey,
                                     BoardController board, CardId cardId, boolean isEdit) {
        Button button = new Button(glyph);
        button.getStyleClass().addAll("tool-button", styleClass);
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
