package com.personalkanban.ui;

import com.personalkanban.AppContext;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.CardReminder;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.WipLimit;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wiring of the reminders (T23 · B10) through the real UI: the 🔔 on the card,
 * the pending band, the startup message window (with the card's title as the
 * message) and the detail window's list, all driven by the same board the user
 * sees.
 */
class ReminderUiTest {

    private AppContext context;
    private BoardController controller;
    private I18n i18n;
    private CardId informe;
    private CardId undated;
    private CardReminder dueMoment;

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
        Path dir = Files.createTempDirectory("pk-reminders");
        context = AppContext.openAt(dir.resolve("kanban.db"), dir.resolve("config.properties"));
        ColumnId column = context.boardService().addColumn("Todo", "", BoardColor.BLUE,
                WipLimit.unlimited());
        LocalDate today = LocalDate.now();
        informe = context.boardService().addCard(column, "Informe trimestral", "", BoardColor.DEFAULT,
                today.plusDays(5), List.of());
        undated = context.boardService().addCard(column, "Sin fecha", "", BoardColor.DEFAULT);

        // One moment already due (an hour ago) and one still ahead: the app was
        // closed when the first one arrived, which is the case the request is
        // about.
        context.boardService().addReminder(informe, today,
                LocalTime.now().minusHours(1).withSecond(0).withNano(0));
        context.boardService().addReminder(informe, today.plusDays(2), null);
        dueMoment = context.boardService().remindersOf(informe).get(0);

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
    void tearDown() throws Exception {
        fx(() -> {
            if (controller != null) {
                controller.stopReminderTicker();
            }
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
    void theStartupCheckShowsTheMessageWithTheCardsTitle() throws Exception {
        String header = fx(() -> {
            controller.onStartup();
            Stage window = reminderWindow();
            assertThat(window.isShowing()).as("the pending list opened on startup").isTrue();
            assertThat(texts(window.getScene().getRoot()))
                    .as("the message is the card's own title")
                    .contains("Informe trimestral");
            return ((Label) findFirst(window.getScene().getRoot(),
                    node -> node instanceof Label label
                            && label.getStyleClass().contains("detail-caption"))).getText();
        });

        assertThat(header).isEqualTo(i18n.text("reminders.notice.header", 1));
    }

    @Test
    void attendingTheLastMomentClosesTheWindow() throws Exception {
        Boolean hidden = fx(() -> {
            controller.onStartup();
            Stage window = reminderWindow();
            Button attendAll = (Button) findFirst(window.getScene().getRoot(),
                    node -> node instanceof Button button
                            && i18n.text("reminders.ack").equals(button.getText()));
            assertThat(attendAll).as("the window offers «attend all»").isNotNull();

            attendAll.fire();
            relayout();
            return !window.isShowing();
        });

        assertThat(hidden).as("nothing left to attend, so nothing left to show").isTrue();
        assertThat(context.boardService().remindersOf(informe))
                .allSatisfy(reminder -> assertThat(reminder.acknowledged()).isTrue());
    }

    @Test
    void theBandCountsThePendingMomentsAndHidesWhenThereIsNothingLeft() throws Exception {
        fx(() -> {
            Label band = reminderBand();
            assertThat(band.isVisible()).isTrue();
            assertThat(band.getText()).isEqualTo(i18n.text("reminders.notice.pending", 1));

            controller.onAcknowledgeAllReminders(List.of(informe));

            assertThat(reminderBand().isVisible()).as("attended → nothing pending").isFalse();
            return null;
        });
    }

    @Test
    void theCardFrontCarriesTheBellWithTheNumberOfMoments() throws Exception {
        fx(() -> {
            Label badge = reminderBadge();
            assertThat(badge.getText()).as("both moments, pending").isEqualTo("\uD83D\uDD14 2");
            assertThat(badge.getStyleClass()).contains("card-reminders-pending");

            controller.onAcknowledgeAllReminders(List.of(informe));
            relayout();

            Label settled = reminderBadge();
            assertThat(settled.getText()).as("the moments stay on the card").isEqualTo("\uD83D\uDD14 2");
            assertThat(settled.getStyleClass())
                    .as("attended moments no longer shout").contains("card-reminders");
            return null;
        });
    }

    @Test
    void theDetailWindowListsTheMomentsAndLetsThemBeAttended() throws Exception {
        List<String> shown = fx(() -> {
            Stage window = openDetail(informe);
            Node pane = reminderTabContent(window);
            List<String> texts = texts(pane);
            assertThat(texts).contains(dueMoment.describe());
            return texts;
        });

        assertThat(shown).anySatisfy(text -> assertThat(text).contains(LocalDate.now().plusDays(2).toString()));

        fx(() -> {
            Stage window = openDetail(informe);
            CheckBox first = (CheckBox) findFirst(reminderTabContent(window),
                    node -> node instanceof CheckBox);
            assertThat(first).as("every moment has an attended box").isNotNull();
            // fire() flips the box and runs the handler, exactly like a click.
            first.fire();
            return null;
        });

        assertThat(context.boardService().remindersOf(informe).get(0).acknowledged()).isTrue();
    }

    @Test
    void aCardWithoutADeadlineExplainsWhatIsMissingInsteadOfOfferingTheCalendar() throws Exception {
        String message = fx(() -> {
            Stage window = openDetail(undated);
            Node pane = reminderTabContent(window);
            Button calendar = (Button) findFirst(pane, node -> node instanceof Button button
                    && i18n.text("reminders.calendar").equals(button.getText()));
            assertThat(calendar).as("the calendar button is there").isNotNull();
            assertThat(calendar.isDisabled()).as("but it cannot work without a deadline").isTrue();
            return texts(pane).stream()
                    .filter(text -> text.equals(i18n.text("reminders.no.due")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("the reason was not explained"));
        });

        assertThat(message).isEqualTo(i18n.text("reminders.no.due"));
    }

    // ------------------------------------------------------------------

    private Stage reminderWindow() {
        return Window.getWindows().stream()
                .filter(Stage.class::isInstance)
                .map(Stage.class::cast)
                .filter(Stage::isShowing)
                .filter(stage -> i18n.text("reminders.window.title").equals(stage.getTitle()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the pending reminders window did not open"));
    }

    private Label reminderBand() {
        Node found = findFirst(controller.root(), node -> node instanceof Label
                && node.getStyleClass().contains("reminder-notice"));
        assertThat(found).as("the reminder band exists").isNotNull();
        return (Label) found;
    }

    private Label reminderBadge() {
        Node found = findFirst(controller.root(), node -> node instanceof Label label
                && (label.getStyleClass().contains("card-reminders-pending")
                || label.getStyleClass().contains("card-reminders")));
        assertThat(found).as("the card front carries the bell badge").isNotNull();
        return (Label) found;
    }

    private Stage openDetail(CardId cardId) {
        var card = context.boardService().board().findCard(cardId).orElseThrow();
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

    private Node reminderTabContent(Stage window) {
        TabPane tabs = (TabPane) findFirst(window.getScene().getRoot(),
                node -> node instanceof TabPane);
        for (Tab tab : tabs.getTabs()) {
            if (i18n.text("card.tab.reminders").equals(tab.getText())) {
                return tab.getContent();
            }
        }
        throw new AssertionError("the detail window has no reminders tab");
    }

    private static List<String> texts(Node node) {
        List<String> found = new ArrayList<>();
        collectTexts(node, found);
        return found;
    }

    private static void collectTexts(Node node, List<String> found) {
        if (node instanceof Label label && label.getText() != null) {
            found.add(label.getText());
        }
        if (node instanceof Button button && button.getText() != null) {
            found.add(button.getText());
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectTexts(child, found);
            }
        }
    }

    private void relayout() {
        controller.root().applyCss();
        controller.root().layout();
    }

    private static Node findFirst(Node node, Predicate<Node> match) {
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
