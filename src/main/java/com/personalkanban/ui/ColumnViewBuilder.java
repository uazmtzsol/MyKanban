package com.personalkanban.ui;

import com.personalkanban.application.BoardService;
import com.personalkanban.domain.board.BoardColumn;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.LabelFilter;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.DataFormat;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

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
                               BoardController board, UndoRedoController undoRedo,
                               LabelFilter labelFilter, LabelFilter quickFilter, boolean selectionMode,
                               java.util.Set<String> collapsedIds, boolean dark,
                               java.util.Map<String, Integer> columnWidths) {
        LabelFilter effective = labelFilter == null ? LabelFilter.none() : labelFilter;
        LabelFilter effectiveQuick = quickFilter == null ? LabelFilter.none() : quickFilter;
        List<VBox> views = new ArrayList<>();
        java.util.List<VBox> all = new ArrayList<>();
        for (BoardColumn column : service.board().columns()) {
            boolean collapsed = collapsedIds.contains(column.id().value());
            VBox view = buildOne(service, i18n, dialogs, board, undoRedo, column,
                    effective, effectiveQuick, selectionMode, collapsed, dark);
            Integer width = columnWidths.get(column.id().value());
            if (width != null && !collapsed) {
                view.setPrefWidth(Math.clamp(width, 200, 800));
            }
            all.add(view);
        }
        // Resize handles BETWEEN columns (drag the gap to change the left one's width),
        // plus one AFTER the last column so it is resizable too.
        for (int i = 0; i < all.size() - 1; i++) {
            VBox left = all.get(i);
            ColumnId leftId = (ColumnId) left.getUserData();
            views.add(left);
            views.add(resizeHandle(left, leftId.value(), board));
        }
        if (!all.isEmpty()) {
            VBox last = all.getLast();
            ColumnId lastId = (ColumnId) last.getUserData();
            views.add(last);
            views.add(resizeHandle(last, lastId.value(), board));
        }
        return views;
    }

    /**
     * A slim gap between two columns; dragging it resizes the LEFT column
     * live (min 120, max 800 px). Release persists the width per board.
     */
    private static VBox resizeHandle(VBox leftColumn, String leftColumnId, BoardController board) {
        VBox handle = new VBox();
        handle.getStyleClass().add("column-resize-handle");
        handle.setPrefWidth(10);
        handle.setMinWidth(10);
        handle.setMaxWidth(10);
        double[] startX = new double[1];
        double[] startWidth = new double[1];
        handle.setOnMousePressed(event -> {
            startX[0] = event.getScreenX();
            startWidth[0] = leftColumn.getWidth();
            handle.getStyleClass().add("column-resize-active");
            event.consume();
        });
        handle.setOnMouseDragged(event -> {
            double delta = event.getScreenX() - startX[0];
            double newWidth = Math.clamp(startWidth[0] + delta, 120, 800);
            leftColumn.setPrefWidth(newWidth);
            event.consume();
        });
        handle.setOnMouseReleased(event -> {
            handle.getStyleClass().remove("column-resize-active");
            board.onColumnWidthChanged(leftColumnId, (int) Math.round(leftColumn.getPrefWidth()));
            event.consume();
        });
        return handle;
    }

    private static VBox buildOne(BoardService service, I18n i18n, Dialogs dialogs,
                                 BoardController board, UndoRedoController undoRedo,
                                 BoardColumn column, LabelFilter labelFilter, LabelFilter quickFilter,
                                 boolean selectionMode, boolean collapsed, boolean dark) {
        if (collapsed) {
            return buildCollapsed(service, i18n, dialogs, board, column, dark);
        }

        // --- Header: title + wip caption + selection + collapse + menu ---
        Label title = new Label(column.title());
        title.getStyleClass().add("column-title");

        Label wip = new Label(wipText(column));
        wip.getStyleClass().add("column-wip");
        wip.getStyleClass().add(column.isFull() ? "wip-full" : "wip-ok");

        Button addCard = iconButton("\uFF0B", "pk-add-card", i18n.text("column.add.card"));
        addCard.setOnAction(e -> board.onAddCard(column.id()));

        // Priority sort (user request): (★+!) to the top, then (!), then (★);
        // everything else keeps its order. One undoable step.
        Button sortPriority = iconButton("\u2193\u2605", "pk-sort-priority", i18n.text("column.sort.priority"));
        sortPriority.setOnAction(e -> board.onSortColumnByPriority(column.id()));

        Button selectCards = iconButton("\u2610", "pk-select-cards", i18n.text("column.select.cards"));
        selectCards.setOnAction(e -> board.onToggleSelectionMode(column.id()));

        Button collapse = iconButton("\u00AB", "pk-collapse", i18n.text("column.collapse"));
        collapse.setOnAction(e -> board.onToggleColumnCollapsed(column.id()));

        MenuButton columnMenu = menuButton("\u22EF", i18n.text("column.menu"));
        MenuItem editItem = new MenuItem(i18n.text("column.edit"));
        editItem.setOnAction(e -> board.onEditColumn(column.id()));
        MenuItem collapseItem = new MenuItem(i18n.text("column.collapse"));
        collapseItem.setOnAction(e -> board.onToggleColumnCollapsed(column.id()));
        MenuItem deleteItem = new MenuItem(i18n.text("column.delete"));
        deleteItem.setOnAction(e -> board.onRemoveColumn(column.id()));
        MenuItem clearItem = new MenuItem(i18n.text("column.clear.cards"));
        clearItem.setOnAction(e -> board.onClearColumn(column.id()));
        columnMenu.getItems().setAll(editItem, collapseItem, deleteItem,
                new SeparatorMenuItem(), clearItem);

        HBox header = new HBox(6, title, wip, addCard, sortPriority, selectCards, collapse, columnMenu);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        header.getStyleClass().add("column-header");

        // --- Description tooltip ---
        if (!column.description().isBlank()) {
            Tooltip.install(title, new Tooltip(column.description()));
        }

        VBox view = new VBox(8);
        view.getStyleClass().addAll("column", ColorCss.styleClass(column.color()));
        ColorCss.applySurface(view, column.color(), dark);
        view.setUserData(column.id());
        view.setMinWidth(240);
        view.setPrefWidth(280);
        view.getChildren().add(header);

        // --- Cards (label/flag/process-filtered; WIP badge keeps counting
        //     everything). Every card carries its own TOP drop edge (splits
        //     the card: above = its index, below = index+1), plus a trailing
        //     slot after the last card, so a drop can land exactly where the
        //     indicator showed — including "above the first card" (user
        //     request: the top of the column must be droppable). ---
        VBox cardsBox = new VBox(0);
        int cardIndex = 0;
        for (var card : column.cards()) {
            if (labelFilter.matches(card) && quickFilter.matches(card)
                    && board.matchesProcessFilter(card)) {
                VBox cardView = CardViewBuilder.build(
                        service, i18n, dialogs, board, card, selectionMode, dark);
                installTopDropEdge(cardView, column.id(), cardIndex, board);
                cardsBox.getChildren().add(cardView);
                cardsBox.getChildren().add(slotRegion(column.id(), cardIndex + 1, board));
                cardIndex++;
            }
        }
        ScrollPane cardsScroll = new ScrollPane(cardsBox);
        cardsScroll.setFitToWidth(true);
        cardsScroll.getStyleClass().add("cards-scroll");
        VBox.setVgrow(cardsScroll, javafx.scene.layout.Priority.ALWAYS);
        view.getChildren().add(cardsScroll);

        // Drop target = the WHOLE cards body (scroll included), so an EMPTY
        // column is droppable too: the content box alone is zero-sized when
        // empty, and drops over it used to be silently rejected.
        installCardDropTarget(cardsScroll, column.id(), service, board, Integer.MAX_VALUE);
        installColumnDragSource(view, column.id(), service, board);
        return view;
    }

    /**
     * Collapsed column: a narrow strip with the vertical title (Trello-like),
     * the card count, and the expand button — enough to identify the column
     * without letting its content distract.
     */
    private static VBox buildCollapsed(BoardService service, I18n i18n, Dialogs dialogs,
                                       BoardController board, BoardColumn column, boolean dark) {
        Button expand = iconButton("\u00BB", "pk-expand", i18n.text("column.expand"));
        expand.setOnAction(e -> board.onToggleColumnCollapsed(column.id()));
        expand.setTooltip(new Tooltip(i18n.text("column.expand")));

        Label verticalTitle = new Label(column.title());
        verticalTitle.getStyleClass().add("column-title-vertical");
        verticalTitle.setRotate(-90);
        if (!column.description().isBlank()) {
            Tooltip.install(verticalTitle, new Tooltip(column.description()));
        }
        javafx.scene.Group verticalText = new javafx.scene.Group(verticalTitle);
        VBox.setVgrow(verticalText, javafx.scene.layout.Priority.ALWAYS);

        Label count = new Label(wipText(column));
        count.getStyleClass().add("column-wip");
        count.getStyleClass().add(column.isFull() ? "wip-full" : "wip-ok");

        MenuButton columnMenu = menuButton("\u22EF", i18n.text("column.menu"));
        MenuItem expandItem = new MenuItem(i18n.text("column.expand"));
        expandItem.setOnAction(e -> board.onToggleColumnCollapsed(column.id()));
        MenuItem editItem = new MenuItem(i18n.text("column.edit"));
        editItem.setOnAction(e -> board.onEditColumn(column.id()));
        columnMenu.getItems().setAll(expandItem, editItem);

        VBox view = new VBox(6, expand, columnMenu, verticalText, count);
        view.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        view.getStyleClass().addAll("column", "collapsed", ColorCss.styleClass(column.color()));
        ColorCss.applySurface(view, column.color(), dark);
        view.setUserData(column.id());
        view.setMinWidth(52);
        view.setPrefWidth(52);
        view.setMaxWidth(52);

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

    /** The whole cards area (scroll included) accepts card drops: append at the end. */
    private static void installCardDropTarget(javafx.scene.Node dropArea, ColumnId columnId,
                                              BoardService service, BoardController board, int slotIndex) {
        dropArea.setOnDragOver(event -> {
            if (event.getDragboard().hasContent(CARD_FORMAT)) {
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            }
        });
        dropArea.setOnDragDropped(event -> {
            var dragboard = event.getDragboard();
            if (dragboard.hasContent(CARD_FORMAT)) {
                CardId dragged = new CardId((String) dragboard.getContent(CARD_FORMAT));
                board.onMoveCard(dragged, columnId, slotIndex);
                event.setDropCompleted(true);
                event.consume();
            }
        });
    }

    /**
     * Whole-card drop zones (user request "soltar ARRIBA"): the upper ~40%
     * of a card drops ABOVE it (index N) and the rest drops BELOW it
     * (index N+1) — exactly what the tiny slots did, but now the whole
     * card gives feedback with a top or bottom insertion line. The
     * trailing slot after the last card still handles append-at-end.
     */
    private static void installTopDropEdge(javafx.scene.Node cardView, ColumnId columnId,
                                           int cardIndex, BoardController board) {
        cardView.setOnDragOver(event -> {
            if (event.getDragboard().hasContent(CARD_FORMAT)) {
                boolean above = event.getY() <= cardView.getBoundsInLocal().getHeight() * 0.4;
                String activeClass = above ? "drop-top-active" : "drop-bottom-active";
                if (!cardView.getStyleClass().contains(activeClass)) {
                    cardView.getStyleClass().removeAll("drop-top-active", "drop-bottom-active");
                    cardView.getStyleClass().add(activeClass);
                }
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            }
        });
        cardView.setOnDragExited(event -> {
            cardView.getStyleClass().removeAll("drop-top-active", "drop-bottom-active");
            event.consume();
        });
        cardView.setOnDragDropped(event -> {
            cardView.getStyleClass().removeAll("drop-top-active", "drop-bottom-active");
            var dragboard = event.getDragboard();
            if (dragboard.hasContent(CARD_FORMAT)) {
                boolean above = event.getY() <= cardView.getBoundsInLocal().getHeight() * 0.4;
                int slot = above ? cardIndex : cardIndex + 1;
                CardId dragged = new CardId((String) dragboard.getContent(CARD_FORMAT));
                board.onMoveCardToSlot(dragged, columnId, slot);
                event.setDropCompleted(true);
                event.consume();
            }
        });
    }

    /**
     * An invisible gap after each card; during a card drag it grows into a
     * visible insertion line. Dropping on slot N places the card between
     * neighbors exactly as the indicator showed.
     */
    private static Region slotRegion(ColumnId columnId, int slotIndex, BoardController board) {
        Region slot = new Region();
        slot.getStyleClass().add("drop-slot");
        slot.setPrefHeight(8);
        slot.setMinHeight(4);
        slot.setOnDragOver(event -> {
            if (event.getDragboard().hasContent(CARD_FORMAT)) {
                if (!slot.getStyleClass().contains("drop-slot-active")) {
                    slot.getStyleClass().add("drop-slot-active");
                }
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            }
        });
        slot.setOnDragExited(event -> {
            slot.getStyleClass().remove("drop-slot-active");
            event.consume();
        });
        slot.setOnDragDropped(event -> {
            slot.getStyleClass().remove("drop-slot-active");
            var dragboard = event.getDragboard();
            if (dragboard.hasContent(CARD_FORMAT)) {
                CardId dragged = new CardId((String) dragboard.getContent(CARD_FORMAT));
                board.onMoveCardToSlot(dragged, columnId, slotIndex);
                event.setDropCompleted(true);
                event.consume();
            }
        });
        return slot;
    }
}
