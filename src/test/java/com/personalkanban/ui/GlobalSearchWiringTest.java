package com.personalkanban.ui;

import com.personalkanban.AppContext;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.WipLimit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCombination;
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
 * Wiring test for the global search (session 14, B2): the feature is reachable
 * from all three places the user can look at (toolbar, "Ver" menu, keyboard)
 * and opening a hit really lands on the card — including when the hit lives in
 * a board that is not on screen.
 */
class GlobalSearchWiringTest {

    private AppContext context;
    private BoardController controller;
    private Scene scene;
    private I18n i18n;

    private BoardId home;
    private BoardId archive;
    private CardId archivedCard;

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
    void setUpBoards() throws Exception {
        Path dir = Files.createTempDirectory("pk-global-search");
        context = AppContext.openAt(dir.resolve("kanban.db"), dir.resolve("config.properties"));

        home = context.boardService().activeBoardId();
        ColumnId todo = context.boardService().addColumn("Todo", "", BoardColor.BLUE,
                WipLimit.unlimited());
        context.boardService().addCard(todo, "Tarjeta local", "", BoardColor.BLUE);

        archive = context.boardService().createBoard("Archivo");
        context.boardService().openBoard(archive);
        ColumnId inbox = context.boardService().addColumn("Bandeja", "", BoardColor.GREEN,
                WipLimit.unlimited());
        archivedCard = context.boardService().addCard(inbox, "Factura antigua de la luz", "",
                BoardColor.GREEN);
        context.boardService().openBoard(home);

        i18n = new I18n(context.savedLocale());
        fx(() -> {
            controller = new BoardController(context);
            scene = new Scene(controller.root(), 900, 600);
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

    /** Lets the deferred focus pass (Platform.runLater) run before asserting. */
    private void flushFx() throws Exception {
        fx(() -> null);
        fx(() -> null);
    }

    private MenuBar menuBar() {
        return (MenuBar) controller.root().getChildren().stream()
                .filter(MenuBar.class::isInstance)
                .findFirst()
                .orElseThrow();
    }

    private MenuItem viewMenuItem(String text) {
        return menuBar().getMenus().stream()
                .filter(menu -> i18n.text("menu.view").equals(menu.getText()))
                .flatMap(menu -> menu.getItems().stream())
                .filter(item -> text.equals(item.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no menu item named " + text));
    }

    @Test
    void theSearchIsReachableFromTheMenuTheToolbarAndTheKeyboard() throws Exception {
        Boolean reachable = fx(() -> {
            boolean inMenu = viewMenuItem(i18n.text("search.menu")) != null;
            boolean accelerator = scene.getAccelerators().containsKey(
                    KeyCombination.valueOf("Shortcut+Shift+F"));
            boolean inToolbar = findToolbarButton(i18n.text("search.tooltip")) != null;
            return inMenu && accelerator && inToolbar;
        });

        assertThat(reachable)
                .as("search is in the Ver menu, has Ctrl+Shift+F and a toolbar button")
                .isTrue();
    }

    @Test
    void theReportAndTheSearchShareTheViewMenu() throws Exception {
        Boolean both = fx(() -> viewMenuItem(i18n.text("report.time")) != null
                && viewMenuItem(i18n.text("search.menu")) != null);

        assertThat(both).isTrue();
    }

    @Test
    void openingAHitOfAnotherBoardSwitchesBoardAndMakesItCurrent() throws Exception {
        assertThat(context.boardService().activeBoardId()).isEqualTo(home);

        fx(() -> {
            controller.focusCardInBoard(archive, archivedCard);
            return null;
        });
        flushFx();

        assertThat(context.boardService().activeBoardId())
                .as("the board of the hit came on screen")
                .isEqualTo(archive);
        Boolean current = fx(() -> {
            controller.root().applyCss();
            controller.root().layout();
            return controller.isCardCurrent(archivedCard);
        });
        assertThat(current).as("the hit is the current card").isTrue();
    }

    @Test
    void openingAHitOfTheBoardOnScreenKeepsTheBoardAndMakesItCurrent() throws Exception {
        CardId local = context.boardService().board().allCards().get(0).id();

        fx(() -> {
            controller.focusCardInBoard(home, local);
            return null;
        });
        flushFx();

        assertThat(context.boardService().activeBoardId()).isEqualTo(home);
        assertThat(fx(() -> controller.isCardCurrent(local))).isTrue();
    }

    private Button findToolbarButton(String tooltipText) {
        return findButton(controller.root(), tooltipText);
    }

    private static Button findButton(Node node, String tooltipText) {
        if (node instanceof Button button && button.getTooltip() != null
                && tooltipText.equals(button.getTooltip().getText())) {
            return button;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Button found = findButton(child, tooltipText);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
