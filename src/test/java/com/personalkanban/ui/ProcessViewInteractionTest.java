package com.personalkanban.ui;

import com.personalkanban.AppContext;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.ProcessId;
import com.personalkanban.domain.board.WipLimit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §7.8 A1 + B1. A single click in the processes view selects/focuses the card
 * (making it «current», like the kanban board) and never opens the detail
 * window; only a double click does. The relation inspector is a single block
 * pinned to the bottom of the whole view, collapsed while no card is focused.
 */
class ProcessViewInteractionTest {

    private AppContext context;
    private BoardController controller;
    private CardId first;
    private CardId second;

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
        Path dir = Files.createTempDirectory("pk-process-interaction");
        context = AppContext.openAt(dir.resolve("kanban.db"), dir.resolve("config.properties"));
        ColumnId column = context.boardService().addColumn("Todo", "", BoardColor.BLUE,
                WipLimit.unlimited());
        first = context.boardService().addCard(column, "Primera", "", BoardColor.DEFAULT);
        second = context.boardService().addCard(column, "Segunda", "", BoardColor.DEFAULT);
        ProcessId process = context.boardService().addProcess("#flujo");
        context.boardService().assignProcessToCards(List.of(first, second), process);
        context.boardService().linkCards(first, second);

        fx(() -> {
            controller = new BoardController(context);
            Scene scene = new Scene(controller.root(), 1000, 700);
            scene.getStylesheets().addAll(context.themeManager().stylesheets());
            controller.bindScene(scene);
            controller.onSelectView(true); // the processes view
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
    void aSingleClickOnlySelectsTheCardAndDoesNotOpenTheDetail() throws Exception {
        fx(() -> {
            Node card = processCard(first);
            assertThat(card).as("the process card is rendered").isNotNull();
            int windowsBefore = Window.getWindows().size();

            click(card, 1);
            relayout();

            assertThat(Window.getWindows().size())
                    .as("one click never opens the detail window, as on the board")
                    .isEqualTo(windowsBefore);
            assertThat(controller.isCardCurrent(first))
                    .as("but it does make the card the current one").isTrue();
            return null;
        });
    }

    @Test
    void aDoubleClickOpensTheDetailWindow() throws Exception {
        fx(() -> {
            Node card = processCard(first);
            int windowsBefore = Window.getWindows().size();
            click(card, 2);
            relayout();
            assertThat(Window.getWindows().size())
                    .as("a double click opens the detail window")
                    .isGreaterThan(windowsBefore);
            return null;
        });
    }

    @Test
    void theInspectorIsASingleBlockCollapsedUntilACardIsFocused() throws Exception {
        fx(() -> {
            List<Node> inspectors = allInspectors();
            assertThat(inspectors).as("exactly one relation inspector in the view")
                    .hasSize(1);
            Node inspector = inspectors.get(0);
            assertThat(inspector.isVisible())
                    .as("collapsed while nothing is focused").isFalse();
            assertThat(inspector.isManaged()).isFalse();

            click(processCard(first), 1);
            relayout();
            assertThat(inspector.isVisible())
                    .as("the inspector appears when a card is focused").isTrue();
            assertThat(inspector.isManaged()).isTrue();

            controller.onClearProcessFocus();
            relayout();
            assertThat(inspector.isVisible())
                    .as("and hides again when the focus is dropped").isFalse();
            return null;
        });
    }

    // ------------------------------------------------------------------

    private Node processCard(CardId id) {
        return findFirst(node -> node.getStyleClass().contains("process-card")
                && id.equals(node.getUserData()));
    }

    private List<Node> allInspectors() {
        List<Node> found = new ArrayList<>();
        collect(controller.root(), node ->
                node.getStyleClass().contains("process-inspector"), found);
        return found;
    }

    private static void collect(Node node, Predicate<Node> match, List<Node> out) {
        if (match.test(node)) {
            out.add(node);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collect(child, match, out);
            }
        }
    }

    private void relayout() {
        controller.root().applyCss();
        controller.root().layout();
    }

    private static void click(Node node, int count) {
        node.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 5, 5, 5, 5,
                MouseButton.PRIMARY, count,
                false, false, false, false,
                true, false, false,
                false, false, false,
                new PickResult(node, 5, 5)));
    }

    private Node findFirst(Predicate<Node> match) {
        return findFirstIn(controller.root(), match);
    }

    private static Node findFirstIn(Node node, Predicate<Node> match) {
        if (match.test(node)) {
            return node;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node found = findFirstIn(child, match);
                if (found != null) {
                    return found;
                }
            }
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
