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
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
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
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User request (2026-10-13, N-6) plus B3: the bar must offer «vencidas»,
 * «vence hoy» and «esta semana», and the startup reminder must say how many
 * cards are in the urgent ranges and take the user to them when clicked.
 * Driven through the real filter bar and the real rendered cards.
 */
class DueFilterUiTest {

    private AppContext context;
    private BoardController controller;
    private I18n i18n;
    private CardId overdue;
    private CardId today;
    private CardId thisWeek;
    private CardId undated;

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
        Path dir = Files.createTempDirectory("pk-due-filter");
        context = AppContext.openAt(dir.resolve("kanban.db"), dir.resolve("config.properties"));
        ColumnId column = context.boardService().addColumn("Todo", "", BoardColor.BLUE,
                WipLimit.unlimited());
        LocalDate today0 = LocalDate.now();
        overdue = addCard(column, "Vencida", today0.minusDays(1));
        today = addCard(column, "Vence hoy", today0);
        thisWeek = addCard(column, "Esta semana", today0.plusDays(2));
        undated = addCard(column, "Sin fecha", null);

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

    private CardId addCard(ColumnId column, String title, LocalDate dueDate) {
        return context.boardService().addCard(column, title, "", BoardColor.DEFAULT, dueDate,
                List.of());
    }

    @Test
    void eachDueToggleShowsExactlyItsRange() throws Exception {
        fx(() -> {
            assertThat(renderedCards()).as("no due filter shows every card")
                    .containsExactlyInAnyOrder(overdue, today, thisWeek, undated);

            dueToggle("filter.due.overdue").fire();
            relayout();
            assertThat(renderedCards()).containsExactly(overdue);

            dueToggle("filter.due.today").fire();
            relayout();
            assertThat(renderedCards()).containsExactly(today);
            assertThat(dueToggle("filter.due.overdue").isSelected())
                    .as("one scope at a time").isFalse();

            dueToggle("filter.due.week").fire();
            relayout();
            assertThat(renderedCards()).containsExactlyInAnyOrder(today, thisWeek);

            dueToggle("filter.due.week").fire();
            relayout();
            assertThat(renderedCards()).as("clicking the active toggle clears the filter")
                    .containsExactlyInAnyOrder(overdue, today, thisWeek, undated);
            return null;
        });
    }

    @Test
    void theReminderCountsOverdueAndTodayAndTakesTheUserToThem() throws Exception {
        fx(() -> {
            Label notice = dueNotice();
            assertThat(notice.isVisible()).as("the reminder is shown").isTrue();
            assertThat(notice.getText())
                    .contains(i18n.text("due.notice.overdue", 1))
                    .contains(i18n.text("due.notice.today", 1));

            click(notice);

            assertThat(notice.isVisible()).as("it steps aside once used").isFalse();
            assertThat(dueToggle("filter.due.overdue").isSelected())
                    .as("it jumps to the most urgent range").isTrue();
            relayout();
            assertThat(renderedCards()).containsExactly(overdue);
            return null;
        });
    }

    @Test
    void theReminderStaysHiddenWhileADueFilterIsActive() throws Exception {
        fx(() -> {
            dueToggle("filter.due.week").fire();
            relayout();
            assertThat(dueNotice().isVisible()).isFalse();

            dueToggle("filter.due.week").fire();
            relayout();
            assertThat(dueNotice().isVisible()).as("it comes back with the unfiltered board").isTrue();
            return null;
        });
    }

    // ------------------------------------------------------------------

    private ToggleButton dueToggle(String tipKey) {
        Node found = findFirst(controller.root(), node -> node instanceof ToggleButton button
                && button.getTooltip() != null
                && i18n.text(tipKey).equals(button.getTooltip().getText()));
        assertThat(found).as("the %s toggle is in the filter bar", tipKey).isNotNull();
        return (ToggleButton) found;
    }

    private Label dueNotice() {
        Node found = findFirst(controller.root(), node -> node instanceof Label
                && node.getStyleClass().contains("due-notice"));
        assertThat(found).as("the due reminder exists").isNotNull();
        return (Label) found;
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
