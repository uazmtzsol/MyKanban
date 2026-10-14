package com.personalkanban.ui;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The calendar grid the user picks reminder days on (T23 · B10), tested without
 * JavaFX: whole weeks Monday-first, days outside the requested range offered but
 * not selectable, and a typed hour that is either usable or reported.
 */
class ReminderCalendarTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14); // Wednesday
    private static final LocalDate DUE = LocalDate.of(2026, 11, 6);    // Friday

    @Test
    void theGridIsWholeWeeksStartingOnMonday() {
        List<List<LocalDate>> weeks = ReminderCalendar.monthGrid(LocalDate.of(2026, 10, 1));

        assertThat(weeks).allSatisfy(week -> assertThat(week).hasSize(7));
        assertThat(weeks.get(0).get(0))
                .as("the first row starts on the Monday of the week of the 1st")
                .isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(weeks.get(0).get(0).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(weeks.get(0))
                .contains(LocalDate.of(2026, 10, 1));
        assertThat(weeks).anySatisfy(week -> assertThat(week).contains(LocalDate.of(2026, 10, 31)));
        // 2026-10-31 is a Saturday, so the last row completes with Sunday.
        assertThat(weeks.get(weeks.size() - 1)).contains(LocalDate.of(2026, 11, 1));
    }

    @Test
    void theGridCrossesTheYearBoundaryBothWays() {
        List<List<LocalDate>> january = ReminderCalendar.monthGrid(LocalDate.of(2026, 1, 15));
        assertThat(january.get(0).get(0)).isEqualTo(LocalDate.of(2025, 12, 29));
        assertThat(january.get(0)).contains(LocalDate.of(2026, 1, 1));

        List<List<LocalDate>> december = ReminderCalendar.monthGrid(LocalDate.of(2025, 12, 1));
        assertThat(december.get(december.size() - 1))
                .contains(LocalDate.of(2026, 1, 1));
        assertThat(december.stream().flatMap(List::stream).toList())
                .contains(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1));
    }

    @Test
    void aFebruaryOfALeapYearKeepsFiveRows() {
        List<List<LocalDate>> weeks = ReminderCalendar.monthGrid(LocalDate.of(2028, 2, 1));

        assertThat(weeks).hasSize(5);
        assertThat(weeks.stream().flatMap(List::stream).toList())
                .contains(LocalDate.of(2028, 2, 29));
    }

    @Test
    void firstOfMonthSnapsToTheFirstDay() {
        assertThat(ReminderCalendar.firstOfMonth(LocalDate.of(2026, 10, 26)))
                .isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void onlyDaysBetweenTodayAndTheDeadlineAreSelectable() {
        assertThat(ReminderCalendar.selectable(TODAY, TODAY, DUE)).isTrue();
        assertThat(ReminderCalendar.selectable(DUE, TODAY, DUE)).isTrue();
        assertThat(ReminderCalendar.selectable(TODAY.minusDays(1), TODAY, DUE)).isFalse();
        assertThat(ReminderCalendar.selectable(DUE.plusDays(1), TODAY, DUE)).isFalse();
        assertThat(ReminderCalendar.selectable(TODAY, TODAY, null))
                .as("without a deadline there is no reminder at all").isFalse();
    }

    @Test
    void anEmptyHourMeansAnAllDayReminder() {
        assertThat(ReminderCalendar.parseTime("").allDay()).isTrue();
        assertThat(ReminderCalendar.parseTime(null).allDay()).isTrue();
        assertThat(ReminderCalendar.parseTime("   ").valid()).isTrue();
    }

    @Test
    void aTypedHourIsReadAsWritten() {
        assertThat(ReminderCalendar.parseTime("09:00").time()).isEqualTo(LocalTime.of(9, 0));
        assertThat(ReminderCalendar.parseTime(" 18:30 ").time()).isEqualTo(LocalTime.of(18, 30));
        assertThat(ReminderCalendar.parseTime("7").time()).isEqualTo(LocalTime.of(7, 0));
        assertThat(ReminderCalendar.parseTime("7:5").time()).isEqualTo(LocalTime.of(7, 5));
        assertThat(ReminderCalendar.parseTime("23:59:59").time()).isEqualTo(LocalTime.of(23, 59, 59));
    }

    @Test
    void nonsenseInTheHourFieldIsReportedInsteadOfGuessed() {
        assertThat(ReminderCalendar.parseTime("24:00").valid()).isFalse();
        assertThat(ReminderCalendar.parseTime("12:60").valid()).isFalse();
        assertThat(ReminderCalendar.parseTime("mediodía").valid()).isFalse();
        assertThat(ReminderCalendar.parseTime("-1").valid()).isFalse();
    }

    @Test
    void hoursAreShownWithoutSeconds() {
        assertThat(ReminderCalendar.formatTime(LocalTime.of(9, 0))).isEqualTo("9:00");
        assertThat(ReminderCalendar.formatTime(LocalTime.of(18, 5))).isEqualTo("18:05");
        assertThat(ReminderCalendar.formatTime(null)).isEmpty();
    }
}
