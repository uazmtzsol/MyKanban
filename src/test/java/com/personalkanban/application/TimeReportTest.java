package com.personalkanban.application;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.Process;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests of the time report (session 14, B1). The fixture is built by hand so
 * every number is checkable on paper: four weeks, work in two processes, a
 * card without any process, a still-running entry, a zero-length entry and one
 * entry that started before the range.
 *
 * <p>2026-10-12 is a Monday, so the ISO weeks of the fixture are 09-14, 09-21,
 * 09-28, 10-05 and 10-12 — a fixed calendar that never depends on the machine
 * that runs the suite.</p>
 */
class TimeReportTest {

    private static final ZoneId ZONE = ZoneOffset.UTC;
    /** Monday 10:00 — "now" for every test. */
    private static final Instant NOW = at("2026-10-12", "10:00");

    private static Instant at(String date, String time) {
        return LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))
                .toInstant(ZoneOffset.UTC);
    }

    private static long minutes(long minutes) {
        return Duration.ofMinutes(minutes).toMillis();
    }

    /**
     * A board with fixed, hand-computed time entries:
     * <ul>
     *   <li><b>Dev</b> — A 60 min (week 09-21), B 30 min (week 10-05),
     *       G 2×5 min (week 10-12) → 100 min on 3 cards.</li>
     *   <li><b>Ops</b> — C 90 min (week 10-12) → 90 min on 1 card.</li>
     *   <li><b>No process</b> — D 240 min (week 09-28) + E running 20 min
     *       (week 10-12) → 260 min on 2 cards.</li>
     *   <li><b>Out of range</b> — F 60 min started on 09-15 (week 09-14).</li>
     *   <li><b>Zero length</b> — H 0 min on 10-12.</li>
     * </ul>
     */
    private static final class Fixture {

        private final Board board = new Board();
        private final CardId devA = card("A");
        private final CardId devB = card("B");
        private final CardId devG = card("G");
        private final CardId opsC = card("C");
        private final CardId looseD = card("D");
        private final CardId runningE = card("E");
        private final CardId oldF = card("F");
        private final CardId zeroH = card("H");

        private Fixture() {
            Process dev = board.addProcess("Dev");
            Process ops = board.addProcess("Ops");
            board.assignCardToProcess(devA, dev.id());
            board.assignCardToProcess(devB, dev.id());
            board.assignCardToProcess(devG, dev.id());
            board.assignCardToProcess(opsC, ops.id());

            logged(devA, "2026-09-22", "09:00", "10:00");
            logged(devB, "2026-10-06", "08:00", "08:30");
            logged(devG, "2026-10-12", "06:00", "06:05");
            logged(devG, "2026-10-12", "06:10", "06:15");
            logged(opsC, "2026-10-12", "07:00", "08:30");
            logged(looseD, "2026-09-29", "09:00", "13:00");
            board.startTimeTracking(runningE, at("2026-10-12", "09:40"));
            logged(oldF, "2026-09-15", "12:00", "13:00");
            logged(zeroH, "2026-10-12", "05:00", "05:00");
        }

        private CardId card(String title) {
            if (board.columns().isEmpty()) {
                board.addColumn("Todo", "", BoardColor.DEFAULT, WipLimit.unlimited());
            }
            var columnId = board.columns().get(0).id();
            return board.addCard(columnId, title, "", BoardColor.DEFAULT).cardId();
        }

        private void logged(CardId cardId, String date, String from, String to) {
            board.startTimeTracking(cardId, at(date, from));
            board.stopTimeTracking(cardId, at(date, to));
        }

        private TimeReport report(int weeks) {
            return TimeReport.of(board, NOW, ZONE, weeks);
        }
    }

    @Test
    void groupsTimeByProcessWithTheLargestFirstAndNoProcessLast() {
        TimeReport report = new Fixture().report(4);

        // The no-process row has MORE time than either process and still goes
        // last: it is a remainder, not a process to rank.
        assertThat(report.byProcess()).extracting(TimeReport.ProcessRow::name)
                .containsExactly("Dev", "Ops", null);
        assertThat(report.byProcess()).extracting(TimeReport.ProcessRow::millis)
                .containsExactly(minutes(100), minutes(90), minutes(260));
        assertThat(report.byProcess()).extracting(TimeReport.ProcessRow::cardCount)
                .containsExactly(3, 1, 2);
    }

    @Test
    void weeksAreIsoWeeksFilledWithoutGaps() {
        TimeReport report = new Fixture().report(4);

        assertThat(report.byWeek()).extracting(TimeReport.WeekRow::weekStart).containsExactly(
                LocalDate.parse("2026-09-21"),
                LocalDate.parse("2026-09-28"),
                LocalDate.parse("2026-10-05"),
                LocalDate.parse("2026-10-12"));
        assertThat(report.byWeek()).extracting(TimeReport.WeekRow::millis).containsExactly(
                minutes(60), minutes(240), minutes(30), minutes(120));
        assertThat(report.firstWeek().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(report.lastWeek()).isEqualTo(LocalDate.parse("2026-10-12"));
    }

    @Test
    void totalsMatchBothTables() {
        TimeReport report = new Fixture().report(4);

        long byProcess = report.byProcess().stream()
                .mapToLong(TimeReport.ProcessRow::millis).sum();
        long byWeek = report.byWeek().stream()
                .mapToLong(TimeReport.WeekRow::millis).sum();

        assertThat(report.total()).isEqualTo(minutes(450));
        assertThat(byProcess).isEqualTo(report.total());
        assertThat(byWeek).isEqualTo(report.total());
        assertThat(report.entryCount()).isEqualTo(7);
    }

    @Test
    void aRunningEntryCountsUpToNow() {
        TimeReport report = new Fixture().report(1);

        // Current week only: C 90 + E 20 (still running) + G 10.
        assertThat(report.total()).isEqualTo(minutes(120));
        assertThat(report.byWeek()).extracting(TimeReport.WeekRow::millis)
                .containsExactly(minutes(120));
        assertThat(report.byProcess()).extracting(TimeReport.ProcessRow::name)
                .containsExactly("Ops", "Dev", null);
        assertThat(report.byProcess().get(2).millis()).isEqualTo(minutes(20)); // E, running
    }

    @Test
    void entriesStartedBeforeTheRangeAreIgnored() {
        TimeReport four = new Fixture().report(4);
        TimeReport all = new Fixture().report(TimeReport.ALL);

        assertThat(four.total()).isEqualTo(minutes(450)); // F is out of range
        assertThat(all.total()).isEqualTo(minutes(510));  // F adds its 60 min
        assertThat(all.firstWeek()).isEqualTo(LocalDate.parse("2026-09-14"));
        assertThat(all.byWeek()).hasSize(5);
    }

    @Test
    void aNegativeRangeMeansEverythingToo() {
        assertThat(new Fixture().report(-1).total())
                .isEqualTo(new Fixture().report(TimeReport.ALL).total());
    }

    @Test
    void zeroLengthEntriesAreIgnored() {
        TimeReport report = new Fixture().report(4);

        // H ran 05:00→05:00: it is not an entry (7 countable) and not a card.
        assertThat(report.entryCount()).isEqualTo(7);
        assertThat(report.byProcess()).extracting(TimeReport.ProcessRow::cardCount)
                .containsExactly(3, 1, 2);
    }

    @Test
    void removingAProcessSendsItsCardsToTheNoProcessRow() {
        Fixture fixture = new Fixture();
        Process dev = fixture.board.processList().get(0);

        fixture.board.removeProcess(dev.id());
        TimeReport report = fixture.report(4);

        assertThat(report.byProcess()).extracting(TimeReport.ProcessRow::name)
                .containsExactly("Ops", null);
        assertThat(report.byProcess().get(1).millis()).isEqualTo(minutes(360)); // 260 + 100
        assertThat(report.total()).isEqualTo(minutes(450)); // nothing lost
    }

    @Test
    void anEmptyBoardStillReportsTheCurrentWeek() {
        TimeReport report = TimeReport.of(new Board(), NOW, ZONE, 4);

        assertThat(report.isEmpty()).isTrue();
        assertThat(report.total()).isZero();
        assertThat(report.byProcess()).isEmpty();
        assertThat(report.byWeek()).extracting(TimeReport.WeekRow::weekStart)
                .containsExactly(LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-28"),
                        LocalDate.parse("2026-10-05"), LocalDate.parse("2026-10-12"));
        assertThat(report.lastWeek()).isEqualTo(LocalDate.parse("2026-10-12"));
    }
}
