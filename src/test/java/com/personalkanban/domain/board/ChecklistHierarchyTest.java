package com.personalkanban.domain.board;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * User request T16 · B8: the checklist gains <b>one level</b> of subtasks.
 * The aggregate owns the invariant (no grandchildren), a parent takes its
 * subtasks with it, and the progress counter covers both levels.
 */
class ChecklistHierarchyTest {

    private final Board board = new Board();

    private Card card(String title) {
        var columnId = board.addColumn(title, "", BoardColor.DEFAULT, WipLimit.unlimited()).id();
        CardAdded added = board.addCard(columnId, title, "", BoardColor.DEFAULT);
        return board.findCard(added.cardId()).orElseThrow();
    }

    @Test
    void aSubtaskHangsFromItsParentAndTheCounterSeesBoth() {
        Card card = card("A");
        ChecklistItem parent = board.addChecklistItem(card.id(), "parent");
        ChecklistItem child = board.addChecklistItem(card.id(), "child", parent.id());

        assertThat(parent.isSubtask()).isFalse();
        assertThat(child.isSubtask()).isTrue();
        assertThat(child.parentId()).isEqualTo(parent.id());
        assertThat(card.checklistChildrenOf(parent.id())).containsExactly(child);
        assertThat(card.subtaskCount(parent.id())).isEqualTo(1);
        assertThat(card.subtaskTotalCount()).isEqualTo(1);

        board.setChecklistItemDone(card.id(), child.id(), true);
        assertThat(card.doneChecklistCount()).isEqualTo(1);
        assertThat(card.checklistCount()).isEqualTo(2);
    }

    @Test
    void aSubtaskCannotHaveItsOwnSubtasks() {
        Card card = card("A");
        ChecklistItem parent = board.addChecklistItem(card.id(), "parent");
        ChecklistItem child = board.addChecklistItem(card.id(), "child", parent.id());

        assertThatThrownBy(() -> board.addChecklistItem(card.id(), "grandchild", child.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subtask");
    }

    @Test
    void removingAParentTakesItsSubtaskWithIt() {
        Card card = card("A");
        ChecklistItem parent = board.addChecklistItem(card.id(), "parent");
        ChecklistItem child = board.addChecklistItem(card.id(), "child", parent.id());
        ChecklistItem other = board.addChecklistItem(card.id(), "other");

        board.removeChecklistItem(card.id(), parent.id());

        assertThat(card.checklist()).containsExactly(other);
    }

    @Test
    void anItemWithSubtasksCannotBecomeASubtask() {
        Card card = card("A");
        ChecklistItem first = board.addChecklistItem(card.id(), "first");
        board.addChecklistItem(card.id(), "child", first.id());
        ChecklistItem second = board.addChecklistItem(card.id(), "second");

        assertThatThrownBy(() -> board.setChecklistItemParent(card.id(), first.id(), second.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subtasks");
    }

    @Test
    void indentAndOutdentAreOneLevelMoves() {
        Card card = card("A");
        ChecklistItem first = board.addChecklistItem(card.id(), "first");
        ChecklistItem second = board.addChecklistItem(card.id(), "second");

        board.setChecklistItemParent(card.id(), second.id(), first.id());
        assertThat(card.checklistItem(second.id()).parentId()).isEqualTo(first.id());

        board.setChecklistItemParent(card.id(), second.id(), null);
        assertThat(card.checklistItem(second.id()).isSubtask()).isFalse();
    }

    @Test
    void hierarchySurvivesAMementoRoundTrip() {
        Card card = card("A");
        ChecklistItem parent = board.addChecklistItem(card.id(), "parent");
        ChecklistItem child = board.addChecklistItem(card.id(), "child", parent.id());

        BoardMemento memento = BoardMemento.capture(board);
        Board restored = new Board();
        restored.restore(memento);

        Card restoredCard = restored.findCard(card.id()).orElseThrow();
        assertThat(restoredCard.checklistItem(child.id()).parentId()).isEqualTo(parent.id());
        assertThat(restoredCard.checklistChildrenOf(parent.id())).hasSize(1);
    }

    @Test
    void twoSpacesInTheAdvancedFormMeanASubtask() {
        Card card = card("A");

        board.setChecklistFromLines(card.id(), List.of("parent", "  child", "sibling"));

        List<ChecklistItem> items = card.checklist();
        assertThat(items).hasSize(3);
        assertThat(items.get(0).isSubtask()).isFalse();
        assertThat(items.get(1).parentId()).isEqualTo(items.get(0).id());
        assertThat(items.get(2).isSubtask()).isFalse();
        assertThat(card.subtaskTotalCount()).isEqualTo(1);
    }
}
