package com.personalkanban.domain.board;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Domain tests for the per-card reminder moments (T23 · B10): the invariants
 * the calendar relies on — a reminder needs a deadline, never lands after it,
 * is unique, keeps its attended state, and travels through undo/redo.
 */
class ReminderDomainTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private static final LocalDate DUE = LocalDate.of(2026, 10, 23);

    private final Board board = new Board();

    /** A card with a deadline on {@link #DUE}, which reminders require. */
    private Card scheduledCard() {
        var columnId = board.addColumn("Todo", "", BoardColor.DEFAULT, WipLimit.unlimited()).id();
        CardAdded added = board.addCard(columnId, "Informe", "", BoardColor.DEFAULT);
        board.rescheduleCard(added.cardId(), DUE);
        return board.findCard(added.cardId()).orElseThrow();
    }

    @Test
    void momentsStayChronologicalWhateverOrderTheyArriveIn() {
        Card card = scheduledCard();
        board.addReminder(card.id(), TODAY.plusDays(4), LocalTime.of(9, 0));
        board.addReminder(card.id(), TODAY, null);
        board.addReminder(card.id(), TODAY.plusDays(2), LocalTime.of(18, 30));

        assertThat(card.reminders())
                .extracting(CardReminder::date)
                .containsExactly(TODAY, TODAY.plusDays(2), TODAY.plusDays(4));
        assertThat(card.reminders().get(0).time()).as("all-day reminder").isNull();
    }

    @Test
    void theSameMomentTwiceIsRejected() {
        Card card = scheduledCard();
        board.addReminder(card.id(), TODAY, LocalTime.of(9, 0));

        assertThatThrownBy(() -> board.addReminder(card.id(), TODAY, LocalTime.of(9, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
        assertThat(card.reminders()).hasSize(1);
    }

    @Test
    void theSameDayAtAnotherHourIsANewMoment() {
        Card card = scheduledCard();
        board.addReminder(card.id(), TODAY, LocalTime.of(9, 0));
        board.addReminder(card.id(), TODAY, LocalTime.of(20, 0));

        assertThat(card.reminders()).hasSize(2);
        assertThat(card.reminders()).extracting(CardReminder::time)
                .containsExactly(LocalTime.of(9, 0), LocalTime.of(20, 0));
    }

    @Test
    void aCardWithoutADeadlineCannotCarryReminders() {
        var columnId = board.addColumn("Todo", "", BoardColor.DEFAULT, WipLimit.unlimited()).id();
        CardAdded added = board.addCard(columnId, "Sin fecha", "", BoardColor.DEFAULT);

        assertThatThrownBy(() -> board.addReminder(added.cardId(), TODAY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("due date");
        assertThat(board.findCard(added.cardId()).orElseThrow().reminders()).isEmpty();
    }

    @Test
    void aMomentAfterTheDeadlineIsRejected() {
        Card card = scheduledCard();

        assertThatThrownBy(() -> board.addReminder(card.id(), DUE.plusDays(1), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("after the due date");
        // The deadline itself is still a valid day to be reminded on.
        board.addReminder(card.id(), DUE, null);
        assertThat(card.reminders()).hasSize(1);
    }

    @Test
    void attendingCountsPendingMomentsAndCanBeUndone() {
        Card card = scheduledCard();
        CardReminder morning = board.addReminder(card.id(), TODAY, LocalTime.of(9, 0));
        board.addReminder(card.id(), TODAY.plusDays(1), null);
        assertThat(card.pendingReminderCount()).isEqualTo(2);

        board.acknowledgeReminder(card.id(), morning.id(), true);
        assertThat(card.pendingReminderCount()).isEqualTo(1);
        assertThat(card.reminders().get(0).acknowledged()).isTrue();

        board.acknowledgeAllReminders(card.id());
        assertThat(card.pendingReminderCount()).isZero();
    }

    @Test
    void removingOneMomentLeavesTheOthers() {
        Card card = scheduledCard();
        CardReminder first = board.addReminder(card.id(), TODAY, null);
        CardReminder second = board.addReminder(card.id(), TODAY.plusDays(1), null);

        board.removeReminder(card.id(), first.id());

        assertThat(card.reminders()).extracting(CardReminder::id).containsExactly(second.id());
        assertThatThrownBy(() -> board.removeReminder(card.id(), first.id()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deletingTheCardTakesItsMomentsAway() {
        Card card = scheduledCard();
        board.addReminder(card.id(), TODAY, null);

        board.removeCard(card.id());

        assertThat(board.allCards()).noneMatch(candidate -> candidate.id().equals(card.id()));
    }

    /**
     * The calendar re-save path: replacing the schedule keeps the identity and
     * the attended state of the moments that were already there, so pressing
     * Save after opening the calendar never resurrects an answered reminder.
     */
    @Test
    void replacingTheScheduleKeepsIdentityAndAttendedState() {
        Card card = scheduledCard();
        CardReminder kept = board.addReminder(card.id(), TODAY, LocalTime.of(9, 0));
        board.acknowledgeReminder(card.id(), kept.id(), true);
        CardReminder replaced = CardReminder.of(TODAY.plusDays(1), LocalTime.of(9, 0));

        board.setReminders(card.id(), List.of(new CardReminder(kept.id(), kept.date(), kept.time(), false),
                replaced));

        assertThat(card.reminders()).hasSize(2);
        assertThat(card.reminders().get(0).id()).isEqualTo(kept.id());
        assertThat(card.reminders().get(0).acknowledged())
                .as("the aggregate owns the attended state, not the caller").isTrue();
    }

    @Test
    void mementoRoundTripKeepsMomentsAndTheirState() {
        Card card = scheduledCard();
        CardReminder timed = board.addReminder(card.id(), TODAY.plusDays(1), LocalTime.of(18, 30));
        board.addReminder(card.id(), TODAY.plusDays(2), null);
        board.acknowledgeReminder(card.id(), timed.id(), true);

        Board restored = BoardMemento.capture(board).toBoard();
        Card reloaded = restored.findCard(card.id()).orElseThrow();

        assertThat(reloaded.reminders()).hasSize(2);
        assertThat(reloaded.reminders().get(0).date()).isEqualTo(TODAY.plusDays(1));
        assertThat(reloaded.reminders().get(0).time()).isEqualTo(LocalTime.of(18, 30));
        assertThat(reloaded.reminders().get(0).acknowledged()).isTrue();
        assertThat(reloaded.reminders().get(1).time()).isNull();
    }

    /**
     * A deadline that moves earlier cannot leave reminders behind: they could
     * never be scheduled again, and the calendar only offers days up to the
     * deadline.
     */
    @Test
    void movingTheDeadlineEarlierDropsTheMomentsLeftBehind() {
        Card card = scheduledCard();
        board.addReminder(card.id(), TODAY.plusDays(2), null);
        board.addReminder(card.id(), TODAY.plusDays(8), null);

        board.rescheduleCard(card.id(), TODAY.plusDays(3));

        assertThat(card.reminders()).extracting(CardReminder::date)
                .containsExactly(TODAY.plusDays(2));
    }

    @Test
    void clearingTheDeadlineDropsEveryMoment() {
        Card card = scheduledCard();
        board.addReminder(card.id(), TODAY, null);

        board.rescheduleCard(card.id(), null);

        assertThat(card.reminders()).isEmpty();
    }
}
