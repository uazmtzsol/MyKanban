package com.personalkanban.ui;

import com.personalkanban.application.BoardService;
import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.Process;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the "processes" view (session 6): no columns, one row per process,
 * its cards laid out left to right and joined by arrows following the
 * precedence order. Each card carries four arrow buttons: the top pair adds
 * a NEW predecessor/successor card, the bottom pair links an EXISTING card.
 * The arrow between two linked cards can be clicked to attach a note.
 */
final class ProcessViewBuilder {

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

        HBox flow = new HBox(6);
        flow.setAlignment(Pos.CENTER_LEFT);
        flow.setPadding(new Insets(8));

        List<Card> members = service.board().cardsOfProcess(process.id());
        List<CardId> ids = members.stream().map(Card::id).toList();
        var order = service.suggestedOrder(ids);
        List<CardId> ordered = new ArrayList<>(order.ordered());
        ordered.addAll(order.cycleRemaining());

        for (int i = 0; i < ordered.size(); i++) {
            if (i > 0) {
                CardId from = ordered.get(i - 1);
                CardId to = ordered.get(i);
                flow.getChildren().add(arrow(service, i18n, controller, from, to));
            }
            service.board().findCard(ordered.get(i))
                    .ifPresent(card -> flow.getChildren().add(cardNode(i18n, controller, card)));
        }
        if (ordered.isEmpty()) {
            Label none = new Label(i18n.text("process.view.no.cards"));
            none.getStyleClass().add("detail-caption");
            flow.getChildren().add(none);
        }

        ScrollPane scroller = new ScrollPane(flow);
        scroller.setFitToHeight(true);
        scroller.setPrefHeight(180);
        scroller.getStyleClass().add("process-row-scroll");

        VBox row = new VBox(4, header, scroller);
        row.getStyleClass().add("process-row");
        return row;
    }

    /** Arrow between two consecutive cards; clickable to attach/edit a note. */
    private static Node arrow(BoardService service, I18n i18n, BoardController controller,
                              CardId from, CardId to) {
        boolean linked = service.board().hasLink(from, to);
        Button node = new Button(linked ? "\u2192" : "\u2937");
        node.getStyleClass().add(linked ? "process-arrow" : "process-arrow-open");
        node.setFocusTraversable(false);
        node.setTooltip(new Tooltip(i18n.text(linked ? "process.arrow.linked" : "process.arrow.unlinked")));
        node.setOnAction(e -> onArrowClicked(controller, from, to));
        return node;
    }

    private static void onArrowClicked(BoardController controller, CardId from, CardId to) {
        controller.onArrangeArrow(from, to);
    }

    private static Node cardNode(I18n i18n, BoardController controller, Card card) {
        Button addPredecessor = arrowButton("\u2190", i18n.text("process.arrow.new.predecessor"));
        addPredecessor.setOnAction(e -> controller.onProcessAddNewRelation(card.id(), true));

        Button addSuccessor = arrowButton("\u2192", i18n.text("process.arrow.new.successor"));
        addSuccessor.setOnAction(e -> controller.onProcessAddNewRelation(card.id(), false));

        Button linkPredecessor = arrowButton("\u2190", i18n.text("process.arrow.link.predecessor"));
        linkPredecessor.setOnAction(e -> controller.onProcessLinkExisting(card.id(), true));

        Button linkSuccessor = arrowButton("\u2192", i18n.text("process.arrow.link.successor"));
        linkSuccessor.setOnAction(e -> controller.onProcessLinkExisting(card.id(), false));

        HBox top = arrowsRow(addPredecessor, addSuccessor);
        HBox bottom = arrowsRow(linkPredecessor, linkSuccessor);

        Label title = new Label(card.title());
        title.getStyleClass().add("process-card-title");
        title.setWrapText(true);
        title.setMaxWidth(160);
        title.setOnMouseClicked(e -> controller.onOpenCardDetail(card.id()));
        title.setTooltip(new Tooltip(i18n.text("process.card.open")));

        HBox titleRow = new HBox(title);
        titleRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(title, Priority.ALWAYS);

        VBox node = new VBox(4, top, titleRow, bottom);
        node.getStyleClass().add("process-card");
        node.setAlignment(Pos.CENTER);
        node.setPadding(new Insets(4));
        return node;
    }

    private static HBox arrowsRow(Node left, Node right) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(4, left, spacer, right);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static Button arrowButton(String glyph, String tip) {
        Button button = new Button(glyph);
        button.getStyleClass().add("process-arrow-button");
        button.setTooltip(new Tooltip(tip));
        button.setFocusTraversable(false);
        return button;
    }

    private static Node buildUnassignedNote(I18n i18n, long count) {
        Label label = new Label(i18n.text("process.view.unassigned", count));
        label.getStyleClass().add("detail-caption");
        return label;
    }
}
