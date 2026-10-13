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
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
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
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User request (2026-10-13, N-12): the label filter must also express
 * «no tiene la etiqueta X» and «no tiene ninguna etiqueta». Driven through the
 * real filter bar: the rendered cards say which ones survived the filter.
 */
class LabelFilterModeUiTest {

    private AppContext context;
    private BoardController controller;
    private I18n i18n;
    private CardId unlabeled;
    private CardId work;
    private CardId home;

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
        Path dir = Files.createTempDirectory("pk-label-filter-mode");
        context = AppContext.openAt(dir.resolve("kanban.db"), dir.resolve("config.properties"));
        ColumnId column = context.boardService().addColumn("Todo", "", BoardColor.BLUE,
                WipLimit.unlimited());
        unlabeled = context.boardService().addCard(column, "Sin etiquetas", "", BoardColor.DEFAULT);
        work = context.boardService().addCard(column, "Con work", "", BoardColor.DEFAULT);
        home = context.boardService().addCard(column, "Con home", "", BoardColor.DEFAULT);
        context.boardService().addLabelsToCards(List.of(work), List.of("work"));
        context.boardService().addLabelsToCards(List.of(home), List.of("home"));

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

    @Test
    void theModeControlOffersEveryCombinationIncludingTheNewOnes() throws Exception {
        fx(() -> {
            assertThat(modeCombo().getItems())
                    .containsExactly(BoardController.LabelFilterMode.values());
            assertThat(modeCombo().getItems())
                    .contains(BoardController.LabelFilterMode.NOT,
                            BoardController.LabelFilterMode.NO_LABELS);
            return null;
        });
    }

    @Test
    void noHavingTheLabelHidesOnlyTheCardsThatCarryIt() throws Exception {
        List<CardId> visible = fx(() -> {
            labelField().setText("work");
            setMode(2); // «Ninguna de ellas»
            relayout();
            return renderedCards();
        });

        assertThat(visible).containsExactlyInAnyOrder(unlabeled, home);
    }

    @Test
    void withoutLabelsKeepsOnlyTheCardsThatHaveNoLabelsAtAll() throws Exception {
        List<CardId> visible = fx(() -> {
            setMode(3); // «Sin etiquetas»
            relayout();
            return renderedCards();
        });

        assertThat(visible).containsExactly(unlabeled);
        assertThat(labelField().isDisable()).as("the label text takes no part").isTrue();
    }

    @Test
    void goingBackToAllRestoresEveryCard() throws Exception {
        List<CardId> visible = fx(() -> {
            setMode(3);
            relayout();
            setMode(0); // «Todas (AND)»
            relayout();
            return renderedCards();
        });

        assertThat(visible).containsExactlyInAnyOrder(unlabeled, work, home);
        assertThat(labelField().isDisable()).isFalse();
    }

    // ------------------------------------------------------------------

    private void setMode(int index) {
        ComboBox<BoardController.LabelFilterMode> combo = modeCombo();
        combo.setValue(combo.getItems().get(index));
    }

    @SuppressWarnings("unchecked")
    private ComboBox<BoardController.LabelFilterMode> modeCombo() {
        Node found = findFirst(controller.root(), node -> node instanceof ComboBox<?> combo
                && i18n.text("filter.mode.empty").equals(combo.getPromptText()));
        assertThat(found).as("the label-mode combo is in the filter bar").isNotNull();
        return (ComboBox<BoardController.LabelFilterMode>) found;
    }

    private TextField labelField() {
        Node found = findFirst(controller.root(), node -> node instanceof TextField field
                && i18n.text("filter.labels.prompt").equals(field.getPromptText()));
        assertThat(found).as("the label text field is in the filter bar").isNotNull();
        return (TextField) found;
    }

    /** Card ids of the cards the board is currently showing. */
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
