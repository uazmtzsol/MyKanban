package com.personalkanban.domain.board;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Domain tests for the card timeline (time tracking), including the
 * start/stop workflow and comment rules.
 */
class TimelineTest {

    private static final CardId CARD = new CardId("card-1");
    private static final Instant T0 = Instant.parse("2027-01-01T13:00:00Z");

    @Test
    void startTrackingCreatesARunningEntry() {
        Timeline timeline = new Timeline(CARD);
        TimelineEntry entry = timeline.startTracking(T0);

        assertThat(timeline.isTracking()).isTrue();
        assertThat(timeline.runningEntry()).isSameAs(entry);
        assertThat(entry.start()).isEqualTo(T0);
        assertThat(entry.end()).isNull();
        assertThat(entry.isRunning()).isTrue();
        assertThat(entry.isClosed()).isFalse();
        assertThat(timeline.entries()).containsExactly(entry);
    }

    @Test
    void stopTrackingClosesTheRunningEntry() {
        Timeline timeline = new Timeline(CARD);
        TimelineEntry entry = timeline.startTracking(T0);
        Instant end = T0.plus(Duration.ofMinutes(45));

        TimelineEntry closed = timeline.stopTracking(end);

        assertThat(closed).isSameAs(entry);
        assertThat(entry.end()).isEqualTo(end);
        assertThat(entry.isClosed()).isTrue();
        assertThat(timeline.isTracking()).isFalse();
        assertThat(entry.duration(T0.plusSeconds(9999))).isEqualTo(Duration.ofMinutes(45));
    }

    @Test
    void multipleEntriesKeepHistory() {
        Timeline timeline = new Timeline(CARD);
        timeline.startTracking(T0);
        timeline.stopTracking(T0.plusSeconds(60));
        timeline.startTracking(T0.plusSeconds(120));
        timeline.stopTracking(T0.plusSeconds(300));

        assertThat(timeline.entries()).hasSize(2);
        assertThat(timeline.entries()).extracting(TimelineEntry::start)
                .containsExactly(T0, T0.plusSeconds(120));
    }

    @Test
    void runningCommentIsStoredAndSurvivesClose() {
        Timeline timeline = new Timeline(CARD);
        timeline.startTracking(T0);
        timeline.setRunningComment("  Iniciando el registro  ");

        TimelineEntry entry = timeline.runningEntry();
        assertThat(entry.comment()).isEqualTo("Iniciando el registro");
        assertThat(entry.hasComment()).isTrue();

        timeline.stopTracking(T0.plusSeconds(30));
        assertThat(entry.comment()).isEqualTo("Iniciando el registro");
    }

    @Test
    void closedEntryRejectsCommentEdits() {
        TimelineEntry entry = TimelineEntry.restore(CARD, EntryId.newId(), T0, T0.plusSeconds(30), "x");
        assertThatThrownBy(() -> entry.setComment("y"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void removeEntryDropsItFromTheTimeline() {
        Timeline timeline = new Timeline(CARD);
        TimelineEntry entry = timeline.startTracking(T0);
        assertThat(timeline.removeEntry(entry.id())).isTrue();
        assertThat(timeline.entries()).isEmpty();
        assertThat(timeline.removeEntry(entry.id())).isFalse();
    }

    @Test
    void restoreReplacesContentsInOrder() {
        Timeline timeline = new Timeline(CARD);
        TimelineEntry later = TimelineEntry.restore(CARD, EntryId.newId(), T0.plusSeconds(100), T0.plusSeconds(200), "");
        TimelineEntry earlier = TimelineEntry.restore(CARD, EntryId.newId(), T0, T0.plusSeconds(50), "");
        timeline.restore(java.util.List.of(later, earlier));

        assertThat(timeline.entries()).containsExactly(earlier, later);
    }

    @Test
    void emptyEntryIsNeverPersisted() {
        TimelineEntry blank = TimelineEntry.open(CARD, EntryId.newId());
        assertThat(blank.isEmpty()).isTrue();
        blank.start(T0);
        assertThat(blank.isEmpty()).isFalse();
    }
}
