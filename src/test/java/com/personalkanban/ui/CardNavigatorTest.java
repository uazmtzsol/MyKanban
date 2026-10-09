package com.personalkanban.ui;

import com.personalkanban.domain.board.CardId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Session 10 keyboard navigation policy: which card each key must focus.
 * The rules come straight from the user's spec (down/up inside the column
 * with wrap, Alt+arrows across columns skipping empty ones, digit keys as
 * positional selectors).
 */
class CardNavigatorTest {

    private static CardId id(String value) {
        return new CardId(value);
    }

    /** Column A: a1 a2 a3 · Column B: (empty) · Column C: c1 c2. */
    private static final List<List<CardId>> BOARD = List.of(
            List.of(id("a1"), id("a2"), id("a3")),
            List.of(),
            List.of(id("c1"), id("c2")));

    // ------------------------------------------------------------------ down / up

    @Test
    void downMovesWithinTheColumnAndWrapsAtTheLastCard() {
        assertThat(CardNavigator.stepInColumn(BOARD, id("a1"), 1)).isEqualTo(id("a2"));
        assertThat(CardNavigator.stepInColumn(BOARD, id("a3"), 1)).isEqualTo(id("a1"));
    }

    @Test
    void upMovesBackwardsAndWrapsAtTheFirstCard() {
        assertThat(CardNavigator.stepInColumn(BOARD, id("a2"), -1)).isEqualTo(id("a1"));
        assertThat(CardNavigator.stepInColumn(BOARD, id("a1"), -1)).isEqualTo(id("a3"));
    }

    @Test
    void downWithoutFocusStartsAtTheBoardStartAndUpAtTheBoardEnd() {
        assertThat(CardNavigator.stepInColumn(BOARD, null, 1)).isEqualTo(id("a1"));
        assertThat(CardNavigator.stepInColumn(BOARD, null, -1)).isEqualTo(id("c2"));
    }

    @Test
    void aFocusedCardInvisibleUnderTheFiltersCountsAsNoFocus() {
        assertThat(CardNavigator.stepInColumn(BOARD, id("hidden"), 1)).isEqualTo(id("a1"));
    }

    // ------------------------------------------------------------- alt + left/right

    @Test
    void altRightJumpsToTheFirstCardOfTheNextColumn() {
        assertThat(CardNavigator.neighbouringColumnFirst(BOARD, id("a2"), 1)).isEqualTo(id("c1"));
    }

    @Test
    void altRightSkipsEmptyColumnsInsteadOfDoingNothing() {
        // From A the next column is the empty B: keep going to C.
        assertThat(CardNavigator.neighbouringColumnFirst(BOARD, id("a1"), 1)).isEqualTo(id("c1"));
    }

    @Test
    void altArrowsWrapAroundTheBoardEdges() {
        assertThat(CardNavigator.neighbouringColumnFirst(BOARD, id("c1"), 1)).isEqualTo(id("a1"));
        assertThat(CardNavigator.neighbouringColumnFirst(BOARD, id("a1"), -1)).isEqualTo(id("c1"));
    }

    @Test
    void altArrowWithoutFocusLandsOnTheFirstVisibleCard() {
        assertThat(CardNavigator.neighbouringColumnFirst(BOARD, null, 1)).isEqualTo(id("a1"));
        assertThat(CardNavigator.neighbouringColumnFirst(BOARD, null, -1)).isEqualTo(id("a1"));
    }

    // ------------------------------------------------------------------ digits

    @Test
    void digitSelectsTheNthCardOfTheCurrentColumn() {
        assertThat(CardNavigator.cardAtPosition(BOARD, id("a3"), 0)).isEqualTo(id("a1"));
        assertThat(CardNavigator.cardAtPosition(BOARD, id("a3"), 2)).isEqualTo(id("a3"));
    }

    @Test
    void digitBeyondTheColumnSizeSelectsNothing() {
        assertThat(CardNavigator.cardAtPosition(BOARD, id("c1"), 4)).isNull();
    }

    @Test
    void digitWithoutFocusUsesTheFirstNonEmptyColumn() {
        assertThat(CardNavigator.cardAtPosition(BOARD, null, 1)).isEqualTo(id("a2"));
    }

    // ------------------------------------------------------------------ empties

    @Test
    void emptyBoardYieldsNoTargetInsteadOfThrowing() {
        List<List<CardId>> empty = List.of();
        assertThat(CardNavigator.firstCard(empty)).isNull();
        assertThat(CardNavigator.lastCard(empty)).isNull();
        assertThat(CardNavigator.stepInColumn(empty, null, 1)).isNull();
        assertThat(CardNavigator.neighbouringColumnFirst(empty, null, 1)).isNull();
        assertThat(CardNavigator.cardAtPosition(empty, null, 0)).isNull();
        assertThat(CardNavigator.firstCard(List.of(List.of()))).isNull();
    }

    @Test
    void columnIndexOfReportsNoFocusForNullOrUnknownCards() {
        assertThat(CardNavigator.columnIndexOf(BOARD, null)).isEqualTo(-1);
        assertThat(CardNavigator.columnIndexOf(BOARD, id("nope"))).isEqualTo(-1);
        assertThat(CardNavigator.columnIndexOf(BOARD, id("c2"))).isEqualTo(2);
    }
}
