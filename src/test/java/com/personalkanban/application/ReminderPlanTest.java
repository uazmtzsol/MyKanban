package com.personalkanban.application;

import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.CardReminder;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The firing rules of the reminders (T23 · B10), with the clock always passed
 * in: what is due, what is not, and how the calendar rebuilds a schedule.
 */
class ReminderPlanTest {

    /** Friday 2026-10-16, mid-afternoon. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 15, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();

    private final Board board = new Board();
    private int cards;

    private Card card(String title, LocalDate dueDate) {
        var columnId = board.addColumn("Col " + cards, "", BoardColor.DEFAULT, WipLimit.unlimited()).id();
        cards++;
        var added = board.addCard(columnId, title, "", BoardColor.DEFAULT);
        board.rescheduleCard(added.cardId(), dueDate);
        return board.findCard(added.cardId()).orElseThrow();
    }

    @Test
    void anAllDayMomentIsDueFromTheVeryStartOfItsDay() {
        Card card = card("Sin hora", TODAY.plusDays(5));
        board.addReminder(card.id(), TODAY, null);

        assertThat(ReminderPlan.dueCount(List.of(card), LocalDateTime.of(TODAY, LocalTime.MIDNIGHT)))
                .as("it fires AT midnight of its day").isEqualTo(1);
        assertThat(ReminderPlan.dueCount(List.of(card),
                LocalDateTime.of(TODAY.minusDays(1), LocalTime.MAX)))
                .as("and not a moment before").isZero();
    }

    @Test
    void aTimedMomentWaitsForItsOwnHour() {
        Card card = card("Con hora", TODAY.plusDays(5));
        board.addReminder(card.id(), TODAY, LocalTime.of(18, 0));

        assertThat(ReminderPlan.dueNow(List.of(card), LocalDateTime.of(TODAY, LocalTime.of(17, 59))))
                .isEmpty();
        assertThat(ReminderPlan.dueNow(List.of(card), LocalDateTime.of(TODAY, LocalTime.of(18, 0))))
                .hasSize(1);
    }

    @Test
    void aMomentMissedWhileTheAppWasClosedIsStillDue() {
        Card card = card("Oportunidad perdida", TODAY.plusDays(5));
        board.addReminder(card.id(), TODAY.minusDays(3), LocalTime.of(9, 0));

        List<ReminderPlan.Due> due = ReminderPlan.dueNow(List.of(card), NOW);

        assertThat(due).hasSize(1);
        assertThat(due.get(0).cardTitle()).isEqualTo("Oportunidad perdida");
    }

    @Test
    void aFinishedCardNeverFiresItsReminders() {
        // T24 · B11: done is done — leftover moments would nag about
        // work already delivered.
        Card card = card("Entregada", TODAY.plusDays(5));
        board.addReminder(card.id(), TODAY.minusDays(1), null);
        assertThat(ReminderPlan.dueCount(List.of(card), NOW)).isEqualTo(1);

        board.completeCard(card.id(), TODAY);

        assertThat(ReminderPlan.dueNow(List.of(card), NOW)).isEmpty();
        // Reopening brings the still-pending moment back.
        board.reopenCard(card.id());
        assertThat(ReminderPlan.dueCount(List.of(card), NOW)).isEqualTo(1);
    }

    @Test
    void anAttendedMomentNeverComesBack() {
        Card card = card("Respondida", TODAY.plusDays(5));
        CardReminder reminder = board.addReminder(card.id(), TODAY.minusDays(1), null);
        assertThat(ReminderPlan.dueCount(List.of(card), NOW)).isEqualTo(1);

        board.acknowledgeReminder(card.id(), reminder.id(), true);

        assertThat(ReminderPlan.dueNow(List.of(card), NOW)).isEmpty();
        assertThat(card.pendingReminderCount()).isZero();
    }

    @Test
    void dueMomentsComeOutOldestFirstAcrossCards() {
        Card first = card("Primera", TODAY.plusDays(5));
        Card second = card("Segunda", TODAY.plusDays(5));
        board.addReminder(second.id(), TODAY.minusDays(1), null);
        board.addReminder(first.id(), TODAY.minusDays(3), LocalTime.of(8, 0));

        List<ReminderPlan.Due> due = ReminderPlan.dueNow(
                List.of(first, second), NOW);

        assertThat(due).extracting(ReminderPlan.Due::cardTitle)
                .containsExactly("Primera", "Segunda");
        assertThat(ReminderPlan.dueOf(first, NOW)).hasSize(1);
    }

    @Test
    void dailyAndWeeklyFillTheRangeTheUserAskedFor() {
        LocalDate from = LocalDate.of(2026, 10, 14); // Wednesday
        LocalDate to = LocalDate.of(2026, 10, 26);   // Monday, 12 days later

        assertThat(ReminderPlan.daily(from, to)).hasSize(13).startsWith(from).endsWith(to);
        assertThat(ReminderPlan.daily(to, from)).as("inverted range").isEmpty();

        assertThat(ReminderPlan.weekly(from, to, DayOfWeek.FRIDAY))
                .containsExactly(LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 23));
        assertThat(ReminderPlan.weekly(from, to, DayOfWeek.WEDNESDAY))
                .containsExactly(from, LocalDate.of(2026, 10, 21));
        assertThat(ReminderPlan.weekly(LocalDate.of(2026, 10, 17), to, DayOfWeek.FRIDAY))
                .as("the range starts on the first occurrence").containsExactly(LocalDate.of(2026, 10, 23));
    }

    /**
     * The calendar saves a whole schedule: a day that was already there keeps
     * its identity (and therefore its attended state), a new day is created.
     */
    @Test
    void theScheduleRebuildKeepsExistingMomentsAndAddsTheNewDays() {
        Card card = card("Reagenda", TODAY.plusDays(10));
        CardReminder kept = board.addReminder(card.id(), TODAY.plusDays(2), LocalTime.of(9, 0));
        board.acknowledgeReminder(card.id(), kept.id(), true);
        CardReminder other = board.addReminder(card.id(), TODAY.plusDays(3), null);

        List<CardReminder> rebuilt = ReminderPlan.schedule(card.reminders(),
                List.of(TODAY.plusDays(3), TODAY.plusDays(2), TODAY.plusDays(2)), LocalTime.of(9, 0));

        assertThat(rebuilt).hasSize(2);
        assertThat(rebuilt.get(0).id()).as("same day and hour → same moment").isEqualTo(kept.id());
        assertThat(rebuilt.get(0).acknowledged()).isTrue();
        assertThat(rebuilt.get(1).id())
                .as("the all-day moment is another moment, so it is built fresh")
                .isNotEqualTo(other.id());
        assertThat(rebuilt.get(1).time()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void anEmptyDaySelectionClearsTheSchedule() {
        Card card = card("Se queda sin recordatorios", TODAY.plusDays(4));
        board.addReminder(card.id(), TODAY.plusDays(1), null);

        assertThat(ReminderPlan.schedule(card.reminders(), List.of(), null)).isEmpty();
    }
}
