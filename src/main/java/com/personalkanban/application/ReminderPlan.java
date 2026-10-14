package com.personalkanban.application;

import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.CardReminder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Firing logic and helpers of the per-card reminders (user request T23 · B10),
 * pure and clock-free: the caller passes "now", so the whole behaviour is
 * unit-testable without a timer or a fake clock.
 *
 * <p>A reminder is <b>due</b> from its own moment on and stays due until it is
 * acknowledged; a reminder without a time is due from 00:00 of its date. An
 * app that was closed at the chosen moment therefore still tells the user about
 * it on the next launch, and an answered reminder never comes back.</p>
 */
public final class ReminderPlan {

    /** A reminder that is due right now, with the card it belongs to. */
    public record Due(CardId cardId, String cardTitle, CardReminder reminder) {
    }

    private ReminderPlan() {
    }

    /** Every pending reminder that has already come due, oldest first. */
    public static List<Due> dueNow(List<Card> cards, LocalDateTime now) {
        Objects.requireNonNull(cards, "cards");
        Objects.requireNonNull(now, "now");
        List<Due> due = new ArrayList<>();
        for (Card card : cards) {
            for (CardReminder reminder : card.reminders()) {
                if (!reminder.acknowledged() && !reminder.dueFrom().isAfter(now)) {
                    due.add(new Due(card.id(), card.title(), reminder));
                }
            }
        }
        due.sort(Comparator.comparing(Due::reminder, CardReminder.ORDER));
        return due;
    }

    /** How many reminders are due right now (the notice's number). */
    public static int dueCount(List<Card> cards, LocalDateTime now) {
        return dueNow(cards, now).size();
    }

    /** The due, still-pending reminders of one card, chronologically. */
    public static List<CardReminder> dueOf(Card card, LocalDateTime now) {
        return card.reminders().stream()
                .filter(reminder -> !reminder.acknowledged())
                .filter(reminder -> !reminder.dueFrom().isAfter(now))
                .toList();
    }

    /**
     * Daily pattern helper: every day from {@code from} to {@code to}
     * (both inclusive, empty when the range is inverted).
     */
    public static List<LocalDate> daily(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            days.add(day);
        }
        return days;
    }

    /**
     * Rebuilds a card's schedule from the days the calendar got, keeping the
     * identity of the moments that already existed: a reminder on a day that
     * was chosen again (same day and hour) keeps its id and its attended state,
     * so re-saving through the calendar never resurrects an answered reminder
     * nor loses the identity that undo and sync rely on.
     *
     * <p>Days are de-duplicated and sorted; {@code time == null} means an
     * all-day reminder (fires from the start of its day).</p>
     */
    public static List<CardReminder> schedule(List<CardReminder> existing, List<LocalDate> days,
                                             LocalTime time) {
        Objects.requireNonNull(existing, "existing");
        Objects.requireNonNull(days, "days");
        List<CardReminder> rebuilt = new ArrayList<>();
        for (LocalDate day : days.stream().distinct().sorted().toList()) {
            CardReminder previous = existing.stream()
                    .filter(reminder -> reminder.date().equals(day))
                    .filter(reminder -> Objects.equals(reminder.time(), time))
                    .findFirst()
                    .orElse(null);
            rebuilt.add(previous == null ? CardReminder.of(day, time) : previous);
        }
        return List.copyOf(rebuilt);
    }

    /**
     * Weekly pattern helper: the occurrences of {@code weekday} in
     * {@code [from, to]} — the natural reading of "once a week" for a deadline
     * several weeks away is "the same weekday as the deadline".
     */
    public static List<LocalDate> weekly(LocalDate from, LocalDate to, DayOfWeek weekday) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(weekday, "weekday");
        List<LocalDate> days = new ArrayList<>();
        LocalDate day = from;
        while (day.getDayOfWeek() != weekday) {
            day = day.plusDays(1);
        }
        for (; !day.isAfter(to); day = day.plusWeeks(1)) {
            days.add(day);
        }
        return days;
    }
}
