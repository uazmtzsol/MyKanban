package com.personalkanban.ui;

import com.personalkanban.application.ReminderPlan;
import com.personalkanban.domain.board.CardReminder;
import com.personalkanban.ui.theme.ThemeManager;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The reminder calendar the user asked for (T23 · B10): one month grid where
 * every day between today and the card's deadline can be toggled, plus the two
 * shortcuts of the request — <b>daily</b> and <b>once a week</b> — and an
 * optional hour shared by the chosen days. The result is a whole schedule, so
 * saving is a single undoable step.
 *
 * <p>Days that already carry a reminder start selected: pressing Save without
 * touching anything changes nothing, so opening the calendar never silently
 * drops a reminder.</p>
 */
final class ReminderDialog {

    /** What the dialog returns: the chosen days and the hour they share. */
    record Schedule(List<LocalDate> days, LocalTime time) {
    }

    private final I18n i18n;
    private final String cardTitle;
    private final LocalDate today;
    private final LocalDate dueDate;
    private final List<CardReminder> existing;

    private final GridPane grid = new GridPane();
    private final Label monthLabel = new Label();
    private final Label countLabel = new Label();
    private final Label errorLabel = new Label();
    private final TextField timeField = new TextField();
    private final Button previousMonth = new Button("\u2039");
    private final Button nextMonth = new Button("\u203A");
    private final Set<LocalDate> selected = new LinkedHashSet<>();

    private LocalDate shownMonth;

    private ReminderDialog(I18n i18n, String cardTitle, LocalDate today, LocalDate dueDate,
                           List<CardReminder> existing) {
        this.i18n = i18n;
        this.cardTitle = cardTitle;
        this.today = today;
        this.dueDate = dueDate;
        this.existing = existing;
        this.shownMonth = ReminderCalendar.firstOfMonth(today);
    }

    /**
     * Opens the day picker for one card. An empty result means the user
     * cancelled, so the card's schedule stays untouched.
     */
    static Optional<Schedule> choose(I18n i18n, ThemeManager themeManager, String cardTitle,
                                     LocalDate today, LocalDate dueDate,
                                     List<CardReminder> existing) {
        return new ReminderDialog(i18n, cardTitle, today, dueDate, existing).show(themeManager);
    }

    private Optional<Schedule> show(ThemeManager themeManager) {
        for (CardReminder reminder : existing) {
            selected.add(reminder.date());
        }
        LocalTime shared = sharedTime().orElse(null);
        timeField.setText(shared == null ? "" : ReminderCalendar.formatTime(shared));
        timeField.setPromptText(ReminderCalendar.formatTime(ReminderCalendar.DEFAULT_TIME));

        Dialog<List<LocalDate>> dialog = new Dialog<>();
        dialog.setTitle(i18n.text("reminders.title"));
        dialog.setHeaderText(i18n.text("reminders.header", cardTitle,
                today.toString(), dueDate.toString()));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(440);
        dialog.getDialogPane().getStylesheets().addAll(themeManager.stylesheets());

        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.setText(i18n.text("reminders.save"));
        ok.addEventFilter(ActionEvent.ACTION, event -> {
            if (!validateHour()) {
                event.consume();
            }
        });
        Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancel.setText(i18n.text("dialog.cancel"));

        Label hint = new Label(i18n.text("reminders.hint"));
        hint.getStyleClass().add("detail-caption");
        hint.setWrapText(true);

        previousMonth.getStyleClass().add("tool-button");
        nextMonth.getStyleClass().add("tool-button");
        previousMonth.setOnAction(e -> showMonth(shownMonth.minusMonths(1)));
        nextMonth.setOnAction(e -> showMonth(shownMonth.plusMonths(1)));
        monthLabel.getStyleClass().add("detail-caption");
        HBox monthRow = new HBox(8, previousMonth, monthLabel, nextMonth);
        monthRow.setAlignment(Pos.CENTER_LEFT);

        // The two shapes of the request: "diario" and "una vez a la semana
        // (el mismo día que la fecha límite)", over [today, due date].
        Button daily = generatorButton("reminders.auto.daily",
                () -> ReminderPlan.daily(today, dueDate));
        Button weekly = generatorButton("reminders.auto.weekly", () -> ReminderPlan.weekly(
                today, dueDate, dueDate.getDayOfWeek()));
        Button clear = generatorButton("reminders.clear", List::of);
        HBox generators = new HBox(8, daily, weekly, clear);
        generators.setAlignment(Pos.CENTER_LEFT);

        Label timeCaption = new Label(i18n.text("reminders.time"));
        timeField.setPrefColumnCount(5);
        Label timeHint = new Label(i18n.text("reminders.time.hint"));
        timeHint.getStyleClass().add("detail-caption");
        HBox timeRow = new HBox(8, timeCaption, timeField, timeHint);
        timeRow.setAlignment(Pos.CENTER_LEFT);

        countLabel.getStyleClass().add("detail-caption");
        errorLabel.getStyleClass().add("detail-caption");
        errorLabel.setWrapText(true);

        Region spacer = new Region();
        VBox content = new VBox(10, hint, monthRow, grid, generators, spacer, countLabel,
                timeRow, errorLabel);
        content.setPadding(new Insets(6));
        dialog.getDialogPane().setContent(content);

        showMonth(shownMonth);
        dialog.setResultConverter(button -> button == ButtonType.OK ? chosenDays() : null);

        Optional<List<LocalDate>> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return Optional.empty();
        }
        LocalTime time = ReminderCalendar.parseTime(timeField.getText()).time();
        return Optional.of(new Schedule(result.get(), time));
    }

    /** A generator button: it replaces the selection with the pattern's days. */
    private Button generatorButton(String key, Supplier<List<LocalDate>> pattern) {
        Button button = new Button(i18n.text(key));
        button.getStyleClass().add("tool-button");
        button.setOnAction(e -> {
            selected.clear();
            selected.addAll(pattern.get());
            refreshGrid();
        });
        return button;
    }

    /**
     * A day outside the month on screen is only painted for the shape of the
     * grid, never selectable, so the user cannot pick a day the range excludes.
     */
    private void showMonth(LocalDate month) {
        shownMonth = month;
        monthLabel.setText(capitalize(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy", i18n.locale()))));
        previousMonth.setDisable(!month.isAfter(ReminderCalendar.firstOfMonth(today)));
        nextMonth.setDisable(!month.isBefore(ReminderCalendar.firstOfMonth(dueDate)));
        refreshGrid();
    }

    private void refreshGrid() {
        grid.getChildren().clear();
        grid.setHgap(4);
        grid.setVgap(4);

        DayOfWeek[] order = DayOfWeek.values(); // MONDAY first, as the grid is built
        for (int column = 0; column < order.length; column++) {
            Label header = new Label(order[column].getDisplayName(TextStyle.SHORT, i18n.locale()));
            header.getStyleClass().add("reminder-weekday");
            header.setPrefWidth(38);
            header.setAlignment(Pos.CENTER);
            grid.add(header, column, 0);
        }

        List<List<LocalDate>> weeks = ReminderCalendar.monthGrid(shownMonth);
        for (int row = 0; row < weeks.size(); row++) {
            List<LocalDate> week = weeks.get(row);
            for (int column = 0; column < week.size(); column++) {
                LocalDate day = week.get(column);
                ToggleButton cell = new ToggleButton(String.valueOf(day.getDayOfMonth()));
                cell.getStyleClass().add("reminder-day");
                cell.setPrefWidth(38);
                boolean inShownMonth = day.getMonth() == shownMonth.getMonth()
                        && day.getYear() == shownMonth.getYear();
                cell.setDisable(!inShownMonth
                        || !ReminderCalendar.selectable(day, today, dueDate));
                cell.setSelected(selected.contains(day));
                cell.setOnAction(e -> {
                    if (cell.isSelected()) {
                        selected.add(day);
                    } else {
                        selected.remove(day);
                    }
                    updateCount();
                });
                grid.add(cell, column, row + 1);
            }
        }
        updateCount();
    }

    private void updateCount() {
        countLabel.setText(i18n.text("reminders.selected.count", selected.size()));
    }

    /** True when the hour field holds something usable; explains it when not. */
    private boolean validateHour() {
        ReminderCalendar.TimeInput input = ReminderCalendar.parseTime(timeField.getText());
        if (input.valid()) {
            errorLabel.setText("");
            return true;
        }
        errorLabel.setText(i18n.text("reminders.time.invalid"));
        return false;
    }

    /** The days the user toggled, chronologically. */
    private List<LocalDate> chosenDays() {
        return selected.stream().sorted().toList();
    }

    /**
     * The hour the card's reminders share, when they agree on one: the value the
     * dialog starts with, so re-saving an existing schedule keeps it.
     */
    private Optional<LocalTime> sharedTime() {
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        LocalTime time = existing.get(0).time();
        boolean shared = existing.stream().allMatch(reminder -> Objects.equals(reminder.time(), time));
        return shared ? Optional.ofNullable(time) : Optional.empty();
    }

    /** "octubre 2026" reads better as "Octubre 2026" at the top of the grid. */
    private String capitalize(String text) {
        if (text.isEmpty()) {
            return text;
        }
        return text.substring(0, 1).toUpperCase(i18n.locale()) + text.substring(1);
    }
}
