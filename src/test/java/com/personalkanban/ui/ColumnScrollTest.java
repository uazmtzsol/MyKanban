package com.personalkanban.ui;

import com.personalkanban.AppContext;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.WipLimit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User request (2026-10-13, N-13): selecting a card refreshes the board, and
 * the refresh used to rebuild each column's scroll pane — which starts at the
 * top. Working on the cards at the end of a long column therefore kept jumping
 * back. The column must stay where the user left it.
 */
class ColumnScrollTest {

    private static final double SCROLLED = 0.6;

    private AppContext context;
    private BoardController controller;
    private ColumnId column;
    private CardId lastCard;

    @BeforeAll
    static void startJavaFxToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // Toolkit already initialized by another test in this JVM.
        }
        Platform.setImplicitExit(false);
    }

    @BeforeEach
    void setUpBoard() throws Exception {
        Path dir = Files.createTempDirectory("pk-column-scroll");
        context = AppContext.openAt(dir.resolve("kanban.db"), dir.resolve("config.properties"));
        column = context.boardService().addColumn("Todo", "", BoardColor.BLUE, WipLimit.unlimited());
        // Long enough that the column really scrolls inside a 600px window.
        for (int i = 0; i < 40; i++) {
            lastCard = context.boardService().addCard(column, "Tarjeta " + i,
                    "una descripción que ocupa sitio", BoardColor.DEFAULT);
        }

        fx(() -> {
            controller = new BoardController(context);
            Scene scene = new Scene(controller.root(), 900, 600);
            scene.getStylesheets().addAll(context.themeManager().stylesheets());
            controller.bindScene(scene);
            relayout();
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void selectingACardKeepsTheColumnWhereItWas() throws Exception {
        Double afterRefresh = fx(() -> {
            ScrollPane scroll = columnScroll();
            assertThat(scroll.getVvalue()).as("a fresh column starts at the top").isZero();
            assertThat(scroll.getContent().getBoundsInLocal().getHeight())
                    .as("the column has to be scrollable for this test to mean anything")
                    .isGreaterThan(scroll.getViewportBounds().getHeight());

            scroll.setVvalue(SCROLLED);
            relayout();
            assertThat(columnScroll().getVvalue()).isEqualTo(SCROLLED);

            // A plain click on a card is what the user does right before the jump.
            controller.onCardSelected(lastCard);
            relayout();

            return columnScroll().getVvalue();
        });

        // The skin quantizes vvalue to pixels, so the restored value may sit a
        // fraction of a pixel away; what matters is that it is not the top.
        assertThat(afterRefresh)
                .as("the column stays where the user was working")
                .isCloseTo(SCROLLED, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void anotherBoardChangeKeepsItToo() throws Exception {
        Double afterRefresh = fx(() -> {
            columnScroll().setVvalue(SCROLLED);
            relayout();

            // Every one of these refreshes the board.
            controller.onSetBoardCardViewMode(
                    com.personalkanban.application.CardViewSettings.Mode.TITLE_ONLY);
            relayout();
            controller.onSetBoardCardViewMode(
                    com.personalkanban.application.CardViewSettings.Mode.FULL);
            relayout();

            return columnScroll().getVvalue();
        });

        assertThat(afterRefresh)
                .isCloseTo(SCROLLED, org.assertj.core.data.Offset.offset(0.01));
    }

    // ------------------------------------------------------------------

    private ScrollPane columnScroll() {
        Node found = findFirst(controller.root(), node -> node instanceof ScrollPane scroll
                && scroll.getStyleClass().contains("cards-scroll"));
        assertThat(found).as("the column card list is on screen").isNotNull();
        return (ScrollPane) found;
    }

    private void relayout() {
        controller.root().applyCss();
        controller.root().layout();
    }

    private static Node findFirst(Node node, java.util.function.Predicate<Node> match) {
        if (match.test(node)) {
            return node;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node found = findFirst(child, match);
                if (found != null) {
                    return found;
                }
            }
        }
        // A freshly built ScrollPane exposes its content only through
        // getContent() until its skin has been created.
        if (node instanceof ScrollPane scroll && scroll.getContent() != null) {
            return findFirst(scroll.getContent(), match);
        }
        return null;
    }

    private static <T> T fx(Supplier<T> action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(action.get());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });
        assertThat(done.await(30, TimeUnit.SECONDS)).as("FX action finished").isTrue();
        assertThat(error.get()).isNull();
        return result.get();
    }
}
