package com.personalkanban.ui;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Month-grid maths of the reminder calendar (user request T23 · B10), pure and
 * JavaFX-free: the dialog only paints what this returns, so the grid, the
 * selectable range and the proposed hour can be tested without a UI.
 *
 * <p>The range the user asked for is <b>[today, due date]</b>: the calendar of a
 * card runs from the current day to its deadline, and days outside it are shown
 * (so the month keeps its shape) but not selectable.</p>
 */
final class ReminderCalendar {

    /** Hour the dialog proposes when the user asks for a timed reminder. */
    static final LocalTime DEFAULT_TIME = LocalTime.of(9, 0);

    private ReminderCalendar() {
    }

    /**
     * The weeks that cover {@code month}, Monday first, always seven days per
     * week: the first and last rows include the neighbouring days that complete
     * them (the dialog disables those).
     */
    static List<List<LocalDate>> monthGrid(LocalDate month) {
        Objects.requireNonNull(month, "month");
        LocalDate first = month.withDayOfMonth(1);
        LocalDate last = first.withDayOfMonth(first.lengthOfMonth());
        LocalDate start = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = last.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        long days = ChronoUnit.DAYS.between(start, end) + 1;
        List<List<LocalDate>> weeks = new ArrayList<>();
        LocalDate day = start;
        for (long done = 0; done < days; done += 7) {
            List<LocalDate> week = new ArrayList<>(7);
            for (int i = 0; i < 7; i++) {
                week.add(day);
                day = day.plusDays(1);
            }
            weeks.add(List.copyOf(week));
        }
        return List.copyOf(weeks);
    }

    /** The first day of the month that contains {@code day}. */
    static LocalDate firstOfMonth(LocalDate day) {
        return Objects.requireNonNull(day, "day").withDayOfMonth(1);
    }

    /**
     * Whether {@code day} may carry a reminder: never before {@code min}
     * (today) and never after {@code max} (the card's due date). With no due
     * date there is no reminder at all, so nothing is selectable.
     */
    static boolean selectable(LocalDate day, LocalDate min, LocalDate max) {
        Objects.requireNonNull(day, "day");
        if (max == null || min == null) {
            return false;
        }
        return !day.isBefore(min) && !day.isAfter(max);
    }

    /**
     * Outcome of reading the hour field: an all-day reminder (no hour), a
     * concrete hour, or text the dialog must reject instead of guessing.
     */
    record TimeInput(LocalTime time, boolean valid) {

        static TimeInput noHour() {
            return new TimeInput(null, true);
        }

        static TimeInput invalid() {
            return new TimeInput(null, false);
        }

        boolean allDay() {
            return valid && time == null;
        }
    }

    /**
     * Checks a typed hour ("09:00", "9:5", "9"): empty text means an all-day
     * reminder, anything unparsable or outside 00:00–23:59 is reported as
     * invalid so the dialog can tell the user instead of saving a guess.
     */
    static TimeInput parseTime(String text) {
        String trimmed = text == null ? "" : text.strip();
        if (trimmed.isEmpty()) {
            return TimeInput.noHour();
        }
        if (!trimmed.matches("\\d{1,2}(:\\d{1,2}){0,2}")) {
            return TimeInput.invalid();
        }
        String[] parts = trimmed.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        int second = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
        if (hour > 23 || minute > 59 || second > 59) {
            return TimeInput.invalid();
        }
        return new TimeInput(LocalTime.of(hour, minute, second), true);
    }

    /** Formats an hour for the dialog field: {@code 09:00}, or empty for all-day. */
    static String formatTime(LocalTime time) {
        if (time == null) {
            return "";
        }
        return time.getHour() + ":" + String.format("%02d", time.getMinute());
    }
}
