package com.personalkanban.ui;

import com.personalkanban.AppContext;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.WipLimit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
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
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User request (2026-10-13, N-3): the «add column» button left the toolbar —
 * the action lives in the Boards menu and in every column's own menu, as
 * «add column to the left / to the right».
 *
 * <p>The placement itself is covered by {@code ColumnInsertAtTest}; here the
 * real scene graph is inspected, and the entries are checked to be wired to
 * an action (clicking them opens the column dialog, which cannot be driven
 * from a headless test).</p>
 */
class ColumnAddMenuTest {

    private AppContext context;
    private BoardController controller;
    private I18n i18n;

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
        Path dir = Files.createTempDirectory("pk-column-add");
        context = AppContext.openAt(dir.resolve("kanban.db"), dir.resolve("config.properties"));
        context.boardService().addColumn("A", "", BoardColor.BLUE, WipLimit.unlimited());
        context.boardService().addColumn("C", "", BoardColor.BLUE, WipLimit.unlimited());

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
    void theToolbarNoLongerOffersAddingAColumn() throws Exception {
        fx(() -> {
            Node toolbar = findFirst(controller.root(),
                    node -> node.getStyleClass().contains("toolbar"));
            assertThat(toolbar).as("the toolbar is on screen").isNotNull();

            List<Button> buttons = new ArrayList<>();
            collectButtons(toolbar, buttons);
            assertThat(buttons).as("the toolbar still has its buttons").isNotEmpty();
            assertThat(buttons)
                    .as("no toolbar button adds a column any more")
                    .noneMatch(button -> button.getTooltip() != null
                            && i18n.text("board.add.column").equals(button.getTooltip().getText()));
            return null;
        });
    }

    @Test
    void theBoardsMenuOffersAddingAColumn() throws Exception {
        fx(() -> {
            MenuItem addColumn = boardsMenuItem(i18n.text("board.add.column"));
            assertThat(addColumn).as("«Add column» is in the Boards menu").isNotNull();
            assertThat(addColumn.getOnAction()).as("and it does something").isNotNull();
            return null;
        });
    }

    @Test
    void everyColumnOffersAddingAColumnToItsSide() throws Exception {
        fx(() -> {
            List<Node> menus = new ArrayList<>();
            collectColumnMenus(controller.root(), menus);
            assertThat(menus).as("one menu per column").hasSize(2);

            for (Node node : menus) {
                MenuButton menu = (MenuButton) node;
                MenuItem left = itemOf(menu.getItems(), i18n.text("column.add.left"));
                MenuItem right = itemOf(menu.getItems(), i18n.text("column.add.right"));
                assertThat(left).as("«add to the left» in the column menu").isNotNull();
                assertThat(right).as("«add to the right» in the column menu").isNotNull();
                assertThat(left.getOnAction()).isNotNull();
                assertThat(right.getOnAction()).isNotNull();
            }
            return null;
        });
    }

    // ------------------------------------------------------------------

    private MenuItem boardsMenuItem(String label) {
        Node bar = findFirst(controller.root(), node -> node instanceof MenuBar);
        assertThat(bar).as("the menu bar is on screen").isNotNull();
        for (Menu menu : ((MenuBar) bar).getMenus()) {
            if (i18n.text("menu.boards").equals(menu.getText())) {
                return itemOf(menu.getItems(), label);
            }
        }
        return null;
    }

    private static MenuItem itemOf(List<MenuItem> items, String label) {
        return items.stream()
                .filter(item -> label.equals(item.getText()))
                .findFirst()
                .orElse(null);
    }

    private static void collectButtons(Node node, List<Button> found) {
        if (node instanceof Button button) {
            found.add(button);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectButtons(child, found);
            }
        }
    }

    private static void collectColumnMenus(Node node, List<Node> found) {
        if (node instanceof MenuButton menu && menu.getStyleClass().contains("pk-column-menu")
                // A shown ScrollPane exposes its content BOTH as a child and
                // through getContent(); without this the same menu is
                // collected twice.
                && !found.contains(menu)) {
            found.add(menu);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectColumnMenus(child, found);
            }
        }
        if (node instanceof ScrollPane scroll && scroll.getContent() != null) {
            collectColumnMenus(scroll.getContent(), found);
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
