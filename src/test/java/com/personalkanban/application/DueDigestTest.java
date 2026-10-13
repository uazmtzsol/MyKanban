package com.personalkanban.application;

import com.personalkanban.application.port.InMemoryBoardRepository;
import com.personalkanban.application.port.InMemorySettingsStore;
import com.personalkanban.application.port.InMemoryUndoHistory;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The due-date ranges (session 15, T5 = N-6 + B3). The boundaries are the
 * interesting part: "today" belongs to the week (the week view is what you have
 * to do in the next 7 days), yesterday is overdue and day 7 is already out of
 * the week.
 */
class DueDigestTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);

    private final InMemoryBoardRepository repository = new InMemoryBoardRepository();
    private final InMemoryUndoHistory history = new InMemoryUndoHistory();
    private final InMemorySettingsStore settings = new InMemorySettingsStore();
    private final BoardService service = new BoardService(repository, history, settings);

    private final List<Card> cards = new ArrayList<>();

    /** Builds a card with the given due date (null = no date) and collects it. */
    private Card cardDue(LocalDate dueDate) {
        ColumnId column = service.board().columns().isEmpty()
                ? service.addColumn("To Do", "", BoardColor.DEFAULT, WipLimit.unlimited())
                : service.board().columns().get(0).id();
        var id = service.addCard(column, "card " + cards.size(), "", BoardColor.DEFAULT,
                dueDate, List.of());
        Card card = service.board().findCard(id).orElseThrow();
        cards.add(card);
        return card;
    }

    private void setUpBoard() {
        service.openBoard(service.createBoard("Due digest"));
        cardDue(null); // first call creates the column
    }

    private DueDigest.Counts counts() {
        return DueDigest.of(cards, TODAY).counts();
    }

    @Test
    void countsEachRangeSeparately() {
        setUpBoard();
        cards.clear();
        cardDue(TODAY.minusDays(3));   // overdue
        cardDue(TODAY.minusDays(1));   // overdue
        cardDue(TODAY);                // today (also inside the week)
        cardDue(TODAY.plusDays(2));    // this week
        cardDue(TODAY.plusDays(6));    // last day of the week
        cardDue(TODAY.plusDays(7));    // next week: out
        cardDue(null);                 // no date

        DueDigest.Counts counts = counts();
        assertThat(counts.overdue()).isEqualTo(2);
        assertThat(counts.dueToday()).isEqualTo(1);
        assertThat(counts.dueThisWeek()).isEqualTo(3);
        assertThat(counts.total()).as("no double counting").isEqualTo(5);
        assertThat(counts.anything()).isTrue();
    }

    @Test
    void anEmptyOrUndatedBoardHasNothingToReport() {
        setUpBoard();

        assertThat(DueDigest.of(List.of(), TODAY).counts().anything()).isFalse();
        assertThat(counts().anything()).as("only the undated card exists").isFalse();
        assertThat(counts().total()).isZero();
    }

    @Test
    void aWeekOnlyBoardReportsButIsNotOverdue() {
        setUpBoard();
        cardDue(TODAY.plusDays(4));

        DueDigest.Counts counts = counts();
        assertThat(counts.overdue()).isZero();
        assertThat(counts.dueToday()).isZero();
        assertThat(counts.dueThisWeek()).isEqualTo(1);
        assertThat(counts.anything()).isTrue();
    }

    @Test
    void matchesFollowsTheSameBoundariesAsTheCounts() {
        setUpBoard();
        Card yesterday = cardDue(TODAY.minusDays(1));
        Card today = cardDue(TODAY);
        Card inSixDays = cardDue(TODAY.plusDays(6));
        Card inSevenDays = cardDue(TODAY.plusDays(7));
        Card undated = cardDue(null);

        assertThat(DueDigest.matches(yesterday, DueDigest.Scope.OVERDUE, TODAY)).isTrue();
        assertThat(DueDigest.matches(today, DueDigest.Scope.OVERDUE, TODAY)).isFalse();

        assertThat(DueDigest.matches(today, DueDigest.Scope.TODAY, TODAY)).isTrue();
        assertThat(DueDigest.matches(yesterday, DueDigest.Scope.TODAY, TODAY)).isFalse();

        assertThat(DueDigest.matches(today, DueDigest.Scope.WEEK, TODAY)).isTrue();
        assertThat(DueDigest.matches(inSixDays, DueDigest.Scope.WEEK, TODAY)).isTrue();
        assertThat(DueDigest.matches(inSevenDays, DueDigest.Scope.WEEK, TODAY)).isFalse();
        assertThat(DueDigest.matches(yesterday, DueDigest.Scope.WEEK, TODAY))
                .as("an overdue card is not \"this week\"")
                .isFalse();

        for (DueDigest.Scope scope : DueDigest.Scope.values()) {
            assertThat(DueDigest.matches(undated, scope, TODAY))
                    .as("scope %s ignores a card without a date", scope)
                    .isFalse();
        }
    }

    @Test
    void theCountsAndTheMatcherAgreeOnTheSameBoard() {
        setUpBoard();
        cardDue(TODAY.minusDays(2));
        cardDue(TODAY);
        cardDue(TODAY.plusDays(1));
        cardDue(TODAY.plusDays(9));

        DueDigest.Counts counts = counts();
        for (DueDigest.Scope scope : DueDigest.Scope.values()) {
            long matched = cards.stream()
                    .filter(card -> DueDigest.matches(card, scope, TODAY))
                    .count();
            long reported = switch (scope) {
                case OVERDUE -> counts.overdue();
                case TODAY -> counts.dueToday();
                case WEEK -> counts.dueThisWeek();
            };
            assertThat(matched).as("scope %s", scope).isEqualTo(reported);
        }
    }
}
