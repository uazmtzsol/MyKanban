package com.personalkanban.ui;

import com.personalkanban.AppContext;
import com.personalkanban.application.CardViewSettings;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.WipLimit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.MenuBar;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
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
 * Drives the real user interface: a full {@link BoardController} over a
 * temporary database, rendered in a scene, where the tests fire the very mouse
 * clicks and menu actions a user produces.
 *
 * <p>Session 13 requirements covered here: clicking a card with the mouse must
 * make it the "current" card (the state the arrow keys move, and the one the
 * card shortcuts act on) with exactly one card marked at a time, and the new
 * "Ver" menu must switch between the board and the processes view from the
 * keyboard.</p>
 */
class CardCurrentSelectionTest {

    private AppContext context;
    private BoardController controller;
    private ColumnId column;
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
        Path dir = Files.createTempDirectory("pk-card-current");
        context = AppContext.openAt(dir.resolve("kanban.db"), dir.resolve("config.properties"));
        column = context.boardService().addColumn("Todo", "", BoardColor.BLUE, WipLimit.unlimited());
        first = context.boardService().addCard(column, "Primera", "una descripción", BoardColor.DEFAULT);
        second = context.boardService().addCard(column, "Segunda", "", BoardColor.BLUE);

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

    /** One left click, exactly as the mouse delivers it to the card node. */
    private static void click(Node node) {
        node.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 5, 5, 5, 5,
                MouseButton.PRIMARY, 1,
                false, false, false, false,
                true, false, false,
                false, false, false,
                new PickResult(node, 5, 5)));
    }

    /**
     * Lays the board out again, like the app's next pulse does: a refresh
     * replaces the columns, and the freshly built scroll panes only expose
     * their cards (through their skin) once they have been laid out.
     */
    private void relayout() {
        controller.root().applyCss();
        controller.root().layout();
    }

    private Node cardNode(CardId id) {
        Node found = find(controller.root(), id);
        assertThat(found).as("rendered card node for %s", id.value()).isNotNull();
        return found;
    }

    private static Node find(Node node, CardId id) {
        if (id.equals(node.getUserData())) {
            return node;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node found = find(child, id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    void clickingACardMakesItTheCurrentOne() throws Exception {
        Boolean current = fx(() -> {
            click(cardNode(second));
            return controller.isCardCurrent(second)
                    && cardNode(second).getStyleClass().contains("card-current")
                    && !controller.isCardCurrent(first)
                    && !cardNode(first).getStyleClass().contains("card-current");
        });
        assertThat(current).as("the clicked card became the current one").isTrue();
    }

    @Test
    void onlyTheLastClickedCardStaysCurrent() throws Exception {
        Boolean onlyOne = fx(() -> {
            click(cardNode(first));
            click(cardNode(second));
            return cardNode(second).getStyleClass().contains("card-current")
                    && !cardNode(first).getStyleClass().contains("card-current");
        });
        assertThat(onlyOne).as("exactly one card is current at a time").isTrue();
    }

    @Test
    void theCurrentCardSurvivesARebuild() throws Exception {
        Boolean survived = fx(() -> {
            click(cardNode(second));
            // Any board-wide change rebuilds every card node from scratch.
            controller.onSetBoardCardViewMode(CardViewSettings.Mode.TITLE_PREVIEW);
            relayout();
            return controller.isCardCurrent(second)
                    && cardNode(second).getStyleClass().contains("card-current");
        });
        assertThat(survived).as("the rebuilt node keeps the current highlight").isTrue();
    }

    @Test
    void inSelectionModeAClickSelectsInsteadOfMarkingCurrent() throws Exception {
        Boolean selectedOnly = fx(() -> {
            controller.onToggleSelectionMode(column);
            relayout();
            click(cardNode(first));
            relayout();
            return controller.isCardSelected(first)
                    && cardNode(first).getStyleClass().contains("card-selected")
                    && !controller.isCardCurrent(first);
        });
        assertThat(selectedOnly).as("selection mode keeps its own highlight").isTrue();
    }

    @Test
    void theViewMenuSwitchesModesWithItsShortcuts() throws Exception {
        Boolean switched = fx(() -> {
            MenuBar bar = (MenuBar) controller.root().getChildren().stream()
                    .filter(MenuBar.class::isInstance)
                    .findFirst()
                    .orElseThrow();
            RadioMenuItem boardView = radioWithAccelerator(bar, "Shortcut+Shift+1");
            RadioMenuItem processViewItem = radioWithAccelerator(bar, "Shortcut+Shift+2");
            boolean initiallyBoard = boardView.isSelected() && !processViewItem.isSelected();

            processViewItem.fire();
            boolean movedToProcesses = processViewItem.isSelected() && !boardView.isSelected();

            boardView.fire();
            boolean backToBoard = boardView.isSelected() && !processViewItem.isSelected();
            return initiallyBoard && movedToProcesses && backToBoard;
        });
        assertThat(switched)
                .as("the \"Ver\" menu offers both modes and switches between them")
                .isTrue();
    }

    private static RadioMenuItem radioWithAccelerator(MenuBar bar, String accelerator) {
        KeyCombination combination = KeyCombination.valueOf(accelerator);
        return bar.getMenus().stream()
                .flatMap(menu -> menu.getItems().stream())
                .filter(RadioMenuItem.class::isInstance)
                .map(RadioMenuItem.class::cast)
                .filter(item -> combination.equals(item.getAccelerator()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no menu radio item bound to " + accelerator));
    }
}
