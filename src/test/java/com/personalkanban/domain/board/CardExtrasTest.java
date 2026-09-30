package com.personalkanban.domain.board;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Domain tests for notes, checklist and process state on the aggregate
 * (sessions 4.4-4.6), including memento round-trips for undo/redo.
 */
class CardExtrasTest {

    private final Board board = new Board();

    private Card card(String title) {
        var columnId = board.addColumn(title, "", BoardColor.DEFAULT, WipLimit.unlimited()).id();
        CardAdded added = board.addCard(columnId, title, "", BoardColor.DEFAULT);
        return board.findCard(added.cardId()).orElseThrow();
    }

    // ------------------------------------------------------------------
    // Notes (4.4)
    // ------------------------------------------------------------------

    @Test
    void notesRoundTripThroughMemento() {
        Card card = card("A");
        board.annotateCard(card.id(), "  remember the milk  ");
        assertThat(card.notes()).isEqualTo("remember the milk");

        board.restore(BoardMemento.capture(board).toBoard() == null
                ? BoardMemento.empty()
                : BoardMemento.capture(board));
        // After a restore the value must survive (undo covers notes).
        assertThat(board.findCard(card.id()).orElseThrow().notes())
                .isEqualTo("remember the milk");
    }

    @Test
    void nullNotesNormalizeToEmpty() {
        Card card = card("A");
        board.annotateCard(card.id(), null);
        assertThat(card.notes()).isEmpty();
    }

    // ------------------------------------------------------------------
    // Checklist (4.5)
    // ------------------------------------------------------------------

    @Test
    void checklistAddToggleRenameRemove() {
        Card card = card("A");
        ChecklistItem item = board.addChecklistItem(card.id(), " step one ");
        assertThat(item.text()).isEqualTo("step one");
        assertThat(card.checklist()).hasSize(1);

        board.setChecklistItemDone(card.id(), item.id(), true);
        assertThat(card.checklist().get(0).done()).isTrue();

        board.renameChecklistItem(card.id(), item.id(), "renamed");
        assertThat(card.checklist().get(0).text()).isEqualTo("renamed");

        board.removeChecklistItem(card.id(), item.id());
        assertThat(card.checklist()).isEmpty();
    }

    @Test
    void blankChecklistItemIsRejected() {
        Card card = card("A");
        assertThatThrownBy(() -> board.addChecklistItem(card.id(), "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownChecklistItemThrows() {
        Card card = card("A");
        board.addChecklistItem(card.id(), "step one");
        assertThatThrownBy(() -> board.setChecklistItemDone(card.id(), "ghost", true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void checklistSurvivesUndoSnapshot() {
        Card card = card("A");
        ChecklistItem item = board.addChecklistItem(card.id(), "step one");
        BoardMemento before = BoardMemento.capture(board);

        board.setChecklistItemDone(card.id(), item.id(), true);
        assertThat(board.findCard(card.id()).orElseThrow().checklist().get(0).done()).isTrue();

        board.restore(before);
        ChecklistItem restored = board.findCard(card.id()).orElseThrow().checklist().get(0);
        assertThat(restored.done()).isFalse();
        assertThat(restored.id()).isEqualTo(item.id()); // stable identity
    }

    // ------------------------------------------------------------------
    // Convert item into card (4.5)
    // ------------------------------------------------------------------

    @Test
    void convertedItemBecomesCardInSameColumnAndLeavesChecklist() {
        Card source = card("A");
        ChecklistItem item = board.addChecklistItem(source.id(), "buy milk");

        CardAdded event = board.convertChecklistItemToCard(source.id(), item.id());

        assertThat(source.checklist()).isEmpty();
        Card created = board.findCard(event.cardId()).orElseThrow();
        assertThat(created.title()).isEqualTo("buy milk");
        assertThat(created.columnId()).isEqualTo(source.columnId());
    }

    @Test
    void convertedItemRollsBackWithUndo() {
        Card source = card("A");
        ChecklistItem item = board.addChecklistItem(source.id(), "buy milk");
        BoardMemento before = BoardMemento.capture(board);

        board.convertChecklistItemToCard(source.id(), item.id());
        assertThat(board.cardCount()).isEqualTo(2);
        assertThat(source.checklist()).isEmpty();

        board.restore(before);
        assertThat(board.cardCount()).isEqualTo(1);
        assertThat(board.findCard(source.id()).orElseThrow().checklist())
                .extracting(ChecklistItem::text)
                .containsExactly("buy milk");
    }

    // ------------------------------------------------------------------
    // Processes (4.6)
    // ------------------------------------------------------------------

    @Test
    void processLifecycleAndMembership() {
        Card a = card("A");
        Card b = card("B");
        Process process = board.addProcess("Mudanza");
        board.assignCardToProcess(a.id(), process.id());
        board.assignCardToProcess(b.id(), process.id());

        assertThat(board.processList()).extracting(Process::name).containsExactly("Mudanza");
        assertThat(board.cardsOfProcess(process.id()))
                .extracting(Card::id)
                .containsExactlyInAnyOrder(a.id(), b.id());

        board.renameProcess(process.id(), "Cambio de casa");
        assertThat(process.name()).isEqualTo("Cambio de casa");

        board.removeProcess(process.id());
        assertThat(board.processList()).isEmpty();
        // Members survive, just unassigned.
        assertThat(board.findCard(a.id()).orElseThrow().processId()).isNull();
    }

    @Test
    void assigningToUnknownProcessThrows() {
        Card a = card("A");
        assertThatThrownBy(() -> board.assignCardToProcess(a.id(), new ProcessId("ghost")))
                .isInstanceOf(com.personalkanban.domain.exception.NotFoundException.class);
    }

    @Test
    void removingCardDetachesItsLinks() {
        Card a = card("A");
        Card b = card("B");
        board.linkCards(a.id(), b.id());
        board.removeCard(a.id());
        assertThat(board.incomingPredecessorsOf(b.id())).isEmpty(); // predecessor gone
    }

    @Test
    void processesAndLinksSurviveUndoSnapshot() {
        Card a = card("A");
        Card b = card("B");
        Process process = board.addProcess("P");
        board.assignCardToProcess(a.id(), process.id());
        board.linkCards(a.id(), b.id());
        BoardMemento before = BoardMemento.capture(board);

        board.removeProcess(process.id());
        board.unlinkCards(a.id(), b.id());

        board.restore(before);
        assertThat(board.processList()).extracting(Process::name).containsExactly("P");
        assertThat(board.findCard(a.id()).orElseThrow().processId()).isEqualTo(process.id());
        assertThat(board.linkList()).hasSize(1);
    }
}
