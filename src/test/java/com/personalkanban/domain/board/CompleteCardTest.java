package com.personalkanban.domain.board;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T24 · B11: the card's "finished" state (completedAt). Covers the manual ✓,
 * the automatic finish/reopen when a card crosses the finalized column's
 * boundary, and the invariants: reordering never changes state, other columns
 * never touch it, and a card finished earlier by hand keeps its original day.
 */
class CompleteCardTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    private Board board;
    private BoardColumn pending;
    private BoardColumn done;

    @BeforeEach
    void setUp() {
        board = new Board();
        pending = board.addColumn("Pendiente", "", BoardColor.BLUE, WipLimit.unlimited());
        done = board.addColumn("Hecho", "", BoardColor.GREEN, WipLimit.unlimited());
        board.markColumnDone(done.id(), true);
    }

    private Card card(String title) {
        var added = board.addCard(pending.id(), title, "", BoardColor.BLUE);
        return board.findCard(added.cardId()).orElseThrow();
    }

    // ------------------------------------------------------------------
    // Manual toggle (the ✓ button)
    // ------------------------------------------------------------------

    @Test
    void aFreshCardIsNotDone() {
        Card card = card("una");
        assertThat(card.isDone()).isFalse();
        assertThat(card.completedAt()).isNull();
    }

    @Test
    void completingPutsTodayOnTheCard() {
        Card card = card("una");
        board.completeCard(card.id(), TODAY);
        assertThat(card.isDone()).isTrue();
        assertThat(card.completedAt()).isEqualTo(TODAY);
    }

    @Test
    void reopeningClearsTheDate() {
        Card card = card("una");
        board.completeCard(card.id(), TODAY);
        board.reopenCard(card.id());
        assertThat(card.isDone()).isFalse();
        assertThat(card.completedAt()).isNull();
    }

    @Test
    void completingIsIndependentOfTheColumn() {
        // The ✓ works anywhere: this card sits in a normal column.
        Card card = card("una");
        board.completeCard(card.id(), TODAY);
        assertThat(card.isDone()).isTrue();
        assertThat(pending.cards()).containsExactly(card);
    }

    // ------------------------------------------------------------------
    // Automatic: crossing the finalized column's boundary
    // ------------------------------------------------------------------

    @Test
    void droppingIntoTheFinalizedColumnFinishesTheCard() {
        Card card = card("una");
        board.moveCard(card.id(), done.id(), 0, TODAY);
        assertThat(card.isDone()).isTrue();
        assertThat(card.completedAt()).isEqualTo(TODAY);
    }

    @Test
    void takingItOutOfTheFinalizedColumnReopensIt() {
        Card card = card("una");
        board.moveCard(card.id(), done.id(), 0, TODAY);
        board.moveCard(card.id(), pending.id(), 0, TODAY);
        assertThat(card.isDone()).isFalse();
        assertThat(card.completedAt()).isNull();
    }

    @Test
    void reorderingInsideOneColumnNeverChangesTheState() {
        board.addCard(pending.id(), "a", "", BoardColor.BLUE);
        Card card = card("b");
        board.completeCard(card.id(), TODAY); // done by hand, still in "Pendiente"
        board.moveCard(card.id(), pending.id(), 0, TODAY);
        assertThat(card.isDone()).isTrue();
        assertThat(card.completedAt()).isEqualTo(TODAY);
    }

    @Test
    void movingBetweenTwoNormalColumnsNeverChangesTheState() {
        BoardColumn other = board.addColumn("Otra", "", BoardColor.RED, WipLimit.unlimited());
        Card card = card("una");
        board.completeCard(card.id(), TODAY);
        board.moveCard(card.id(), other.id(), 0, TODAY);
        assertThat(card.isDone()).isTrue();
        assertThat(card.completedAt()).isEqualTo(TODAY);
    }

    @Test
    void aCardFinishedEarlierByHandKeepsItsOriginalDay() {
        Card card = card("una");
        board.completeCard(card.id(), YESTERDAY);
        // Later it reaches the finalized column: the state was already right
        // and the original completion day is what the report must show.
        board.moveCard(card.id(), done.id(), 0, TODAY);
        assertThat(card.completedAt()).isEqualTo(YESTERDAY);
    }

    @Test
    void bulkMoveIntoTheFinalizedColumnFinishesEveryCard() {
        Card one = card("una");
        Card two = card("dos");
        board.moveCardsToColumn(List.of(one.id(), two.id()), done.id(), TODAY);
        assertThat(one.isDone()).isTrue();
        assertThat(two.isDone()).isTrue();
        assertThat(one.completedAt()).isEqualTo(TODAY);
    }

    @Test
    void bulkMoveOutOfTheFinalizedColumnReopensEveryCard() {
        Card one = card("una");
        Card two = card("dos");
        board.moveCardsToColumn(List.of(one.id(), two.id()), done.id(), TODAY);
        board.moveCardsToColumn(List.of(one.id(), two.id()), pending.id(), TODAY);
        assertThat(one.isDone()).isFalse();
        assertThat(two.isDone()).isFalse();
    }

    // ------------------------------------------------------------------
    // Snapshot round trip (undo/redo, persistence, sync)
    // ------------------------------------------------------------------

    @Test
    void snapshotKeepsTheCompletionDate() {
        Card card = card("una");
        board.completeCard(card.id(), TODAY);
        CardSnapshot snapshot = CardSnapshot.from(card);
        assertThat(snapshot.completedAt()).isEqualTo(TODAY);
        Card restored = snapshot.toCard(pending.id());
        assertThat(restored.isDone()).isTrue();
        assertThat(restored.completedAt()).isEqualTo(TODAY);
    }

    @Test
    void legacySnapshotShapeMeansPending() {
        // Pre-V12 snapshots carry no completion and must restore as pending.
        Card card = card("una");
        CardSnapshot legacy = new CardSnapshot(card.id(), card.title(), "", card.color(),
                null, List.of(), card.createdAt(), "", List.of(), null, List.of());
        assertThat(legacy.completedAt()).isNull();
        assertThat(legacy.toCard(pending.id()).isDone()).isFalse();
    }
}
