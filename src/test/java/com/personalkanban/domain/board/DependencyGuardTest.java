package com.personalkanban.domain.board;

import com.personalkanban.domain.exception.CyclicDependencyException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure-policy tests for the precedence links (session 4.6): acyclicity
 * enforcement and the suggested execution order.
 */
class DependencyGuardTest {

    private final Board board = new Board();

    private CardId cardOf(String title) {
        return addCard(title).cardId();
    }

    private CardAdded addCard(String title) {
        return board.addCard(board.addColumn(title, "", BoardColor.DEFAULT, WipLimit.unlimited()).id(),
                title, "", BoardColor.DEFAULT);
    }

    @Test
    void selfLinkIsRejected() {
        CardId card = cardOf("A");
        assertThatThrownBy(() -> DependencyGuard.requireLinkable(board, card, card))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownCardIsRejected() {
        CardId known = cardOf("A");
        CardId ghost = new CardId("no-such-card");
        assertThatThrownBy(() -> DependencyGuard.requireLinkable(board, known, ghost))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DependencyGuard.requireLinkable(board, ghost, known))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void simpleLinkAndItsReverseAreAcceptedWhileAcyclic() {
        CardId a = cardOf("A");
        CardId b = cardOf("B");
        CardId other = cardOf("Other");
        assertThatCode(() -> DependencyGuard.requireLinkable(board, a, b)).doesNotThrowAnyException();
        board.linkCards(a, b);
        // b→other is a different pair; still fine while no cycle would close.
        assertThatCode(() -> DependencyGuard.requireLinkable(board, b, other))
                .doesNotThrowAnyException();
    }

    @Test
    void directTwoCardCycleIsRejected() {
        CardId a = cardOf("A");
        CardId b = cardOf("B");
        board.linkCards(a, b);
        assertThatThrownBy(() -> DependencyGuard.requireLinkable(board, b, a))
                .isInstanceOf(CyclicDependencyException.class);
    }

    @Test
    void transitiveCycleIsRejected() {
        CardId a = cardOf("A");
        CardId b = cardOf("B");
        CardId c = cardOf("C");
        board.linkCards(a, b);
        board.linkCards(b, c);
        assertThatThrownBy(() -> DependencyGuard.requireLinkable(board, c, a))
                .isInstanceOf(CyclicDependencyException.class);
    }

    @Test
    void diamondIsAllowed() {
        CardId a = cardOf("A");
        CardId b = cardOf("B");
        CardId c = cardOf("C");
        CardId d = cardOf("D");
        board.linkCards(a, b);
        board.linkCards(a, c);
        board.linkCards(b, d);
        board.linkCards(c, d);
        assertThat(board.linkList()).hasSize(4);
    }

    @Test
    void suggestedOrderRespectsPrecedence() {
        CardId a = cardOf("A");
        CardId b = cardOf("B");
        CardId c = cardOf("C");
        board.linkCards(a, b);
        board.linkCards(b, c);

        var result = DependencyGuard.topologicalOrder(board, List.of(c, a, b));
        assertThat(result.ordered()).containsExactly(a, b, c);
        assertThat(result.cycleRemaining()).isEmpty();
    }

    @Test
    void suggestedOrderKeepsStableOrderOnTies() {
        CardId a = cardOf("A");
        CardId b = cardOf("B");
        CardId c = cardOf("C");

        var result = DependencyGuard.topologicalOrder(board, List.of(c, a, b));
        assertThat(result.ordered()).containsExactly(c, a, b);
    }

    @Test
    void suggestedOrderReportsCycleRemainder() {
        CardId a = cardOf("A");
        CardId b = cardOf("B");
        CardId c = cardOf("C");
        board.linkCards(a, b);
        board.linkCards(b, c);
        // Force a cycle directly through the memento (the guard would reject
        // it): restore REPLACES links, so include the existing two as well.
        board.restore(new BoardMemento(
                board.columns().stream().map(ColumnSnapshot::from).toList(),
                List.of(),
                List.of(new CardLink(a, b), new CardLink(b, c), new CardLink(c, a))));

        var result = DependencyGuard.topologicalOrder(board, List.of(a, b, c));
        assertThat(result.ordered()).isEmpty(); // everything is inside the cycle
        assertThat(result.cycleRemaining()).containsExactlyInAnyOrder(a, b, c);
    }
}
