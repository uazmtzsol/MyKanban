package com.personalkanban.domain.board;

import com.personalkanban.domain.exception.WipLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardTest {

    private Board board;

    @BeforeEach
    void setUp() {
        board = new Board();
    }

    private BoardColumn column(String title) {
        return board.addColumn(title, "", BoardColor.BLUE, WipLimit.unlimited());
    }

    private BoardColumn limitedColumn(String title, int limit) {
        return board.addColumn(title, "", BoardColor.GREEN, WipLimit.of(limit));
    }

    // ------------------------------------------------------------------
    // Columns
    // ------------------------------------------------------------------

    @Test
    void addsAndListsColumnsInOrder() {
        BoardColumn first = column("To Do");
        BoardColumn second = column("Doing");
        assertThat(board.columns()).containsExactly(first, second);
    }

    @Test
    void rejectsBlankColumnTitle() {
        assertThatThrownBy(() -> board.addColumn("   ", "", BoardColor.BLUE, WipLimit.unlimited()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void movesColumnWithinBoard() {
        BoardColumn a = column("A");
        BoardColumn b = column("B");
        BoardColumn c = column("C");

        board.moveColumn(a.id(), 2); // to the end

        assertThat(board.columns()).containsExactly(b, c, a);
    }

    @Test
    void removeColumnDropsItAndItsCardsAreGone() {
        BoardColumn col = column("A");
        board.addCard(col.id(), "t1", "", BoardColor.RED);
        board.removeColumn(col.id());
        assertThat(board.columns()).isEmpty();
        assertThat(board.cardCount()).isZero();
    }

    // ------------------------------------------------------------------
    // Cards
    // ------------------------------------------------------------------

    @Test
    void addsCardsToTheEndOfTheirColumn() {
        BoardColumn col = column("A");
        board.addCard(col.id(), "one", "", BoardColor.BLUE);
        board.addCard(col.id(), "two", "", BoardColor.BLUE);
        assertThat(col.cards()).extracting(Card::title).containsExactly("one", "two");
    }

    @Test
    void cardTitleIsRequired() {
        BoardColumn col = column("A");
        assertThatThrownBy(() -> board.addCard(col.id(), "  ", "", BoardColor.BLUE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wipLimitBlocksThirdCard() {
        BoardColumn col = limitedColumn("Limited", 2);
        board.addCard(col.id(), "one", "", BoardColor.BLUE);
        board.addCard(col.id(), "two", "", BoardColor.BLUE);

        assertThatThrownBy(() -> board.addCard(col.id(), "three", "", BoardColor.BLUE))
                .isInstanceOf(WipLimitExceededException.class);
        assertThat(col.cardCount()).isEqualTo(2);
    }

    @Test
    void loweringWipBelowCurrentCountIsRejected() {
        BoardColumn col = column("A");
        board.addCard(col.id(), "one", "", BoardColor.BLUE);
        board.addCard(col.id(), "two", "", BoardColor.BLUE);
        assertThatThrownBy(() -> board.limitColumn(col.id(), WipLimit.of(1)))
                .isInstanceOf(IllegalArgumentException.class);
        board.limitColumn(col.id(), WipLimit.of(2)); // exactly full is allowed
        assertThat(board.columnById(col.id()).orElseThrow().wipLimit()).isEqualTo(WipLimit.of(2));
    }

    @Test
    void moveWithinColumnReordersBeforeGivenIndex() {
        BoardColumn col = column("A");
        var one = board.addCard(col.id(), "one", "", BoardColor.BLUE);
        board.addCard(col.id(), "two", "", BoardColor.BLUE);
        board.addCard(col.id(), "three", "", BoardColor.BLUE);

        board.moveCard(one.cardId(), col.id(), 2);

        assertThat(col.cards()).extracting(Card::title).containsExactly("two", "one", "three");
    }

    @Test
    void moveAcrossColumnsRespectsWipOfTarget() {
        BoardColumn source = column("Source");
        BoardColumn target = limitedColumn("Target", 1);
        var card = board.addCard(source.id(), "moving", "", BoardColor.BLUE);
        board.addCard(target.id(), "occupied", "", BoardColor.BLUE);

        assertThatThrownBy(() -> board.moveCard(card.cardId(), target.id(), 0))
                .isInstanceOf(WipLimitExceededException.class);

        // Atomic: the card must still be on the source column.
        assertThat(source.hasCard(card.cardId())).isTrue();
        assertThat(target.cardCount()).isEqualTo(1);
    }

    @Test
    void moveAcrossColumnsTransfersTheCard() {
        BoardColumn source = column("Source");
        BoardColumn target = column("Target");
        var card = board.addCard(source.id(), "moving", "", BoardColor.BLUE);

        board.moveCard(card.cardId(), target.id(), 0);

        assertThat(source.cardCount()).isZero();
        assertThat(target.cards()).extracting(Card::title).containsExactly("moving");
    }

    @Test
    void editingCardUpdatesAllFields() {
        BoardColumn col = column("A");
        var event = board.addCard(col.id(), "old", "desc", BoardColor.BLUE);

        board.editCard(event.cardId(), "new", "new desc", BoardColor.PINK);

        Card card = board.findCard(event.cardId()).orElseThrow();
        assertThat(card.title()).isEqualTo("new");
        assertThat(card.description()).isEqualTo("new desc");
        assertThat(card.color()).isEqualTo(BoardColor.PINK);
    }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    @Test
    void drainsEventsOnce() {
        BoardColumn col = column("A");
        var added = board.addCard(col.id(), "one", "", BoardColor.BLUE);
        board.moveCard(added.cardId(), col.id(), 0);
        board.removeCard(added.cardId());

        assertThat(board.drainEvents()).hasSize(3);
        assertThat(board.drainEvents()).isEmpty();
    }

    // ------------------------------------------------------------------
    // Memento round-trip
    // ------------------------------------------------------------------

    @Test
    void snapshotRestoresFullState() {
        BoardColumn col = column("A");
        var added = board.addCard(col.id(), "one", "desc", BoardColor.PINK);
        board.limitColumn(col.id(), WipLimit.of(5));

        BoardMemento snapshot = BoardMemento.capture(board);
        Board restored = snapshot.toBoard();

        assertThat(restored.columns()).hasSize(1);
        BoardColumn restoredColumn = restored.columns().get(0);
        assertThat(restoredColumn.title()).isEqualTo("A");
        assertThat(restoredColumn.wipLimit()).isEqualTo(WipLimit.of(5));
        assertThat(restoredColumn.cards()).singleElement().satisfies(card -> {
            assertThat(card.id()).isEqualTo(added.cardId());
            assertThat(card.title()).isEqualTo("one");
            assertThat(card.color()).isEqualTo(BoardColor.PINK);
        });
    }
}
