package com.personalkanban.application;

import com.personalkanban.domain.board.Card;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Due-date digest of a set of cards (session 15, T5 = N-6 + B3), pure and
 * testable: it only looks at {@link Card#dueDate()} and the day it is asked
 * about, so the same object answers both the filter and the startup notice.
 *
 * <p>Three non-overlapping ranges are reported:</p>
 * <ul>
 *   <li>{@link Scope#OVERDUE}: strictly before today,</li>
 *   <li>{@link Scope#WEEK}: today plus the next 6 days (the "this week" view
 *       includes today on purpose — what is due today is what the user has to
 *       do today),</li>
 *   <li>{@code dueToday} is the first of those 7 days, reported separately so
 *       the notice can say "hoy" instead of "esta semana".</li>
 * </ul>
 *
 * <p>Cards without a due date are never matched and never counted.</p>
 */
public final class DueDigest {

    /** Days covered by {@link Scope#WEEK}, today included. */
    public static final int WEEK_DAYS = 7;

    /** The due-date ranges a user can filter by. */
    public enum Scope { OVERDUE, TODAY, WEEK }

    /** How many cards fall in each range. */
    public record Counts(int overdue, int dueToday, int dueThisWeek) {

        /** Cards due before today or within the week; the ranges do not overlap. */
        public int total() {
            return overdue + dueThisWeek;
        }

        /** True when there is something worth telling the user about. */
        public boolean anything() {
            return overdue > 0 || dueThisWeek > 0;
        }
    }

    private final Counts counts;

    private DueDigest(Counts counts) {
        this.counts = counts;
    }

    /** Counts the cards of each range relative to {@code today}. */
    public static DueDigest of(List<Card> cards, LocalDate today) {
        Objects.requireNonNull(cards, "cards");
        Objects.requireNonNull(today, "today");

        int overdue = 0;
        int todayCount = 0;
        int week = 0;
        for (Card card : cards) {
            LocalDate due = card.dueDate();
            if (due == null) {
                continue;
            }
            if (due.isBefore(today)) {
                overdue++;
            } else if (due.equals(today)) {
                todayCount++;
                week++;
            } else if (!due.isAfter(lastDayOfWeek(today))) {
                week++;
            }
        }
        return new DueDigest(new Counts(overdue, todayCount, week));
    }

    public Counts counts() {
        return counts;
    }

    /** The last day the {@link Scope#WEEK} range covers. */
    public static LocalDate lastDayOfWeek(LocalDate today) {
        return today.plusDays(WEEK_DAYS - 1L);
    }

    /** True when the card falls inside the given due range. */
    public static boolean matches(Card card, Scope scope, LocalDate today) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(today, "today");

        LocalDate due = card.dueDate();
        if (due == null) {
            return false;
        }
        return switch (scope) {
            case OVERDUE -> due.isBefore(today);
            case TODAY -> due.equals(today);
            case WEEK -> !due.isBefore(today) && !due.isAfter(lastDayOfWeek(today));
        };
    }
}
