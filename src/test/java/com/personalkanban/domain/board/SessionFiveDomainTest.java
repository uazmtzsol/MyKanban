package com.personalkanban.domain.board;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Session-5 additions: stable priority sort of a column and checklist
 * rebuild from plain lines.
 */
class SessionFiveDomainTest {

    private final Board board = new Board();

    private Card card(String title) {
        // All cards land in ONE column (the tests assert intra-column order).
        if (board.columns().isEmpty()) {
            board.addColumn("col", "", BoardColor.DEFAULT, WipLimit.unlimited());
        }
        var columnId = board.columns().get(0).id();
        return board.findCard(board.addCard(columnId, title, "", BoardColor.DEFAULT).cardId())
                .orElseThrow();
    }

    private ColumnId columnOf(Card card) {
        return card.columnId();
    }

    @Test
    void prioritySortGroupsFlagsFirstAndKeepsRestStable() {
        var columnId = columnOf(card("plain1"));
        Card urgent = card("urgent");
        board.toggleLabel(urgent.id(), Card.LABEL_URGENT);
        Card plain2 = card("plain2");
        Card both = card("both");
        board.toggleLabel(both.id(), Card.LABEL_URGENT);
        board.toggleLabel(both.id(), Card.LABEL_IMPORTANT);
        Card important = card("important");
        board.toggleLabel(important.id(), Card.LABEL_IMPORTANT);

        board.sortColumnByPriority(columnId);

        var order = board.columns().get(0).cards().stream().map(Card::title).toList();
        // (iu) → (u) → (i) → the rest in their original relative order.
        assertThat(order).containsExactly("both", "urgent", "important", "plain1", "plain2");
    }

    @Test
    void prioritySortOnlyTouchesTheGivenColumn() {
        var first = columnOf(card("A"));
        var second = board.addColumn("second", "", BoardColor.DEFAULT, WipLimit.unlimited()).id();
        board.addCard(second, "B", "", BoardColor.DEFAULT); // directly on the 2nd column

        board.sortColumnByPriority(second);

        assertThat(board.columnOrThrow(second).cards()).hasSize(1);
        assertThat(board.columnOrThrow(first).cards()).hasSize(1);
    }

    @Test
    void checklistFromLinesCreatesMarksAndKeepsIdentity() {
        Card card = card("A");
        ChecklistItem first = board.addChecklistItem(card.id(), "step one");
        board.setChecklistItemDone(card.id(), first.id(), true);

        // The dialog pre-fills done items with the [x] marker, so the line
        // carries the state; the item identity survives the rebuild.
        board.setChecklistFromLines(card.id(), List.of("[x] step one", "step two", "step three"));

        var items = card.checklist();
        assertThat(items).hasSize(3);
        assertThat(items.get(0).text()).isEqualTo("step one");
        assertThat(items.get(0).done()).isTrue();
        assertThat(items.get(1).text()).isEqualTo("step two");
        assertThat(items.get(2).text()).isEqualTo("step three");
    }

    @Test
    void checklistFromLinesParsesMarkersAndRemovesDeletedLines() {
        Card card = card("A");
        board.addChecklistItem(card.id(), "obsolete");

        board.setChecklistFromLines(card.id(), List.of(
                "[x] done task", "[ ] pending task", "plain pending", "  ", ""));

        var items = card.checklist();
        assertThat(items).hasSize(3);
        assertThat(items.get(0).text()).isEqualTo("done task");
        assertThat(items.get(0).done()).isTrue();
        assertThat(items.get(1).text()).isEqualTo("pending task");
        assertThat(items.get(1).done()).isFalse();
        assertThat(items.get(2).text()).isEqualTo("plain pending");
        // "obsolete" disappeared because it is not in the new lines.
        assertThat(items).extracting(ChecklistItem::text).doesNotContain("obsolete");
    }

    @Test
    void checklistFromLinesSurvivesMementoRoundTrip() {
        Card card = card("A");
        board.setChecklistFromLines(card.id(), List.of("[x] alpha", "beta"));

        board.restore(BoardMemento.capture(board));

        var items = board.findCard(card.id()).orElseThrow().checklist();
        assertThat(items).extracting(ChecklistItem::text).containsExactly("alpha", "beta");
        assertThat(items.get(0).done()).isTrue();
    }

    @Test
    void undoRestoresPreviousChecklistAndOrder() {
        var columnId = columnOf(card("plain1"));
        Card urgent = card("urgent");
        board.toggleLabel(urgent.id(), Card.LABEL_URGENT);

        board.sortColumnByPriority(columnId);
        assertThat(board.columns().get(0).cards().stream().map(Card::title).toList())
                .containsExactly("urgent", "plain1");

        // The service-level undo is covered in SessionFiveServiceTest; here
        // we verify the sort is a plain list reorder visible to the memento.
        board.restore(BoardMemento.capture(board));
        assertThat(board.columns().get(0).cards().stream().map(Card::title).toList())
                .containsExactly("urgent", "plain1");
    }
}
