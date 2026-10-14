package com.personalkanban.ui;

import com.personalkanban.AppContext;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.WipLimit;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wiring test for the reorganized main menu (session 16, user request): the
 * bar carries the new "Editar" and "Tarjetas" menus in the agreed order, and
 * the actions that used to be reachable only from the toolbar / a card's
 * context menu are now reachable from the menu bar too — which is what makes
 * them available to the keyboard user (Alt opens the bar).
 */
class MainMenuStructureTest {

    private AppContext context;
    private BoardController controller;
    private I18n i18n;
    private CardId cardId;

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
        Path dir = Files.createTempDirectory("pk-main-menu");
        context = AppContext.openAt(dir.resolve("kanban.db"), dir.resolve("config.properties"));
        ColumnId column = context.boardService().addColumn("Todo", "", BoardColor.BLUE,
                WipLimit.unlimited());
        cardId = context.boardService().addCard(column, "Una tarjeta", "", BoardColor.DEFAULT);

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

    private MenuBar menuBar() {
        return (MenuBar) controller.root().getChildren().stream()
                .filter(MenuBar.class::isInstance)
                .findFirst()
                .orElseThrow();
    }

    private Menu menuByKey(MenuBar bar, String key) {
        String text = i18n.text(key);
        return bar.getMenus().stream()
                .filter(menu -> text.equals(menu.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no menu named " + key));
    }

    private MenuItem itemByText(Menu menu, String key) {
        String text = i18n.text(key);
        return menu.getItems().stream()
                .filter(item -> text.equals(item.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no item " + key + " in menu " + menu.getText()));
    }

    private Menu submenuByText(Menu menu, String key) {
        String text = i18n.text(key);
        return menu.getItems().stream()
                .filter(Menu.class::isInstance)
                .map(Menu.class::cast)
                .filter(sub -> text.equals(sub.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no submenu " + key + " in menu " + menu.getText()));
    }

    @Test
    void theBarCarriesTheReorganizedMenusInOrder() throws Exception {
        List<String> expected = List.of(
                i18n.text("menu.database"),
                i18n.text("menu.edit"),
                i18n.text("menu.boards"),
                i18n.text("menu.view"),
                i18n.text("menu.cards"),
                i18n.text("menu.processes"),
                i18n.text("menu.labels"),
                i18n.text("menu.language"),
                i18n.text("menu.help"));

        List<String> actual = fx(() -> menuBar().getMenus().stream()
                .map(Menu::getText)
                .collect(Collectors.toList()));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void theEditMenuGroupsTheEditingActions() throws Exception {
        List<String> items = fx(() -> {
            Menu edit = menuByKey(menuBar(), "menu.edit");
            return edit.getItems().stream()
                    .map(MenuItem::getText)
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.toList());
        });

        assertThat(items).contains(
                i18n.text("menu.undo"),
                i18n.text("menu.redo"),
                i18n.text("edit.labels"),
                i18n.text("card.archive.menu"),
                i18n.text("edit.reset.cardview"));
    }

    @Test
    void theViewMenuReachesEveryWayOfLookingAtTheBoard() throws Exception {
        Boolean ok = fx(() -> {
            Menu view = menuByKey(menuBar(), "menu.view");
            itemByText(view, "view.board");
            itemByText(view, "view.processes");
            itemByText(view, "filter.archived.show");
            itemByText(view, "report.time");
            itemByText(view, "search.menu");
            itemByText(view, "menu.dark.mode");
            Menu cardView = submenuByText(view, "menu.cardview");
            long radios = cardView.getItems().stream()
                    .filter(RadioMenuItem.class::isInstance)
                    .count();
            return radios == 3;
        });

        assertThat(ok).as("the card-view submenu offers the three modes").isTrue();
    }

    @Test
    void theCardsMenuIsDisabledWithoutACurrentCardAndEnabledWithOne() throws Exception {
        Boolean disabledWithout = fx(() ->
                itemByText(menuByKey(menuBar(), "menu.cards"), "card.open").isDisable());
        assertThat(disabledWithout)
                .as("with no current card the Cards menu entries are inert")
                .isTrue();

        Boolean enabledWith = fx(() -> {
            controller.onCardSelected(cardId);
            Menu cards = menuByKey(menuBar(), "menu.cards");
            // The menu refreshes itself when it opens; drive that directly.
            cards.getOnShowing().handle(null);
            return !itemByText(cards, "card.open").isDisable()
                    && !itemByText(cards, "card.delete").isDisable();
        });
        assertThat(enabledWith)
                .as("with a current card the Cards menu acts on it")
                .isTrue();
    }

    @Test
    void theCardsMenuOffersTheContextMenuActions() throws Exception {
        Boolean ok = fx(() -> {
            controller.onCardSelected(cardId);
            Menu cards = menuByKey(menuBar(), "menu.cards");
            cards.getOnShowing().handle(null);
            itemByText(cards, "card.open");
            itemByText(cards, "card.edit");
            itemByText(cards, "card.reminders.menu");
            itemByText(cards, "card.archive.menu");
            itemByText(cards, "card.export.menu");
            itemByText(cards, "card.transfer.menu");
            itemByText(cards, "card.delete");
            submenuByText(cards, "card.reschedule.menu");
            submenuByText(cards, "card.process.label");
            return true;
        });

        assertThat(ok).isTrue();
    }
}
