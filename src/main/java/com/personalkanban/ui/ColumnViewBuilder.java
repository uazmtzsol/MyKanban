package com.personalkanban.ui;

import com.personalkanban.application.BoardService;
import com.personalkanban.domain.board.BoardColumn;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ColumnId;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.DataFormat;
import javafx.scene.input.TransferMode;

import java.util.Map;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stateless builder of column UIs (Pure Fabrication). Displays the column
 * header with WIP caption, the card list, and the column menu (edit, delete,
 * clear cards). Every action delegates to BoardService via the board
 * controller's intents.
 */
final class ColumnViewBuilder {

    static final DataFormat COLUMN_FORMAT = new DataFormat("application/x-pk-column");
    static final DataFormat CARD_FORMAT = new DataFormat("application/x-pk-card");

    private ColumnViewBuilder() {
    }

    static List<VBox> buildAll(BoardService service, I18n i18n, Dialogs dialogs,
                               BoardController board, UndoRedoController undoRedo) {
        List<VBox> views = new ArrayList<>();
        for (BoardColumn column : service.board().columns()) {
            views.add(buildOne(service, i18n, dialogs, board, undoRedo, column));
        }
        return views;
    }

    private static VBox buildOne(BoardService service, I18n i18n, Dialogs dialogs,
                                 BoardController board, UndoRedoController undoRedo,
                                 BoardColumn column) {
        // --- Header: title + wip caption + menu ---
        Label title = new Label(column.title());
        title.getStyleClass().add("column-title");

        Label wip = new Label(wipText(column));
        wip.getStyleClass().add("column-wip");
        wip.getStyleClass().add(column.isFull() ? "wip-full" : "wip-ok");

        Button addCard = iconButton("\uFF0B", "pk-add-card", i18n.text("column.add.card"));
        addCard.setOnAction(e -> board.onAddCard(column.id()));

        Button clearCards = iconButton("\uD83E\uDDF9", "pk-clear-cards", i18n.text("column.clear.cards"));
        clearCards.setOnAction(e -> board.onClearColumn(column.id()));

        MenuButton columnMenu = menuButton("\u22EF", i18n.text("column.menu"));
        MenuItem editItem = new MenuItem(i18n.text("column.edit"));
        editItem.setOnAction(e -> board.onEditColumn(column.id()));
        MenuItem deleteItem = new MenuItem(i18n.text("column.delete"));
        deleteItem.setOnAction(e -> board.onRemoveColumn(column.id()));
        columnMenu.getItems().setAll(editItem, deleteItem, new SeparatorMenuItem());

        HBox header = new HBox(6, title, wip, addCard, clearCards, columnMenu);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        header.getStyleClass().add("column-header");

        // --- Description tooltip ---
        if (!column.description().isBlank()) {
            Tooltip.install(title, new Tooltip(column.description()));
        }

        VBox view = new VBox(8);
        view.getStyleClass().addAll("column", ColorCss.styleClass(column.color()));
        view.setUserData(column.id());
        view.setMinWidth(240);
        view.setPrefWidth(280);
        view.getChildren().add(header);

        // --- Cards ---
        VBox cardsBox = new VBox(8);
        for (var card : column.cards()) {
            cardsBox.getChildren().add(CardViewBuilder.build(service, i18n, dialogs, board, card));
        }
        ScrollPane cardsScroll = new ScrollPane(cardsBox);
        cardsScroll.setFitToWidth(true);
        cardsScroll.getStyleClass().add("cards-scroll");
        VBox.setVgrow(cardsScroll, javafx.scene.layout.Priority.ALWAYS);
        view.getChildren().add(cardsScroll);

        installCardDropTarget(cardsBox, column.id(), service, board);
        installColumnDragSource(view, column.id(), service, board);
        return view;
    }

    static String wipText(BoardColumn column) {
        if (column.wipLimit().isUnlimited()) {
            return String.valueOf(column.cardCount());
        }
        return column.wipLimit().describe(column.cardCount());
    }

    private static Button iconButton(String glyph, String styleClass, String tipKey) {
        Button button = new Button(glyph);
        button.getStyleClass().addAll("tool-button", styleClass);
        button.setTooltip(new Tooltip(tipKey));
        return button;
    }

    private static MenuButton menuButton(String glyph, String tipKey) {
        MenuButton menuButton = new MenuButton(glyph);
        menuButton.getStyleClass().addAll("tool-button", "pk-column-menu");
        menuButton.setTooltip(new Tooltip(tipKey));
        return menuButton;
    }

    // ------------------------------------------------------------------
    // Drag & drop
    // ------------------------------------------------------------------

    /** Columns are drag sources; dropping one on another places it before the target. */
    private static void installColumnDragSource(VBox view, ColumnId columnId,
                                                BoardService service, BoardController board) {
        view.setOnDragDetected(event -> {
            var dragboard = view.startDragAndDrop(TransferMode.MOVE);
            dragboard.setContent(Map.of(COLUMN_FORMAT, columnId.value()));
            dragboard.setDragView(view.snapshot(null, null));
            event.consume();
        });
        view.setOnDragDone(javafx.event.Event::consume);

        view.setOnDragOver(event -> {
            if (event.getDragboard().hasContent(COLUMN_FORMAT)) {
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            }
        });
        view.setOnDragDropped(event -> {
            var dragboard = event.getDragboard();
            if (dragboard.hasContent(COLUMN_FORMAT)) {
                ColumnId dragged = new ColumnId((String) dragboard.getContent(COLUMN_FORMAT));
                if (!dragged.equals(columnId)) {
                    int targetIndex = service.board().columns().stream()
                            .map(BoardColumn::id).toList().indexOf(columnId);
                    board.onMoveColumn(dragged, targetIndex);
                }
                event.setDropCompleted(true);
                event.consume();
            }
        });
    }

    /** The cards list accepts cards from any column; drops append at the end. */
    private static void installCardDropTarget(VBox cardsBox, ColumnId columnId,
                                              BoardService service, BoardController board) {
        cardsBox.setOnDragOver(event -> {
            if (event.getDragboard().hasContent(CARD_FORMAT)) {
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            }
        });
        cardsBox.setOnDragDropped(event -> {
            var dragboard = event.getDragboard();
            if (dragboard.hasContent(CARD_FORMAT)) {
                CardId dragged = new CardId((String) dragboard.getContent(CARD_FORMAT));
                board.onMoveCard(dragged, columnId, Integer.MAX_VALUE);
                event.setDropCompleted(true);
                event.consume();
            }
        });
    }
}
