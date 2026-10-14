package com.personalkanban.ui;

import com.personalkanban.domain.board.CardReminder;
import com.personalkanban.ui.theme.ThemeManager;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ToggleButton;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The wiring of the reminder calendar dialog (T23 · B10): the month grid it
 * paints, the daily / weekly generators over the [today, due date] range, the
 * hour it starts from and the guard that refuses an unusable one. The dialog is
 * built but never shown, so the modal part stays out of the test.
 */
class ReminderDialogTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14); // Wednesday
    private static final LocalDate DUE = LocalDate.of(2026, 10, 26);   // Monday

    private final I18n i18n = new I18n(Locale.of("es"));
    private final ThemeManager themeManager = new ThemeManager(ThemeManager.Theme.LIGHT);

    @BeforeAll
    static void startJavaFxToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // Toolkit already initialized by another test in this JVM.
        }
        Platform.setImplicitExit(false);
    }

    @Test
    void theGridOffersExactlyTheDaysBetweenTodayAndTheDeadline() throws Exception {
        fx(() -> {
            List<ToggleButton> cells = dayCells(buildDialog(List.of()));

            assertThat(cells).as("October 2026 spans five Monday-first weeks").hasSize(35);
            assertThat(datesOf(cells, true))
                    .as("the whole request range is offered, and nothing else")
                    .hasSize(13).startsWith(TODAY).endsWith(DUE);
            assertThat(datesOf(cells, false))
                    .as("the neighbouring weeks are painted but not offered")
                    .isNotEmpty();
            return null;
        });
    }

    @Test
    void theDaysThatAlreadyHaveAMomentStartSelected() throws Exception {
        fx(() -> {
            List<ToggleButton> cells = dayCells(buildDialog(List.of(
                    new CardReminder("r-1", LocalDate.of(2026, 10, 16), null, false))));
            assertThat(selectedDates(cells)).containsExactly(LocalDate.of(2026, 10, 16));
            return null;
        });
    }

    @Test
    void dailyFillsTheRangeAndWeeklyTakesTheDeadlineWeekday() throws Exception {
        fx(() -> {
            Dialog<List<LocalDate>> dialog = buildDialog(List.of());
            assertThat(DUE.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);

            fire(dialog, "reminders.auto.daily");
            assertThat(selectedDates(dayCells(dialog)))
                    .as("daily = every day from today to the deadline")
                    .hasSize(13).startsWith(TODAY).endsWith(DUE);

            fire(dialog, "reminders.auto.weekly");
            assertThat(selectedDates(dayCells(dialog)))
                    .as("weekly = the deadline's weekday, every week")
                    .containsExactly(LocalDate.of(2026, 10, 19), DUE);
            return null;
        });
    }

    @Test
    void clearingLeavesNothingSelected() throws Exception {
        fx(() -> {
            Dialog<List<LocalDate>> dialog = buildDialog(List.of(
                    new CardReminder("r-1", LocalDate.of(2026, 10, 16), null, false)));

            fire(dialog, "reminders.clear");

            assertThat(selectedDates(dayCells(dialog))).isEmpty();
            return null;
        });
    }

    @Test
    void aCellTogglesItsDayOnAndOffOnAClick() throws Exception {
        fx(() -> {
            Dialog<List<LocalDate>> dialog = buildDialog(List.of());
            ToggleButton friday = cell(dialog, LocalDate.of(2026, 10, 16));

            // fire() is exactly what a click runs: it flips the button and then
            // calls the handler with the new state.
            friday.fire();
            assertThat(selectedDates(dayCells(dialog))).containsExactly(LocalDate.of(2026, 10, 16));

            friday.fire();
            assertThat(selectedDates(dayCells(dialog))).isEmpty();
            return null;
        });
    }

    @Test
    void aDayOutsideTheRangeNeverEntersTheSelection() throws Exception {
        fx(() -> {
            Dialog<List<LocalDate>> dialog = buildDialog(List.of());
            ToggleButton yesterday = cell(dialog, TODAY.minusDays(1));

            assertThat(yesterday.isDisabled()).as("yesterday is out of range").isTrue();
            // Disabled buttons do not even fire, so the range check inside the
            // handler is what keeps an out-of-range day out of a saved schedule.
            yesterday.setSelected(true);
            yesterday.getOnAction().handle(new javafx.event.ActionEvent());

            assertThat(selectedDates(dayCells(dialog))).isEmpty();
            return null;
        });
    }

    @Test
    void theHourFieldStartsFromTheScheduleAndAnAllDayOneStaysEmpty() throws Exception {
        fx(() -> {
            ReminderDialog withHour = new ReminderDialog(i18n, "Informe", TODAY, DUE, List.of(
                    new CardReminder("r-1", LocalDate.of(2026, 10, 16), LocalTime.of(18, 30), false),
                    new CardReminder("r-2", LocalDate.of(2026, 10, 17), LocalTime.of(18, 30), false)));
            withHour.buildDialog(themeManager);
            assertThat(withHour.timeField().getText())
                    .as("a schedule that shares one hour keeps it").isEqualTo("18:30");

            ReminderDialog allDay = new ReminderDialog(i18n, "Informe", TODAY, DUE, List.of(
                    new CardReminder("r-1", LocalDate.of(2026, 10, 16), null, false)));
            allDay.buildDialog(themeManager);
            assertThat(allDay.timeField().getText())
                    .as("an all-day schedule starts with an empty hour").isEmpty();
            return null;
        });
    }

    @Test
    void anUnusableHourIsRefusedWithAnExplanationInsteadOfSaving() throws Exception {
        fx(() -> {
            ReminderDialog model = new ReminderDialog(i18n, "Informe", TODAY, DUE, List.of());
            Dialog<List<LocalDate>> dialog = model.buildDialog(themeManager);
            model.timeField().setText("mediodía");

            okButton(dialog).fire();

            assertThat(model.errorLabel().getText())
                    .as("the dialog says what is wrong").isEqualTo(i18n.text("reminders.time.invalid"));
            assertThat(dialog.getResult()).as("and does not save").isNull();

            model.timeField().setText("09:15");
            okButton(dialog).fire();

            assertThat(model.errorLabel().getText()).isEmpty();
            assertThat(dialog.getResult())
                    .as("a usable hour saves the chosen days").isEmpty();
            return null;
        });
    }

    // ------------------------------------------------------------------

    private Dialog<List<LocalDate>> buildDialog(List<CardReminder> existing) {
        return new ReminderDialog(i18n, "Informe trimestral", TODAY, DUE, existing)
                .buildDialog(themeManager);
    }

    private void fire(Dialog<List<LocalDate>> dialog, String key) {
        Button button = (Button) findFirst(dialog.getDialogPane(),
                node -> node instanceof Button candidate && i18n.text(key).equals(candidate.getText()));
        assertThat(button).as("the %s button is in the dialog", key).isNotNull();
        button.fire();
    }

    private static Button okButton(Dialog<List<LocalDate>> dialog) {
        return (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
    }

    private List<ToggleButton> dayCells(Dialog<List<LocalDate>> dialog) {
        return cellsMatching(dialog, node -> true);
    }

    private static ToggleButton cell(Dialog<List<LocalDate>> dialog, LocalDate day) {
        List<ToggleButton> cells = cellsMatching(dialog,
                node -> day.equals(node.getUserData()));
        assertThat(cells).as("one cell for %s", day).hasSize(1);
        return cells.get(0);
    }

    private static List<ToggleButton> cellsMatching(Dialog<List<LocalDate>> dialog,
                                                    Predicate<Node> extra) {
        List<ToggleButton> cells = new ArrayList<>();
        collect(dialog.getDialogPane(), node -> node instanceof ToggleButton button
                && button.getStyleClass().contains("reminder-day")
                && extra.test(node), cells);
        return cells;
    }

    /** Dates of the cells that are offered (their disabled siblings are out of range). */
    private static List<LocalDate> datesOf(List<ToggleButton> cells, boolean offered) {
        List<LocalDate> days = new ArrayList<>();
        for (ToggleButton cell : cells) {
            if (cell.isDisabled() != offered) {
                days.add((LocalDate) cell.getUserData());
            }
        }
        days.sort(LocalDate::compareTo);
        return days;
    }

    private static List<LocalDate> selectedDates(List<ToggleButton> cells) {
        List<LocalDate> days = new ArrayList<>();
        for (ToggleButton cell : cells) {
            if (cell.isSelected()) {
                days.add((LocalDate) cell.getUserData());
            }
        }
        days.sort(LocalDate::compareTo);
        return days;
    }

    private static void collect(Node node, Predicate<Node> match, List<ToggleButton> found) {
        if (match.test(node)) {
            found.add((ToggleButton) node);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collect(child, match, found);
            }
        }
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
