package com.personalkanban.domain.board;

import com.personalkanban.domain.exception.WipLimitBulkException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Bulk card operations backing the multi-selection UI use cases. */
class BulkCardOperationsTest {

    private Board board;
    private ColumnId a;
    private ColumnId b;

    @BeforeEach
    void setUp() {
        board = new Board();
        a = board.addColumn("A", "", BoardColor.BLUE, WipLimit.unlimited()).id();
        b = board.addColumn("B", "", BoardColor.GREEN, WipLimit.unlimited()).id();
    }

    private CardId card(ColumnId column, String title, String... labels) {
        return board.addCard(column, title, "", BoardColor.GRAY, null, List.of(labels)).cardId();
    }

    @Test
    void addLabelsMergesCaseInsensitivelyKeepingFirstSpelling() {
        CardId c1 = card(a, "one", "uaz");
        CardId c2 = card(a, "two");

        board.addLabels(List.of(c1, c2), List.of("uaz", "urgent"));

        assertThat(board.findCard(c1).orElseThrow().labels()).containsExactly("uaz", "urgent");
        assertThat(board.findCard(c2).orElseThrow().labels()).containsExactly("uaz", "urgent");
    }

    @Test
    void removeLabelsIsCaseInsensitiveAndIgnoresMissing() {
        CardId c1 = card(a, "one", "UAZ", "urgent");
        CardId c2 = card(a, "two", "home");

        board.removeLabels(List.of(c1, c2), List.of("uaz", "nope"));

        assertThat(board.findCard(c1).orElseThrow().labels()).containsExactly("urgent");
        assertThat(board.findCard(c2).orElseThrow().labels()).containsExactly("home");
    }

    @Test
    void recolorCardsAppliesToAllSelected() {
        CardId c1 = card(a, "one");
        CardId c2 = card(a, "two");

        board.recolorCards(List.of(c1, c2), BoardColor.PINK);

        assertThat(board.findCard(c1).orElseThrow().color()).isEqualTo(BoardColor.PINK);
        assertThat(board.findCard(c2).orElseThrow().color()).isEqualTo(BoardColor.PINK);
    }

    @Test
    void removeCardsDeletesEverySelectedCard() {
        CardId c1 = card(a, "one");
        CardId c2 = card(a, "two");
        CardId keep = card(a, "keep");

        board.removeCards(List.of(c1, c2));

        assertThat(board.findCard(c1)).isEmpty();
        assertThat(board.findCard(c2)).isEmpty();
        assertThat(board.findCard(keep)).isPresent();
    }

    @Test
    void moveCardsToColumnAppendsInOrderAcrossSources() {
        ColumnId c = board.addColumn("C", "", BoardColor.TEAL, WipLimit.unlimited()).id();
        CardId c1 = card(a, "one");
        CardId c2 = card(b, "two");
        CardId c3 = card(a, "three");

        board.moveCardsToColumn(List.of(c1, c2, c3), c);

        assertThat(board.columnOrThrow(c).cards()).extracting(Card::title)
                .containsExactly("one", "two", "three");
        assertThat(board.columnOrThrow(a).cardCount()).isZero();
        assertThat(board.columnOrThrow(b).cardCount()).isZero();
    }

    @Test
    void moveCardsIgnoresCardsAlreadyInTargetColumn() {
        CardId c1 = card(a, "one");
        CardId inB = card(b, "already");
        CardId c2 = card(a, "two");

        board.moveCardsToColumn(List.of(c1, inB, c2), b);

        assertThat(board.columnOrThrow(b).cards()).extracting(Card::title)
                .containsExactly("already", "one", "two");
    }

    @Test
    void moveCardsRejectsWholeBatchWhenWipWouldBeExceeded() {
        ColumnId limited = board.addColumn("L", "", BoardColor.RED, WipLimit.of(3)).id();
        card(limited, "occupied-1");
        card(limited, "occupied-2");
        CardId c1 = card(a, "one");
        CardId c2 = card(a, "two");

        // 2 occupied + 2 moving > 3: the whole batch must be refused.
        assertThatThrownBy(() -> board.moveCardsToColumn(List.of(c1, c2), limited))
                .isInstanceOf(WipLimitBulkException.class)
                .satisfies(e -> assertThat(
                        ((WipLimitBulkException) e).excess()).isEqualTo(1));

        // Nothing moved: target untouched, sources untouched.
        assertThat(board.columnOrThrow(limited).cardCount()).isEqualTo(2);
        assertThat(board.columnOrThrow(a).cardCount()).isEqualTo(2);
    }

    @Test
    void toggleLabelAddsRemovesAndMatchesCaseInsensitively() {
        CardId c = card(a, "one");

        assertThat(board.toggleLabel(c, Card.LABEL_URGENT)).isTrue();
        assertThat(board.findCard(c).orElseThrow().labels()).contains("Urgente");
        assertThat(board.findCard(c).orElseThrow().hasLabelIgnoreCase("urgente")).isTrue();

        // Toggling a different casing of the same label removes it.
        assertThat(board.toggleLabel(c, "URGENTE")).isFalse();
        assertThat(board.findCard(c).orElseThrow().labels()).isEmpty();
    }

    @Test
    void toggleLabelRejectsUnknownCardAndRespectsLabelCap() {
        CardId ghost = new CardId("no-such");
        assertThatThrownBy(() -> board.toggleLabel(ghost, Card.LABEL_IMPORTANT))
                .isInstanceOf(com.personalkanban.domain.exception.NotFoundException.class);

        CardId full = card(a, "full", "l1", "l2", "l3", "l4", "l5", "l6", "l7", "l8");
        assertThatThrownBy(() -> board.toggleLabel(full, Card.LABEL_IMPORTANT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moveCardsFailsAtomicallyOnUnknownCard() {
        CardId c1 = card(a, "one");
        CardId ghost = new CardId("no-such-card");

        assertThatThrownBy(() -> board.moveCardsToColumn(List.of(c1, ghost), b))
                .isInstanceOf(com.personalkanban.domain.exception.NotFoundException.class);
        assertThat(board.columnOrThrow(a).cardCount()).isEqualTo(1);
        assertThat(board.columnOrThrow(b).cardCount()).isZero();
    }
}
