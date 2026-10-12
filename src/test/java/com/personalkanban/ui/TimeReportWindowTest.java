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
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wiring test for the time report (session 14, B1): the "Ver" menu must open
 * the real window over a real board, and that window must show the tracked
 * time of the board on screen. The numbers themselves are covered by
 * {@code TimeReportTest} and {@code TimeReportExportTest}; here the point is
 * that the feature is reachable from the interface the user actually uses.
 */
class TimeReportWindowTest {

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
        Path dir = Files.createTempDirectory("pk-time-report");
        context = AppContext.openAt(dir.resolve("kanban.db"), dir.resolve("config.properties"));
        ColumnId column = context.boardService().addColumn("Todo", "", BoardColor.BLUE,
                WipLimit.unlimited());
        CardId card = context.boardService().addCard(column, "Con tiempo", "", BoardColor.DEFAULT);
        // One hour of tracked time, logged directly on the aggregate so the
        // report has something to show without depending on the clock.
        Instant end = Instant.now().minus(Duration.ofMinutes(30));
        context.boardService().board().startTimeTracking(card, end.minus(Duration.ofHours(1)));
        context.boardService().board().stopTimeTracking(card, end);

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
    void tearDown() throws Exception {
        // Windows may only be hidden from the FX thread; the report window is
        // a real Stage, so the test closes it before the context goes away.
        fx(() -> {
            for (Window window : List.copyOf(Window.getWindows())) {
                if (window instanceof Stage stage && stage.isShowing()
                        && stage != controllerWindow()) {
                    stage.hide();
                }
            }
            return null;
        });
        if (context != null) {
            context.close();
        }
    }

    private Stage controllerWindow() {
        return controller == null || controller.root().getScene() == null
                ? null
                : (Stage) controller.root().getScene().getWindow();
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

    /** The report entry the "Ver" menu carries (its only non-radio item). */
    private MenuItem reportMenuItem(MenuBar bar) {
        return bar.getMenus().stream()
                .filter(menu -> i18n.text("menu.view").equals(menu.getText()))
                .flatMap(menu -> menu.getItems().stream())
                .filter(item -> !(item instanceof RadioMenuItem))
                .filter(item -> !(item instanceof SeparatorMenuItem))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no report entry in the view menu"));
    }

    @Test
    void theViewMenuOpensTheReportOverTheBoardOnScreen() throws Exception {
        String boardName = context.boardService().boards().stream()
                .filter(board -> board.id().equals(context.boardService().activeBoardId()))
                .findFirst()
                .orElseThrow()
                .name();

        Boolean ok = fx(() -> {
            MenuBar bar = (MenuBar) controller.root().getChildren().stream()
                    .filter(MenuBar.class::isInstance)
                    .findFirst()
                    .orElseThrow();
            MenuItem report = reportMenuItem(bar);
            assertThat(report.getText()).isEqualTo(i18n.text("report.time"));

            report.fire();

            Stage window = Window.getWindows().stream()
                    .filter(Stage.class::isInstance)
                    .map(Stage.class::cast)
                    .filter(Stage::isShowing)
                    .filter(stage -> i18n.text("report.title").equals(stage.getTitle()))
                    .findFirst()
                    .orElse(null);
            if (window == null) {
                return false;
            }
            // The subtitle names the board whose time is being reported, and at
            // least one of the two tables came back with rows.
            return containsLabelText(window.getScene().getRoot(), boardName)
                    && !tablesWithRows(window.getScene().getRoot()).isEmpty();
        });

        assertThat(ok).as("the report window shows the active board's time").isTrue();
    }

    @Test
    void theReportEntryExistsEvenOnABoardWithNothingTracked() throws Exception {
        Boolean present = fx(() -> {
            MenuBar bar = (MenuBar) controller.root().getChildren().stream()
                    .filter(MenuBar.class::isInstance)
                    .findFirst()
                    .orElseThrow();
            return reportMenuItem(bar).isDisable() == false;
        });
        assertThat(present).as("the time report is always reachable").isTrue();
    }

    private static boolean containsLabelText(Node node, String text) {
        if (node instanceof Label label && label.getText() != null
                && label.getText().contains(text)) {
            return true;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (containsLabelText(child, text)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<TableView<?>> tablesWithRows(Node node) {
        List<TableView<?>> found = new java.util.ArrayList<>();
        collectTables(node, found);
        return found.stream().filter(table -> !table.getItems().isEmpty()).toList();
    }

    private static void collectTables(Node node, List<TableView<?>> found) {
        if (node instanceof TableView<?> table) {
            found.add(table);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectTables(child, found);
            }
        }
    }
}
