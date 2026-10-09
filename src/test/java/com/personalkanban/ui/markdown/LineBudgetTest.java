package com.personalkanban.ui.markdown;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Session 10 (F2): the preview budget must count LINES, not AST blocks —
 * that mismatch is why the 3-line preview used to look identical to the
 * full mode.
 */
class LineBudgetTest {

    @Test
    void keepsContentUntilTheLineBudgetIsSpent() {
        LineBudget budget = new LineBudget(3, Integer.MAX_VALUE);
        assertThat(budget.accept("one")).isEqualTo("one");
        assertThat(budget.accept("\n")).isEqualTo("\n");
        assertThat(budget.accept("two")).isEqualTo("two");
        assertThat(budget.accept("\n")).isEqualTo("\n");
        assertThat(budget.accept("three")).isEqualTo("three");
        assertThat(budget.exhausted()).isFalse();
    }

    @Test
    void cutsOffTheFourthLineInsideOnePiece() {
        LineBudget budget = new LineBudget(3, Integer.MAX_VALUE);
        // The kept prefix carries the line break of the last visible row;
        // MarkdownSummary turns it into the ellipsis via markCut().
        assertThat(budget.accept("a\nb\nc\nd\ne")).isEqualTo("a\nb\nc\n");
        assertThat(budget.exhausted()).isTrue();
        assertThat(budget.accept("f")).isEmpty();
    }

    @Test
    void aSingleUnbrokenLineIsStillCappedByCharacters() {
        LineBudget budget = new LineBudget(3, 20);
        String kept = budget.accept("0123456789012345678901234567890123456789");
        assertThat(kept.length()).isLessThanOrEqualTo(20);
        assertThat(budget.exhausted()).isTrue();
    }

    @Test
    void characterCutPrefersAWordBoundary() {
        LineBudget budget = new LineBudget(3, 10);
        assertThat(budget.accept("hello world again")).isEqualTo("hello");
        assertThat(budget.exhausted()).isTrue();
    }

    @Test
    void zeroBudgetRejectsEverything() {
        LineBudget noLines = new LineBudget(0, 100);
        assertThat(noLines.accept("x")).isEmpty();
        assertThat(noLines.exhausted()).isTrue();

        LineBudget noChars = new LineBudget(3, 0);
        assertThat(noChars.accept("x")).isEmpty();
        assertThat(noChars.exhausted()).isTrue();
    }

    @Test
    void marksTheCutAfterALastLineBreakOnTheSameRow() {
        LineBudget budget = new LineBudget(1, Integer.MAX_VALUE);
        budget.accept("row\nrow2");
        assertThat(budget.exhausted()).isTrue();
        assertThat(budget.markCut("row\n")).isEqualTo("row\u2026");
    }

    @Test
    void markDoesNothingWhileTheBudgetLasts() {
        LineBudget budget = new LineBudget(5, 100);
        budget.accept("plain");
        assertThat(budget.exhausted()).isFalse();
        assertThat(budget.markCut("plain")).isEqualTo("plain");
    }

    @Test
    void markNeverDoublesTheEllipsis() {
        LineBudget budget = new LineBudget(1, Integer.MAX_VALUE);
        budget.accept("x\ny");
        assertThat(budget.markCut(budget.markCut("x"))).isEqualTo("x\u2026");
    }
}
