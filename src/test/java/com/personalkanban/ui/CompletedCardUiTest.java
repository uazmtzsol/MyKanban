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
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T24 · B11: finished cards are hidden until «Mostrar terminadas» says
 * otherwise, the ✓ on the card front (and the card menu) toggles the state,
 * and the due filters/notice never count finished work. Driven through the
 * real controller and the real rendered cards.
 */
class CompletedCardUiTest {

    private AppContext context;
    private BoardController controller;
    private I18n i18n;
    private ColumnId pending;
    private ColumnId doneColumn;
    private CardId overdue;
    private CardId plain;

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
        Path dir = Files.createTempDirectory("pk-completed-filter");
        context = AppContext.openAt(dir.resolve("kanban.db"), dir.resolve("config.properties"));
        pending = context.boardService().addColumn("Pendiente", "", BoardColor.BLUE,
                WipLimit.unlimited());
        doneColumn = context.boardService().addColumn("Hecho", "", BoardColor.GREEN,
                WipLimit.unlimited());
        context.boardService().setColumnDone(doneColumn, true);
        overdue = addCard("Vencida", LocalDate.now().minusDays(1));
        plain = addCard("Sin fecha", null);

        i18n = new I18n(context.savedLocale());
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

    private CardId addCard(String title, LocalDate dueDate) {
        return context.boardService().addCard(pending, title, "", BoardColor.DEFAULT, dueDate,
                List.of());
    }

    @Test
    void finishedCardsAreHiddenUntilTheToggleBringsThemBack() throws Exception {
        context.boardService().completeCard(overdue);
        fx(() -> {
            controller.refresh();
            relayout();
            assertThat(renderedCards()).as("finished cards are history by default")
                    .containsExactly(plain);

            completedToggle().fire();
            relayout();
            assertThat(renderedCards()).as("«Mostrar terminadas» restores them")
                    .containsExactlyInAnyOrder(overdue, plain);

            completedToggle().fire();
            relayout();
            assertThat(renderedCards()).containsExactly(plain);
            return null;
        });
    }

    @Test
    void droppingIntoTheFinalizedColumnHidesTheCardAsFinished() throws Exception {
        context.boardService().moveCard(overdue, doneColumn, 0);
        fx(() -> {
            controller.refresh();
            relayout();
            assertThat(renderedCards())
                    .as("the drop finished the card, so the board hides it")
                    .containsExactly(plain);
            return null;
        });
        assertThat(context.boardService().board().findCard(overdue).orElseThrow().isDone())
                .isTrue();
    }

    @Test
    void theDoneToggleOnTheCardFrontTogglesTheState() throws Exception {
        fx(() -> {
            Button check = doneButtonOn(overdue);
            assertThat(check.getStyleClass()).as("pending: dimmed glyph")
                    .contains("card-flag-off");
            check.fire();
            relayout();
            assertThat(renderedCards()).containsExactly(plain);
            return null;
        });
        assertThat(context.boardService().board().findCard(overdue).orElseThrow().isDone())
                .as("the ✓ marked it finished").isTrue();

        fx(() -> {
            completedToggle().fire();
            relayout();
            Button check = doneButtonOn(overdue);
            assertThat(check.getStyleClass()).as("done: vivid glyph")
                    .contains("card-done-on");
            check.fire();
            relayout();
            assertThat(renderedCards()).containsExactlyInAnyOrder(overdue, plain);
            return null;
        });
        assertThat(context.boardService().board().findCard(overdue).orElseThrow().isDone())
                .as("the ✓ put it back in progress").isFalse();
    }

    @Test
    void theDueFiltersAndNoticeNeverCountFinishedCards() throws Exception {
        // The only overdue card on the board is now finished: by default the
        // overdue filter must find nothing, and the notice must not mention it.
        // «Mostrar terminadas» is what recovers it from the due filters too.
        context.boardService().completeCard(overdue);
        fx(() -> {
            controller.refresh();
            relayout();

            Node overdueToggle = findFirst(node -> node instanceof ToggleButton button
                    && button.getTooltip() != null
                    && i18n.text("filter.due.overdue")
                            .equals(button.getTooltip().getText()));
            assertThat(overdueToggle).isNotNull();
            ((ToggleButton) overdueToggle).fire();
            relayout();
            assertThat(renderedCards())
                    .as("a finished card is not overdue work anymore")
                    .isEmpty();

            ((ToggleButton) overdueToggle).fire(); // clear the due filter
            completedToggle().fire();              // now show finished cards
            relayout();
            assertThat(renderedCards())
                    .as("the toggle recovers them from the due filters too")
                    .containsExactlyInAnyOrder(overdue, plain);
            return null;
        });
    }

    // ------------------------------------------------------------------

    /** The filter bar's «Mostrar terminadas» toggle (tooltip = the i18n key). */
    private ToggleButton completedToggle() {
        Node found = findFirst(node -> node instanceof ToggleButton button
                && button.getTooltip() != null
                && i18n.text("filter.completed.show").equals(button.getTooltip().getText()));
        assertThat(found).as("the finished-cards toggle is in the filter bar").isNotNull();
        return (ToggleButton) found;
    }

    /** The ✓ on the front of the given card. */
    private Button doneButtonOn(CardId cardId) {
        Node card = findFirst(node -> cardId.equals(node.getUserData()));
        assertThat(card).as("the card %s is rendered", cardId).isNotNull();
        Node found = findFirstIn(card, node -> node instanceof Button button
                && button.getTooltip() != null
                && (i18n.text("card.done.off").equals(button.getTooltip().getText())
                        || i18n.text("card.done.on").equals(button.getTooltip().getText())));
        assertThat(found).as("the card %s carries a ✓ button", cardId).isNotNull();
        return (Button) found;
    }

    private List<CardId> renderedCards() {
        List<CardId> shown = new java.util.ArrayList<>();
        collectCardIds(controller.root(), shown);
        return shown;
    }

    private static void collectCardIds(Node node, List<CardId> found) {
        if (node.getUserData() instanceof CardId id) {
            found.add(id);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectCardIds(child, found);
            }
        }
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

    private void relayout() {
        controller.root().applyCss();
        controller.root().layout();
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
