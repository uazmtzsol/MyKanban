package com.personalkanban.application;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.TimelineEntry;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Read-only time report of one board (session 14, user request B1): how much
 * tracked time went into each week.
 *
 * <p>It only reads what the time tracking already stores per card
 * ({@code Timeline}/{@code TimelineEntry}), so no data has to be added to
 * produce it. Two deliberate rules keep the numbers honest:</p>
 *
 * <ul>
 *   <li>An entry belongs to the range when it <em>started</em> inside it; an
 *       entry started before the range is not counted, however long it ran.</li>
 *   <li>A still-running entry counts up to {@code now} — the same criterion the
 *       card's time tab uses — so the report never under-reports today.</li>
 * </ul>
 *
 * <p>Weeks are ISO weeks (Monday to Sunday, in the reporting time zone) and
 * weeks with no tracked time are kept at zero, because a gap is information
 * too.</p>
 */
public final class TimeReport {

    /** Range for "everything ever tracked" (no lower bound). */
    public static final int ALL = 0;

    /** One row of the per-week table; {@code weekStart} is always a Monday. */
    public record WeekRow(LocalDate weekStart, long millis) {}

    private final List<WeekRow> byWeek;
    private final long total;
    private final int entryCount;

    private TimeReport(List<WeekRow> byWeek, long total, int entryCount) {
        this.byWeek = List.copyOf(byWeek);
        this.total = total;
        this.entryCount = entryCount;
    }

    /**
     * Builds the report of {@code board}.
     *
     * @param now   the instant a running entry is counted up to
     * @param zone  the zone that decides which day/week an entry belongs to
     * @param weeks how many weeks back to cover, including the current one;
     *              {@link #ALL} or any negative value means "everything"
     */
    public static TimeReport of(Board board, Instant now, ZoneId zone, int weeks) {
        Objects.requireNonNull(board, "board");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(zone, "zone");

        LocalDate currentWeek = weekStartOf(now.atZone(zone).toLocalDate());
        LocalDate firstWeek = weeks > 0 ? currentWeek.minusWeeks(weeks - 1L) : null;
        Instant lowerBound = firstWeek == null ? null : firstWeek.atStartOfDay(zone).toInstant();

        Map<LocalDate, Long> millisByWeek = new TreeMap<>();

        long total = 0;
        int entries = 0;
        LocalDate earliestWeek = null;

        for (Card card : board.allCards()) {
            for (TimelineEntry entry : card.timeline().entries()) {
                if (entry.start() == null) {
                    continue; // never started: nothing to report
                }
                if (lowerBound != null && entry.start().isBefore(lowerBound)) {
                    continue; // started before the range
                }
                long millis = entry.durationMillis(now);
                if (millis <= 0) {
                    continue; // zero-length entry: adds nothing
                }
                LocalDate week = weekStartOf(entry.start().atZone(zone).toLocalDate());
                millisByWeek.merge(week, millis, Long::sum);
                if (earliestWeek == null || week.isBefore(earliestWeek)) {
                    earliestWeek = week;
                }
                total += millis;
                entries++;
            }
        }

        return new TimeReport(
                weekRows(firstWeek, earliestWeek, currentWeek, millisByWeek),
                total,
                entries);
    }

    /** One row per week in the range, oldest first; empty weeks stay at zero. */
    private static List<WeekRow> weekRows(LocalDate firstWeek, LocalDate earliestWeek,
                                          LocalDate currentWeek, Map<LocalDate, Long> millisByWeek) {
        LocalDate from = firstWeek != null ? firstWeek
                : (earliestWeek != null ? earliestWeek : currentWeek);
        List<WeekRow> rows = new ArrayList<>();
        for (LocalDate week = from; !week.isAfter(currentWeek); week = week.plusWeeks(1)) {
            rows.add(new WeekRow(week, millisByWeek.getOrDefault(week, 0L)));
        }
        return rows;
    }

    /** Monday of the ISO week that contains {@code date}. */
    static LocalDate weekStartOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Total tracked time inside the range, in milliseconds. */
    public long total() {
        return total;
    }

    /** How many time entries contributed to the report. */
    public int entryCount() {
        return entryCount;
    }

    /** Per-week rows, oldest first, no gaps. */
    public List<WeekRow> byWeek() {
        return byWeek;
    }

    /** First week covered (the oldest row). */
    public LocalDate firstWeek() {
        return byWeek.get(0).weekStart();
    }

    /** Last week covered (the one containing {@code now}). */
    public LocalDate lastWeek() {
        return byWeek.get(byWeek.size() - 1).weekStart();
    }

    /** True when nothing was tracked inside the range. */
    public boolean isEmpty() {
        return entryCount == 0;
    }
}
