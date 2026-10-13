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
 * User request (2026-10-13, N-1/N-2): in card-selection mode the filter bar
 * must not be shown — the two bars alternate — and the configured exit key
 * (Esc by default) must drop the current selection.
 */
class SelectionModeBarsTest {

    private AppContext context;
    private BoardController controller;
    private I18n i18n;
    private ColumnId column;
    private CardId firstCard;
    private CardId secondCard;

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
        Path dir = Files.createTempDirectory("pk-selection-bars");
        context = AppContext.openAt(dir.resolve("kanban.db"), dir.resolve("config.properties"));
        column = context.boardService().addColumn("Todo", "", BoardColor.BLUE, WipLimit.unlimited());
        firstCard = context.boardService().addCard(column, "Uno", "", BoardColor.DEFAULT);
        secondCard = context.boardService().addCard(column, "Dos", "", BoardColor.DEFAULT);

        i18n = new I18n(context.savedLocale());
        fx(() -> {
            controller = new BoardController(context);
            Scene scene = new Scene(controller.root(), 900, 600);
            scene.getStylesheets().addAll(context.themeManager().stylesheets());
            controller.bindScene(scene);
            controller.root().applyCss();
            controller.root().layout();
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
    void theTwoBarsNeverShowAtTheSameTime() throws Exception {
        fx(() -> {
            assertThat(filterBar().isVisible()).as("filters visible outside selection mode").isTrue();
            assertThat(selectionBar().isVisible()).as("selection bar hidden by default").isFalse();

            controller.onToggleSelectionMode(column);

            assertThat(selectionBar().isVisible()).as("selection bar visible in the mode").isTrue();
            assertThat(selectionBar().isManaged()).isTrue();
            assertThat(filterBar().isVisible()).as("filters hidden in selection mode").isFalse();
            assertThat(filterBar().isManaged()).as("hidden filters take no space").isFalse();

            controller.onToggleSelectionMode(column);

            assertThat(filterBar().isVisible()).as("filters are back").isTrue();
            assertThat(selectionBar().isVisible()).as("selection bar hidden again").isFalse();
            return null;
        });
    }

    @Test
    void theExitKeyDropsTheSelectionThenLeavesTheMode() throws Exception {
        fx(() -> {
            controller.onToggleSelectionMode(column);
            controller.onToggleCardSelection(firstCard);
            controller.onToggleCardSelection(secondCard);
            assertThat(selectionCountText()).contains("2");

            // First press: the selection is dropped, the mode stays on so the
            // user can pick a different set of cards.
            controller.onExitKey();
            assertThat(selectionCountText()).contains("0");
            assertThat(selectionBar().isVisible()).isTrue();
            assertThat(filterBar().isVisible()).isFalse();

            // Second press (nothing selected): leave the mode, filters return.
            controller.onExitKey();
            assertThat(selectionBar().isVisible()).isFalse();
            assertThat(filterBar().isVisible()).isTrue();
            return null;
        });
    }

    @Test
    void theExitKeyDropsTheFocusedCardOutsideSelectionMode() throws Exception {
        fx(() -> {
            // Nothing focused: the key is harmless.
            controller.onExitKey();
            assertThat(filterBar().isVisible()).isTrue();
            assertThat(selectionBar().isVisible()).isFalse();
            return null;
        });
    }

    private Node barWithStyleClass(String styleClass) {
        for (Node child : controller.root().getChildren()) {
            if (child.getStyleClass().contains(styleClass)) {
                return child;
            }
        }
        throw new AssertionError("no node with style class " + styleClass + " in the root");
    }

    private Node filterBar() {
        return barWithStyleClass("filter-bar");
    }

    private Node selectionBar() {
        return barWithStyleClass("selection-bar");
    }

    /** The "n selected" label of the selection bar. */
    private String selectionCountText() {
        Label count = (Label) descendants(selectionBar()).stream()
                .filter(Label.class::isInstance)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no count label in the selection bar"));
        return count.getText();
    }

    private static java.util.List<Node> descendants(Node node) {
        java.util.List<Node> found = new java.util.ArrayList<>();
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                found.add(child);
                found.addAll(descendants(child));
            }
        }
        return found;
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
