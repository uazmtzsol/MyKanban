package com.personalkanban.ui;

import com.personalkanban.AppContext;
import com.personalkanban.application.CardViewSettings;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ChecklistItem;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.WipLimit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
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
 * User request (2026-10-13, N-14): every card gets a button to choose what the
 * column shows for it — the description (as before), the tasks (checkable
 * without opening the card) or the notes («comentarios», per the user's
 * clarification). Driven through the real card, the real menu and the real
 * rendered nodes.
 */
class CardContentUiTest {

    private static final String DESCRIPTION = "Una descripción de la tarjeta";

    private AppContext context;
    private BoardController controller;
    private I18n i18n;
    private ColumnId column;
    private CardId card;

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
        Path dir = Files.createTempDirectory("pk-card-content");
        context = AppContext.openAt(dir.resolve("kanban.db"), dir.resolve("config.properties"));
        column = context.boardService().addColumn("Todo", "", BoardColor.BLUE, WipLimit.unlimited());
        card = context.boardService().addCard(column, "Tarjeta", DESCRIPTION, BoardColor.DEFAULT,
                null, List.of(), "%% Compras\n\nLeche, pan",
                List.of(ChecklistItem.newItem("alpha"), ChecklistItem.newItem("beta")), null);

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
    void theCardFrontFollowsTheChoice() throws Exception {
        fx(() -> {
            assertThat(contentMenu().getItems()).extracting(MenuItem::getText).containsExactly(
                    i18n.text("card.content.description"),
                    i18n.text("card.content.checklist"),
                    i18n.text("card.content.notes"));
            assertThat(textWithStyle("card-description"))
                    .as("the card shows its description until the user says otherwise")
                    .isNotNull();
            assertThat(checkBox("alpha")).as("tasks are not on the front by default").isNull();

            choose(CardViewSettings.Content.CHECKLIST);
            relayout();
            assertThat(checkBox("alpha")).isNotNull();
            assertThat(checkBox("beta")).isNotNull();
            assertThat(textWithStyle("card-description"))
                    .as("the description steps aside for the tasks").isNull();

            choose(CardViewSettings.Content.NOTES);
            relayout();
            Label note = (Label) textWithStyle("card-notes-preview-text");
            assertThat(note).as("the notes are on the front").isNotNull();
            assertThat(note.getText()).contains("Compras").contains("Leche, pan");
            assertThat(checkBox("alpha")).as("the tasks stepped aside").isNull();

            choose(CardViewSettings.Content.DESCRIPTION);
            relayout();
            assertThat(textWithStyle("card-description")).isNotNull();
            assertThat(checkBox("alpha")).isNull();
            assertThat(controller.currentCardViewSettings().hasContentOverride(card.value()))
                    .as("going back to the description stores no override")
                    .isFalse();
            return null;
        });
    }

    @Test
    void checkingATaskOnTheCardCommitsItAndUndoRestoresIt() throws Exception {
        fx(() -> {
            choose(CardViewSettings.Content.CHECKLIST);
            relayout();
            CheckBox beta = checkBox("beta");
            assertThat(beta).isNotNull();

            // CheckBox.fire() flips the selection and then fires onAction,
            // which is exactly what a user click does.
            beta.fire();
            relayout();

            assertThat(taskDone("beta")).as("checking on the card is a real change").isTrue();
            assertThat(checkBox("beta").isSelected()).as("and the card shows it checked").isTrue();

            context.boardService().undo();
            // Ctrl+Z goes through the controller, which repaints; here the
            // board is re-rendered with a no-op view-mode intent (settings
            // are not part of the undo history) so the card is rebuilt from
            // the model and can be inspected again.
            controller.onSetBoardCardViewMode(controller.currentCardViewSettings().boardDefault());
            relayout();
            assertThat(taskDone("beta")).as("one Ctrl+Z is enough").isFalse();
            assertThat(checkBox("beta").isSelected())
                    .as("and the card follows the model again").isFalse();
            return null;
        });
    }

    /**
     * The choice survives a restart: it lives in the board's view settings,
     * which the service persists per board.
     */
    @Test
    void theChoiceIsPersistedPerCard() throws Exception {
        fx(() -> {
            choose(CardViewSettings.Content.NOTES);
            relayout();
            return null;
        });

        assertThat(context.boardService().cardViewSettingsOf(context.boardService().activeBoardId())
                .effectiveContent(card.value())).isEqualTo(CardViewSettings.Content.NOTES);
    }

    // ------------------------------------------------------------------

    private boolean taskDone(String text) {
        return context.boardService().board().findCard(card).orElseThrow().checklist().stream()
                .filter(item -> item.text().equals(text))
                .findFirst()
                .orElseThrow()
                .done();
    }

    /** The content menu of the card, e.g. to inspect or fire its items. */
    private MenuButton contentMenu() {
        Node found = findFirst(cardNode(), node -> node instanceof MenuButton menu
                && menu.getStyleClass().contains("pk-card-content"));
        assertThat(found).as("the card carries its content button").isNotNull();
        return (MenuButton) found;
    }

    /** Picks one content option from the card's own button. */
    private void choose(CardViewSettings.Content content) {
        MenuItem item = contentMenu().getItems().stream()
                .filter(candidate -> candidate.getUserData() == content)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no menu entry for " + content));
        item.fire();
    }

    private CheckBox checkBox(String text) {
        Node found = findFirst(cardNode(), node -> node instanceof CheckBox box
                && text.equals(box.getText()));
        return (CheckBox) found;
    }

    private Node textWithStyle(String styleClass) {
        return findFirst(cardNode(), node -> node.getStyleClass().contains(styleClass));
    }

    private Node cardNode() {
        Node found = findFirst(controller.root(), node -> node.getUserData() instanceof CardId);
        assertThat(found).as("the card is on screen").isNotNull();
        return found;
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
        // A freshly built ScrollPane exposes its content only through
        // getContent() until its skin has been created.
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
