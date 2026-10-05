package com.personalkanban.ui;

import com.personalkanban.application.BoardService;
import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.Process;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.Line;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Builds the "processes" view: no columns, one row per process.
 * The cards of a process are drawn like a small diagram, the way a
 * drawing program (Dia, drawio) shows a dependency graph: a card
 * sits one column to the right of its deepest predecessor, so the
 * roots line up on the left, a card with several predecessors has
 * them stacked on its left, and a card with several successors fans
 * out to its right. Links are drawn as smooth bezier connectors
 * with real arrowheads; the per-card arrow buttons are drawn vector
 * shapes too, not font glyphs.
 *
 * <p>A process whose cards are not linked yet falls back to a plain
 * row with a dotted arrow between consecutive cards, so the suggested
 * order can be linked up by clicking.</p>
 */
final class ProcessViewBuilder {

    /** Fixed card cell of the process graph canvas. */
    private static final double CARD_W = 180;
    private static final double CARD_H = 96;
    private static final double H_GAP = 72;
    private static final double V_GAP = 28;
    private static final double PAD = 12;

    private ProcessViewBuilder() {
    }

    static Node buildAll(BoardService service, I18n i18n, BoardController controller) {
        VBox rows = new VBox(18);
        rows.setPadding(new Insets(14));

        List<Process> processes = service.processes();
        long unassigned = service.board().allCards().stream()
                .filter(card -> card.processId() == null).count();
        if (processes.isEmpty() && unassigned == 0) {
            Label empty = new Label(i18n.text("process.view.empty"));
            empty.getStyleClass().add("detail-caption");
            rows.getChildren().add(empty);
            return rows;
        }

        for (Process process : processes) {
            rows.getChildren().add(buildRow(service, i18n, controller, process));
        }
        if (unassigned > 0) {
            rows.getChildren().add(buildUnassignedNote(i18n, unassigned));
        }
        return rows;
    }

    private static VBox buildRow(BoardService service, I18n i18n,
                                 BoardController controller, Process process) {
        String name = process.name() == null ? "" : process.name();
        Label header = new Label(name.startsWith("#") ? name : "#" + name);
        header.getStyleClass().add("process-header");

        List<Card> members = service.board().cardsOfProcess(process.id());
        List<CardId> ids = members.stream().map(Card::id).toList();
        var order = service.suggestedOrder(ids);
        List<CardId> ordered = new ArrayList<>(order.ordered());
        ordered.addAll(order.cycleRemaining());

        Layout layout = computeLayout(service, ids, ordered);
        Node body = layout.hasEdges()
                ? graphCanvas(service, i18n, controller, layout)
                : linearFlow(service, i18n, controller, ordered);

        ScrollPane scroller = new ScrollPane(body);
        scroller.setFitToHeight(false);
        scroller.setFitToWidth(false);
        scroller.setPrefHeight(200);
        scroller.setPannable(true);
        scroller.getStyleClass().add("process-row-scroll");

        VBox row = new VBox(4, header, scroller);
        row.getStyleClass().add("process-row");
        return row;
    }

    // ------------------------------------------------------------------
    // Layout: cards in columns by precedence depth
    // ------------------------------------------------------------------

    /** One card's cell: top-left corner on the canvas. */
    private record Cell(double x, double y) { }

    /** The computed graph layout of one process's cards. */
    private record Layout(List<CardId> ordered,
                          Map<CardId, Integer> layer,
                          Map<CardId, List<CardId>> successors,
                          Map<CardId, Cell> cells,
                          double width, double height,
                          int edges) {
        boolean hasEdges() {
            return edges > 0;
        }
    }

    /**
     * Lays the process's cards out on a fixed grid. Columns are
     * precedence depth (longest path from a root), rows are the
     * position inside the column. Links only ever point right, so
     * the diagram reads left to right like a flow chart.
     */
    private static Layout computeLayout(BoardService service, List<CardId> ids,
                                        List<CardId> ordered) {
        Set<CardId> members = new HashSet<>(ids);
        Map<CardId, List<CardId>> predecessors = new HashMap<>();
        Map<CardId, List<CardId>> successors = new HashMap<>();
        int edges = 0;
        for (CardId id : ids) {
            predecessors.put(id, new ArrayList<>());
            successors.put(id, new ArrayList<>());
        }
        for (CardId from : ids) {
            for (CardId to : service.board().outgoingSuccessorsOf(from)) {
                if (members.contains(to)) {
                    successors.get(from).add(to);
                    predecessors.get(to).add(from);
                    edges++;
                }
            }
        }

        // Longest-path layering over the suggested (topological) order:
        // a card sits one column right of its deepest predecessor. The
        // order is topological, so every predecessor is already layered.
        Map<CardId, Integer> layer = new HashMap<>();
        for (CardId id : ordered) {
            int deepest = -1;
            for (CardId pred : predecessors.get(id)) {
                Integer predLayer = layer.get(pred);
                if (predLayer != null) {
                    deepest = Math.max(deepest, predLayer);
                }
            }
            layer.put(id, deepest + 1);
        }

        // Column membership follows the suggested order; one barycenter
        // sweep (mean slot of the already-placed predecessors) then
        // pulls converging cards toward each other and cuts crossings.
        Map<CardId, Integer> orderIndex = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            orderIndex.put(ordered.get(i), i);
        }
        Map<Integer, List<CardId>> columns = new TreeMap<>();
        for (CardId id : ordered) {
            columns.computeIfAbsent(layer.get(id), key -> new ArrayList<>()).add(id);
        }
        Map<CardId, Integer> slot = new HashMap<>();
        for (Map.Entry<Integer, List<CardId>> entry : columns.entrySet()) {
            List<CardId> cards = entry.getValue();
            if (entry.getKey() > 0) {
                cards.sort(Comparator
                        .comparingDouble((CardId id) -> barycenter(predecessors, slot, id))
                        .thenComparingInt(orderIndex::get));
            }
            for (int slotIndex = 0; slotIndex < cards.size(); slotIndex++) {
                slot.put(cards.get(slotIndex), slotIndex);
            }
        }

        Map<CardId, Cell> cells = new HashMap<>();
        int deepestColumn = 0;
        int deepestSlot = 0;
        for (Map.Entry<Integer, List<CardId>> entry : columns.entrySet()) {
            deepestColumn = Math.max(deepestColumn, entry.getKey());
            List<CardId> cards = entry.getValue();
            for (int slotIndex = 0; slotIndex < cards.size(); slotIndex++) {
                cells.put(cards.get(slotIndex), new Cell(
                        PAD + entry.getKey() * (CARD_W + H_GAP),
                        PAD + slotIndex * (CARD_H + V_GAP)));
                deepestSlot = Math.max(deepestSlot, slotIndex);
            }
        }
        double width = PAD * 2 + (deepestColumn + 1) * (CARD_W + H_GAP) - H_GAP;
        double height = PAD * 2 + (deepestSlot + 1) * (CARD_H + V_GAP) - V_GAP;
        return new Layout(ordered, layer, successors, cells, width, height, edges);
    }

    /** Mean slot of a card's already-placed predecessors (crossing reduction). */
    private static double barycenter(Map<CardId, List<CardId>> predecessors,
                                     Map<CardId, Integer> slot, CardId id) {
        double total = 0;
        int placed = 0;
        for (CardId pred : predecessors.get(id)) {
            Integer predSlot = slot.get(pred);
            if (predSlot != null) {
                total += predSlot;
                placed++;
            }
        }
        return placed == 0 ? 0 : total / placed;
    }

    // ------------------------------------------------------------------
    // Graph canvas: bezier connectors behind the cards
    // ------------------------------------------------------------------

    /** The layered diagram: connectors first, then the cards on top. */
    private static Pane graphCanvas(BoardService service, I18n i18n,
                                    BoardController controller, Layout layout) {
        Pane canvas = new Pane();
        canvas.getStyleClass().add("process-canvas");

        for (CardId from : layout.ordered) {
            Cell source = layout.cells.get(from);
            for (CardId to : layout.successors.get(from)) {
                Cell target = layout.cells.get(to);
                boolean forward = layout.layer.get(to) > layout.layer.get(from);
                canvas.getChildren().add(connector(i18n, controller, from, to,
                        forward ? source.x + CARD_W : source.x + CARD_W / 2,
                        forward ? source.y + CARD_H / 2 : source.y + CARD_H,
                        forward ? target.x : target.x + CARD_W / 2,
                        forward ? target.y + CARD_H / 2 : target.y + CARD_H,
                        true));
            }
        }
        // Suggested links: consecutive cards of the recommended order
        // that sit in adjacent columns and are not linked yet. Dotted,
        // and a click links them (the same affordance the plain row
        // below offers for processes without any link).
        for (int i = 1; i < layout.ordered.size(); i++) {
            CardId from = layout.ordered.get(i - 1);
            CardId to = layout.ordered.get(i);
            if (service.board().hasLink(from, to)) {
                continue;
            }
            if (layout.layer.get(to) != layout.layer.get(from) + 1) {
                continue;
            }
            Cell source = layout.cells.get(from);
            Cell target = layout.cells.get(to);
            canvas.getChildren().add(connector(i18n, controller, from, to,
                    source.x + CARD_W, source.y + CARD_H / 2,
                    target.x, target.y + CARD_H / 2, false));
        }

        for (CardId id : layout.ordered) {
            service.board().findCard(id).ifPresent(card -> {
                Node node = cardNode(i18n, controller, card);
                Cell cell = layout.cells.get(id);
                node.setLayoutX(cell.x);
                node.setLayoutY(cell.y);
                canvas.getChildren().add(node);
            });
        }
        canvas.setMinSize(layout.width, layout.height);
        canvas.setPrefSize(layout.width, layout.height);
        return canvas;
    }

    /**
     * A connector between two cards, drawn the way a drawing program
     * draws an edge: a smooth bezier curve ending in a filled
     * arrowhead. Linked cards get a solid accent-colored connector
     * (click to unlink); a suggested link is dotted gray (click to
     * link). Only cyclic legacy data can produce a "backward" edge;
     * it is routed below the cards and re-enters from underneath.
     */
    private static Node connector(I18n i18n, BoardController controller,
                                  CardId from, CardId to,
                                  double x1, double y1, double x2, double y2,
                                  boolean linked) {
        Path curve = new Path();
        Polygon head;
        if (x2 > x1 + 1) {
            // Forward edge: horizontal tangents at both ends, so the
            // arrow arrives pointing straight at the next card.
            double dx = x2 - x1;
            curve.getElements().addAll(
                    new MoveTo(x1, y1),
                    new CubicCurveTo(x1 + dx / 3, y1, x2 - dx / 3, y2, x2, y2));
            head = new Polygon(x2, y2, x2 - 9, y2 - 4.5, x2 - 9, y2 + 4.5);
        } else {
            // Feedback edge: dips below the cards and re-enters from
            // underneath, arrowhead pointing up into the target.
            double drop = 22;
            curve.getElements().addAll(
                    new MoveTo(x1, y1),
                    new CubicCurveTo(x1, y1 + drop, x2, y2 + drop, x2, y2));
            head = new Polygon(x2, y2, x2 - 4.5, y2 + 9, x2 + 4.5, y2 + 9);
        }
        curve.setStrokeLineCap(StrokeLineCap.ROUND);
        curve.getStyleClass().add(linked ? "process-edge" : "process-edge-ghost");
        if (!linked) {
            curve.getStrokeDashArray().setAll(5.0, 5.0);
        }
        head.getStyleClass().add(linked ? "process-edge-head" : "process-edge-head-ghost");

        // A fat, invisible copy of the curve is the click target: the
        // thin visible line alone would be fiddly to grab, drawing
        // programs make their edges easy to pick.
        Path hitArea = new Path();
        hitArea.getElements().setAll(curve.getElements());
        hitArea.setStrokeWidth(16);
        hitArea.setStroke(Color.TRANSPARENT);
        hitArea.setFill(null);
        hitArea.setOnMouseClicked(event -> controller.onArrangeArrow(from, to));
        Tooltip.install(hitArea, new Tooltip(i18n.text(
                linked ? "process.arrow.linked" : "process.arrow.unlinked")));

        return new Group(curve, head, hitArea);
    }

    // ------------------------------------------------------------------
    // Plain row: processes whose cards are not linked yet
    // ------------------------------------------------------------------

    /**
     * Layout for a process without any link: a plain row with a dotted
     * arrow between consecutive cards, so the suggested order can be
     * linked up by clicking the arrows.
     */
    private static Node linearFlow(BoardService service, I18n i18n,
                                   BoardController controller, List<CardId> ordered) {
        HBox flow = new HBox(6);
        flow.setAlignment(Pos.CENTER_LEFT);
        flow.setPadding(new Insets(8));

        for (int i = 0; i < ordered.size(); i++) {
            if (i > 0) {
                flow.getChildren().add(linearConnector(service, i18n, controller,
                        ordered.get(i - 1), ordered.get(i)));
            }
            service.board().findCard(ordered.get(i))
                    .ifPresent(card -> flow.getChildren().add(cardNode(i18n, controller, card)));
        }
        if (ordered.isEmpty()) {
            Label none = new Label(i18n.text("process.view.no.cards"));
            none.getStyleClass().add("detail-caption");
            flow.getChildren().add(none);
        }
        return flow;
    }

    /** The dotted "not linked yet" arrow between two consecutive cards. */
    private static Node linearConnector(BoardService service, I18n i18n,
                                        BoardController controller,
                                        CardId from, CardId to) {
        boolean linked = service.board().hasLink(from, to);
        StackPane button = new StackPane(drawnArrowGlyph(true, false));
        button.getStyleClass().addAll("process-arrow-button",
                linked ? "process-arrow-link" : "process-arrow-ghost");
        Tooltip.install(button, new Tooltip(i18n.text(
                linked ? "process.arrow.linked" : "process.arrow.unlinked")));
        button.setFocusTraversable(false);
        button.setOnMouseClicked(event -> controller.onArrangeArrow(from, to));
        return button;
    }

    // ------------------------------------------------------------------
    // Process card: drawn arrow affordances around the title
    // ------------------------------------------------------------------

    /** One process card: title with its four drawn arrow affordances. */
    private static Node cardNode(I18n i18n, BoardController controller, Card card) {
        // Top pair: a click creates a NEW card and links it to this one.
        // Predecessor arrows point left, successor arrows point right:
        // the tip (and its "+") faces the side of the diagram where
        // the related card lives.
        Node addPredecessor = drawnArrowButton(true, true,
                i18n.text("process.arrow.new.predecessor"),
                () -> controller.onProcessAddNewRelation(card.id(), true));
        Node addSuccessor = drawnArrowButton(false, true,
                i18n.text("process.arrow.new.successor"),
                () -> controller.onProcessAddNewRelation(card.id(), false));
        // Bottom pair: a click opens the picker that links an EXISTING card.
        Node linkPredecessor = drawnArrowButton(true, false,
                i18n.text("process.arrow.link.predecessor"),
                () -> controller.onProcessLinkExisting(card.id(), true));
        Node linkSuccessor = drawnArrowButton(false, false,
                i18n.text("process.arrow.link.successor"),
                () -> controller.onProcessLinkExisting(card.id(), false));

        HBox top = arrowsRow(addPredecessor, addSuccessor);
        HBox bottom = arrowsRow(linkPredecessor, linkSuccessor);

        Rectangle swatch = new Rectangle(10, 10, Color.web(card.color().hex()));
        swatch.setArcWidth(3);
        swatch.setArcHeight(3);

        Label title = new Label(card.title());
        title.getStyleClass().add("process-card-title");
        title.setWrapText(true);
        title.setMaxWidth(CARD_W - 20);
        title.setMaxHeight(34); // two wrapped lines at most
        title.setOnMouseClicked(event -> controller.onOpenCardDetail(card.id()));
        Tooltip.install(title, new Tooltip(i18n.text("process.card.open")));

        HBox titleRow = new HBox(6, swatch, title);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);

        VBox node = new VBox(3, top, titleRow, bottom);
        node.getStyleClass().add("process-card");
        node.setAlignment(Pos.CENTER);
        node.setPadding(new Insets(4));
        node.setPrefSize(CARD_W, CARD_H);
        // Keep a long wrapped title from spilling out of the fixed cell.
        node.setClip(new Rectangle(CARD_W, CARD_H));
        return node;
    }

    private static HBox arrowsRow(Node left, Node right) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(4, left, spacer, right);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    /**
     * One of the card's four arrow affordances, drawn as vector shapes
     * (shaft + head, plus a small "+" on the new-card arrows) instead
     * of a font glyph, so it stays visible in every theme. Green
     * arrows create a NEW card; blue arrows link an EXISTING card.
     */
    private static Node drawnArrowButton(boolean leftwards, boolean createsCard,
                                         String tooltip, Runnable action) {
        StackPane button = new StackPane(drawnArrowGlyph(leftwards, createsCard));
        button.getStyleClass().addAll("process-arrow-button",
                createsCard ? "process-arrow-new" : "process-arrow-link");
        Tooltip.install(button, new Tooltip(tooltip));
        button.setFocusTraversable(false);
        button.setOnMouseClicked(event -> action.run());
        return button;
    }

    /** Shaft + arrowhead pointing left or right, optionally with a drawn "+". */
    private static Group drawnArrowGlyph(boolean leftwards, boolean withPlus) {
        double width = 24;
        double height = 14;
        double midY = height / 2;
        double headLength = 8;
        double headHalf = 4.5;
        double tailX = leftwards ? width : 0;
        double baseX = leftwards ? headLength : width - headLength;
        double tipX = leftwards ? 0 : width;

        Line shaft = new Line(tailX, midY, baseX, midY);
        shaft.getStyleClass().add("arrow-shaft");

        Polygon head = new Polygon(tipX, midY, baseX, midY - headHalf, baseX, midY + headHalf);
        head.getStyleClass().add("arrow-head");

        Group glyph = new Group(shaft, head);
        if (withPlus) {
            // The "+" sits on the side the arrow points to: where the
            // new card will appear.
            double plusX = leftwards ? -7 : width + 7;
            Line plusVertical = new Line(plusX, midY - 3.5, plusX, midY + 3.5);
            Line plusHorizontal = new Line(plusX - 3.5, midY, plusX + 3.5, midY);
            plusVertical.getStyleClass().add("arrow-plus");
            plusHorizontal.getStyleClass().add("arrow-plus");
            glyph.getChildren().addAll(plusVertical, plusHorizontal);
        }
        return glyph;
    }

    private static Node buildUnassignedNote(I18n i18n, long count) {
        Label label = new Label(i18n.text("process.view.unassigned", count));
        label.getStyleClass().add("detail-caption");
        return label;
    }
}
