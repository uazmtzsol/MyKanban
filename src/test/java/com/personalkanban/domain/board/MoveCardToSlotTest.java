package com.personalkanban.domain.board;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MoveCardToSlotTest {

    private final Board board = new Board();

    private BoardColumn column(String title) {
        return board.addColumn(title, "", BoardColor.BLUE, WipLimit.unlimited());
    }

    @Test
    void crossColumnDropLandsInExactVisualSlot() {
        BoardColumn target = column("Target");
        board.addCard(target.id(), "a", "", BoardColor.BLUE);
        board.addCard(target.id(), "b", "", BoardColor.BLUE);

        BoardColumn source = column("Source");
        var moving = board.addCard(source.id(), "m", "", BoardColor.BLUE);

        // Drop on the slot right after "a" (slot 1): a, m, b.
        board.moveCardToSlot(moving.cardId(), target.id(), 1);

        assertThat(target.cards()).extracting(Card::title).containsExactly("a", "m", "b");
        assertThat(source.cardCount()).isZero();
    }

    @Test
    void sameColumnDropOntoOwnTrailingSlotIsANoOp() {
        BoardColumn col = column("C");
        board.addCard(col.id(), "a", "", BoardColor.BLUE);
        var b = board.addCard(col.id(), "b", "", BoardColor.BLUE);
        board.addCard(col.id(), "c", "", BoardColor.BLUE);

        // Pre-drop [a,b,c]; slot 2 sits between b and c. Dropping b there keeps
        // it exactly where it was.
        board.moveCardToSlot(b.cardId(), col.id(), 2);
        assertThat(col.cards()).extracting(Card::title).containsExactly("a", "b", "c");
    }

    @Test
    void sameColumnDropAfterCardXLandsRightAfterX() {
        BoardColumn col = column("C");
        var a = board.addCard(col.id(), "a", "", BoardColor.BLUE);
        board.addCard(col.id(), "b", "", BoardColor.BLUE);
        board.addCard(col.id(), "c", "", BoardColor.BLUE);

        // Pre-drop [a,b,c]; slot 2 sits between b and c. Dropping a there must
        // land it right after b — exactly the slot the user aimed at.
        board.moveCardToSlot(a.cardId(), col.id(), 2);
        assertThat(col.cards()).extracting(Card::title).containsExactly("b", "a", "c");
    }

    @Test
    void sameColumnDropBeforeItselfIsPositionZero() {
        BoardColumn col = column("C");
        var a = board.addCard(col.id(), "a", "", BoardColor.BLUE);
        board.addCard(col.id(), "b", "", BoardColor.BLUE);
        board.addCard(col.id(), "c", "", BoardColor.BLUE);

        // Slot 0 is before "a": moving a to its own slot 0 is a no-op order.
        board.moveCardToSlot(a.cardId(), col.id(), 0);
        assertThat(col.cards()).extracting(Card::title).containsExactly("a", "b", "c");
    }

    @Test
    void slotBeyondEndClampsToAppend() {
        BoardColumn target = column("Target");
        board.addCard(target.id(), "a", "", BoardColor.BLUE);

        BoardColumn source = column("Source");
        var moving = board.addCard(source.id(), "m", "", BoardColor.BLUE);

        board.moveCardToSlot(moving.cardId(), target.id(), 99);
        assertThat(target.cards()).extracting(Card::title).containsExactly("a", "m");
    }

    @Test
    void wipLimitStillEnforcedOnSlotDrops() {
        BoardColumn target = column("Target");
        target.limitTo(WipLimit.of(1));
        board.addCard(target.id(), "a", "", BoardColor.BLUE);

        BoardColumn source = column("Source");
        var moving = board.addCard(source.id(), "m", "", BoardColor.BLUE);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> board.moveCardToSlot(moving.cardId(), target.id(), 0))
                .isInstanceOf(com.personalkanban.domain.exception.WipLimitExceededException.class);
        assertThat(source.hasCard(moving.cardId())).isTrue();
    }
}
