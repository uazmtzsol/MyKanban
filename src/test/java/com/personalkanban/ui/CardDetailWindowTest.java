package com.personalkanban.ui;

import com.personalkanban.AppContext;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.WipLimit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.Window;
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
 * User request (2026-10-13, N-10/N-11): in the card detail window Esc must
 * behave like «Cancelar», and «Añadir nota» / «Guardar nota» must live on the
 * editor's own header row next to the edit/render toggle, while «Eliminar
 * nota» stays at the bottom.
 */
class CardDetailWindowTest {

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
        Path dir = Files.createTempDirectory("pk-card-detail");
        context = AppContext.openAt(dir.resolve("kanban.db"), dir.resolve("config.properties"));
        ColumnId column = context.boardService().addColumn("Todo", "", BoardColor.BLUE,
                WipLimit.unlimited());
        cardId = context.boardService().addCard(column, "Detalle", "descripción",
                BoardColor.DEFAULT);
        i18n = new I18n(context.savedLocale());

        fx(() -> {
            controller = new BoardController(context);
            Scene scene = new Scene(controller.root(), 900, 600);
            controller.bindScene(scene);
            return null;
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        fx(() -> {
            for (Window window : List.copyOf(Window.getWindows())) {
                if (window instanceof Stage stage && stage.isShowing()) {
                    stage.hide();
                }
            }
            return null;
        });
        if (context != null) {
            context.close();
        }
    }

    @Test
    void escapeClosesTheDetailWindowLikeCancel() throws Exception {
        Boolean closed = fx(() -> {
            Stage window = openDetail();
            assertThat(window.isShowing()).isTrue();

            window.getScene().getRoot().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED,
                    "", "", KeyCode.ESCAPE, false, false, false, false));

            return !window.isShowing();
        });
        assertThat(closed).as("Esc behaves like Cancelar and closes the window").isTrue();
    }

    @Test
    void addAndSaveNoteButtonsSitOnTheEditorHeaderNextToTheModeToggle() throws Exception {
        List<String> headerTexts = fx(() -> {
            Stage window = openDetail();
            Node notesPane = notesTabContent(window);
            ToggleButton toggle = (ToggleButton) findFirstNode(notesPane,
                    node -> node instanceof ToggleButton
                            && node.getStyleClass().contains("md-mode-toggle"));
            assertThat(toggle).as("the notes editor has its edit/render toggle").isNotNull();
            Parent header = toggle.getParent();
            List<String> texts = new ArrayList<>();
            for (Node child : header.getChildrenUnmodifiable()) {
                if (child instanceof Button button) {
                    texts.add(button.getText());
                }
            }
            return texts;
        });

        assertThat(headerTexts)
                .as("add/save share the row with the mode toggle")
                .contains(i18n.text("card.notes.add"), i18n.text("card.notes.save"));
    }

    @Test
    void removeNoteStaysAwayFromTheEditorHeader() throws Exception {
        Boolean atTheBottom = fx(() -> {
            Stage window = openDetail();
            Node notesPane = notesTabContent(window);
            Button remove = (Button) findFirstNode(notesPane, node -> node instanceof Button button
                    && i18n.text("card.notes.remove").equals(button.getText()));
            assertThat(remove).as("the remove button exists").isNotNull();

            ToggleButton toggle = (ToggleButton) findFirstNode(notesPane,
                    node -> node instanceof ToggleButton
                            && node.getStyleClass().contains("md-mode-toggle"));
            return remove.getParent() != toggle.getParent();
        });
        assertThat(atTheBottom).as("«Eliminar nota» is not on the editor header").isTrue();
    }

    // ------------------------------------------------------------------

    private Stage openDetail() {
        Card card = context.boardService().board().findCard(cardId).orElseThrow();
        CardDetailWindow.open(card, controller, i18n, context.themeManager());
        return Window.getWindows().stream()
                .filter(Stage.class::isInstance)
                .map(Stage.class::cast)
                .filter(Stage::isShowing)
                .filter(stage -> stage.getTitle() != null
                        && stage.getTitle().startsWith(i18n.text("card.detail.title")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the detail window did not open"));
    }

    private Node notesTabContent(Stage window) {
        TabPane tabs = (TabPane) findFirstNode(window.getScene().getRoot(),
                node -> node instanceof TabPane);
        for (Tab tab : tabs.getTabs()) {
            if (i18n.text("card.tab.notes").equals(tab.getText())) {
                return tab.getContent();
            }
        }
        throw new AssertionError("no notes tab in the detail window");
    }

    private static Node findFirstNode(Node node, java.util.function.Predicate<Node> match) {
        if (match.test(node)) {
            return node;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node found = findFirstNode(child, match);
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
