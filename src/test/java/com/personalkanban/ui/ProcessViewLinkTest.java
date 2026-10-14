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
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T12 · N-8 (user report: «se borran las relaciones al hacer clic en una
 * flecha de la vista de proyectos»). The connector of an existing link used to
 * remove it on a single plain click; now linking a suggested connector still
 * costs one click, while removing an existing relation asks first.
 *
 * <p>The confirmation itself is a modal dialog, so it is not driven here: the
 * one-click path (linking) is exercised through the real rendered view, and the
 * guard (confirm <b>before</b> unlinking) is checked on the controller's code,
 * the same way {@code KeyboardNavigationTest} checks the delete confirmation.</p>
 */
class ProcessViewLinkTest {

    private AppContext context;
    private BoardController controller;
    private I18n i18n;
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
        Path dir = Files.createTempDirectory("pk-process-links");
        context = AppContext.openAt(dir.resolve("kanban.db"), dir.resolve("config.properties"));
        ColumnId column = context.boardService().addColumn("Todo", "", BoardColor.BLUE,
                WipLimit.unlimited());
        first = context.boardService().addCard(column, "Primera", "", BoardColor.DEFAULT);
        second = context.boardService().addCard(column, "Segunda", "", BoardColor.DEFAULT);
        ProcessId process = context.boardService().addProcess("#flujo");
        context.boardService().assignProcessToCards(List.of(first, second), process);

        i18n = new I18n(context.savedLocale());
        fx(() -> {
            controller = new BoardController(context);
            Scene scene = new Scene(controller.root(), 1000, 700);
            scene.getStylesheets().addAll(context.themeManager().stylesheets());
            controller.bindScene(scene);
            controller.onSelectView(true); // the projects/processes view
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
    void aDottedConnectorStillLinksTheTwoCardsWithOneClick() throws Exception {
        fx(() -> {
            assertThat(context.boardService().board().hasLink(first, second))
                    .as("the two members start unlinked").isFalse();
            Node ghost = findFirst(controller.root(), node -> node instanceof javafx.scene.layout.StackPane
                    && node.getStyleClass().contains("process-arrow-ghost"));
            assertThat(ghost).as("the suggested connector is drawn").isNotNull();
            assertThat(tooltipOf(ghost)).isEqualTo(i18n.text("process.arrow.unlinked"));

            click(ghost);
            relayout();

            assertThat(context.boardService().board().hasLink(first, second))
                    .as("one click links them, as before").isTrue();
            return null;
        });
    }

    @Test
    void theConnectorOfAnExistingLinkSaysItAsksBeforeRemovingIt() throws Exception {
        fx(() -> {
            context.boardService().linkCards(first, second);
            controller.onSelectView(false);
            controller.onSelectView(true);
            relayout();

            Node hitArea = findFirst(controller.root(), node -> node instanceof javafx.scene.shape.Path
                    && node.getStyleClass().contains("process-edge-hit"));
            assertThat(hitArea).as("the drawn connector of the link is clickable").isNotNull();
            assertThat(tooltipOf(hitArea))
                    .as("and the tooltip announces the confirmation")
                    .isEqualTo(i18n.text("process.arrow.linked"));
            return null;
        });
    }

    @Test
    void theControllerAsksBeforeRemovingAnExistingRelation() throws Exception {
        Path source = boardControllerSource();
        String code = Files.readString(source);
        int start = code.indexOf("public void onArrangeArrow(");
        int end = code.indexOf("private String arrowPairLabel(");
        assertThat(start).as("onArrangeArrow is where it was").isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        String method = code.substring(start, end);

        int confirm = method.indexOf("dialogs.confirm(i18n.text(\"process.unlink.confirm\"");
        int unlink = method.indexOf("service.unlinkCards(");

        assertThat(confirm).as("removing a relation asks the user first").isGreaterThanOrEqualTo(0);
        assertThat(unlink).as("and unlinks only after the answer").isGreaterThan(confirm);
        assertThat(method.substring(confirm, unlink))
                .as("the answer is checked before touching the board").contains("return;");
    }

    // ------------------------------------------------------------------

    private static Path boardControllerSource() {
        Path candidate = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 4 && candidate != null; depth++) {
            Path probe = candidate.resolve("src/main/java/com/personalkanban/ui/BoardController.java");
            if (Files.isRegularFile(probe)) {
                return probe;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate BoardController.java");
    }

    /**
     * The tooltip of any node: a control carries it as a property, and
     * {@code Tooltip.install} leaves it in the node's own properties under the
     * key JavaFX uses for that (which is how the connectors are annotated).
     */
    private static String tooltipOf(Node node) {
        if (node instanceof javafx.scene.control.Control control
                && control.getTooltip() != null) {
            return control.getTooltip().getText();
        }
        Object installed = node.getProperties().get("javafx.scene.control.Tooltip");
        return installed instanceof Tooltip tooltip ? tooltip.getText() : null;
    }

    private void relayout() {
        controller.root().applyCss();
        controller.root().layout();
    }

    private static void click(Node node) {
        node.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 5, 5, 5, 5,
                MouseButton.PRIMARY, 1,
                false, false, false, false,
                true, false, false,
                false, false, false,
                new PickResult(node, 5, 5)));
    }

    private static Node findFirst(Node node, Predicate<Node> match) {
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
