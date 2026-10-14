package com.personalkanban.ui;

import com.personalkanban.AppContext;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.ProcessId;
import com.personalkanban.domain.board.WipLimit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
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
 * T9 · N-5 (user request): a card can be assigned to a process straight from
 * its own menu, without entering selection mode. The submenu is built by the
 * same code the card front uses, so its entries and labels cannot drift from
 * the bulk bar's.
 */
class CardProcessMenuTest {

    private AppContext context;
    private BoardController controller;
    private I18n i18n;
    private CardId cardId;
    private CardId other;
    private ProcessId first;
    private ProcessId second;

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
        Path dir = Files.createTempDirectory("pk-card-process");
        context = AppContext.openAt(dir.resolve("kanban.db"), dir.resolve("config.properties"));
        ColumnId column = context.boardService().addColumn("Todo", "", BoardColor.BLUE,
                WipLimit.unlimited());
        cardId = context.boardService().addCard(column, "Tarjeta", "", BoardColor.DEFAULT);
        other = context.boardService().addCard(column, "Otra", "", BoardColor.DEFAULT);
        first = context.boardService().addProcess("#uno");
        second = context.boardService().addProcess("#dos");

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
    void theMenuOffersEveryProcessAndSaysWhichOneIsCurrent() throws Exception {
        fx(() -> {
            Menu menu = processMenu();

            assertThat(menu.getText()).isEqualTo(i18n.text("card.process.label"));
            assertThat(labels(menu)).containsExactly(
                    i18n.text("card.process.none"), "\u26AD #uno", "\u26AD #dos");
            assertThat(checked(menu)).containsExactly(i18n.text("card.process.none"));
            return null;
        });
    }

    @Test
    void choosingAProcessAssignsThisOneCardAndUndoBringsItBack() throws Exception {
        fx(() -> {
            item(entry("#dos")).fire();

            assertThat(processOf(cardId)).as("the click assigns the card").isEqualTo(second);
            assertThat(processOf(other)).as("and only that card").isNull();

            context.boardService().undo();

            assertThat(processOf(cardId)).as("one Ctrl+Z undoes the assignment").isNull();
            return null;
        });
    }

    /**
     * The bulk bar promises that leaving a process never destroys the card's
     * arrow relations; the single-card route must keep that promise too.
     */
    @Test
    void leavingTheProcessKeepsTheCardsRelations() throws Exception {
        fx(() -> {
            item(entry("#uno")).fire();
            context.boardService().linkCards(cardId, other);

            item(i18n.text("card.process.none")).fire();

            assertThat(processOf(cardId)).isNull();
            assertThat(context.boardService().board().hasLink(cardId, other))
                    .as("the relation survives").isTrue();
            return null;
        });
    }

    @Test
    void theCheckedEntryFollowsTheCard() throws Exception {
        fx(() -> {
            item(entry("#uno")).fire();
            controller.onSelectView(false);

            assertThat(checked(processMenu())).containsExactly("\u26AD #uno");
            return null;
        });
    }

    // ------------------------------------------------------------------

    private Menu processMenu() {
        Card card = context.boardService().board().findCard(cardId).orElseThrow();
        return CardViewBuilder.processMenu(i18n, controller, card);
    }

    /** The label a process gets in the menu, exactly like the card's chip. */
    private static String entry(String processName) {
        return "\u26AD " + processName;
    }

    private MenuItem item(String label) {
        MenuItem found = processMenu().getItems().stream()
                .filter(entry -> label.equals(entry.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no entry «" + label + "» in the menu"));
        assertThat(found).isInstanceOf(RadioMenuItem.class);
        return found;
    }

    private static List<String> labels(Menu menu) {
        return menu.getItems().stream().map(MenuItem::getText).toList();
    }

    private static List<String> checked(Menu menu) {
        return menu.getItems().stream()
                .filter(RadioMenuItem.class::isInstance)
                .map(RadioMenuItem.class::cast)
                .filter(RadioMenuItem::isSelected)
                .map(MenuItem::getText)
                .toList();
    }

    private ProcessId processOf(CardId id) {
        return context.boardService().board().findCard(id).orElseThrow().processId();
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
