package com.personalkanban.application.sync;

import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardMemento;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.CardLink;
import com.personalkanban.domain.board.CardSnapshot;
import com.personalkanban.domain.board.ChecklistItem;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.ColumnSnapshot;
import com.personalkanban.domain.board.EntryId;
import com.personalkanban.domain.board.ProcessId;
import com.personalkanban.domain.board.ProcessSnapshot;
import com.personalkanban.domain.board.TimelineEntry;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The §4 conflict policy: per-field last-writer-wins, collection union,
 * edit-beats-delete, and deterministic tie-breaks that record a conflict.
 */
class SyncMergeTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final ColumnId COL = new ColumnId("col-1");
    private static final CardId CARD = new CardId("card-1"); 
    private static final ProcessId PROC = new ProcessId("proc-1");

    private static CardSnapshot card(String id, String title, String description, BoardColor color,
                                     List<String> labels, List<ChecklistItem> checklist) {
        return new CardSnapshot(new CardId(id), title, description, color, null, labels, T0,
                "", checklist, null);
    }

    private static ColumnSnapshot column(CardSnapshot... cards) {
        return new ColumnSnapshot(COL, "Todo", "", BoardColor.BLUE, WipLimit.unlimited(), T0,
                false, null, List.of(cards));
    }

    private static BoardMemento board(CardSnapshot... cards) {
        return new BoardMemento(List.of(column(cards)));
    }

    private static BoardMemento board(List<ColumnSnapshot> columns, List<ProcessSnapshot> processes,
                                      List<CardLink> links, List<TimelineEntry> timeline) {
        return new BoardMemento(columns, processes, links, timeline);
    }

    private static CardSnapshot find(BoardMemento memento, String cardId) {
        return memento.columns().get(0).cards().stream()
                .filter(card -> card.id().value().equals(cardId))
                .findFirst()
                .orElseThrow();
    }

    // ------------------------------------------------------------------
    // Scalar fields
    // ------------------------------------------------------------------

    @Test
    void disjointFieldEditsBothSurvive() {
        BoardMemento base = board(card("c1", "Title", "Desc", BoardColor.BLUE, List.of("a"), List.of()));
        BoardMemento local = board(card("c1", "Title2", "Desc", BoardColor.BLUE, List.of("a"), List.of()));
        BoardMemento remote = board(card("c1", "Title", "Desc", BoardColor.RED, List.of("a", "b"), List.of()));

        MergeOutcome outcome = SyncMerge.merge(base, local, remote);

        assertThat(outcome.conflicts()).isEmpty();
        CardSnapshot merged = find(outcome.merged(), "c1");
        assertThat(merged.title()).isEqualTo("Title2");   // local-only edit
        assertThat(merged.color()).isEqualTo(BoardColor.RED); // remote-only edit
        assertThat(merged.labels()).containsExactly("a", "b"); // union
    }

    @Test
    void bothEditingSameFieldRecordsConflictAndPicksDeterministically() {
        BoardMemento base = board(card("c1", "Title", "Desc", BoardColor.BLUE, List.of(), List.of()));
        BoardMemento local = board(card("c1", "Title", "Local", BoardColor.BLUE, List.of(), List.of()));
        BoardMemento remote = board(card("c1", "Title", "Remote", BoardColor.BLUE, List.of(), List.of()));

        MergeOutcome outcome = SyncMerge.merge(base, local, remote);

        assertThat(outcome.hasConflicts()).isTrue();
        assertThat(outcome.conflicts()).singleElement()
                .satisfies(conflict -> {
                    assertThat(conflict.entity()).isEqualTo("card");
                    assertThat(conflict.field()).isEqualTo("description");
                });
        // Greater canonical value wins, identically on both devices.
        assertThat(find(outcome.merged(), "c1").description()).isEqualTo("Remote");
    }

    @Test
    void describeIsStableForTheSameInputsReversed() {
        BoardMemento base = board(card("c1", "Title", "Desc", BoardColor.BLUE, List.of(), List.of()));
        BoardMemento local = board(card("c1", "Title", "Local", BoardColor.BLUE, List.of(), List.of()));
        BoardMemento remote = board(card("c1", "Title", "Remote", BoardColor.BLUE, List.of(), List.of()));

        // Swapping sides must not swap the winner: the choice is by value, not role.
        assertThat(find(SyncMerge.merge(base, local, remote).merged(), "c1").description())
                .isEqualTo(find(SyncMerge.merge(base, remote, local).merged(), "c1").description());
    }

    // ------------------------------------------------------------------
    // Collections
    // ------------------------------------------------------------------

    @Test
    void labelRemovedOnOneSideIsDroppedWhenTheOtherSideKeptIt() {
        BoardMemento base = board(card("c1", "T", "D", BoardColor.BLUE, List.of("a"), List.of()));
        BoardMemento local = board(card("c1", "T", "D", BoardColor.BLUE, List.of(), List.of()));
        BoardMemento remote = board(card("c1", "T", "D", BoardColor.BLUE, List.of("a"), List.of()));

        CardSnapshot merged = find(SyncMerge.merge(base, local, remote).merged(), "c1");
        assertThat(merged.labels()).isEmpty();
    }

    @Test
    void checklistItemsUniteByIdWithFieldMerge() {
        ChecklistItem i1 = new ChecklistItem("i1", "one", false);
        ChecklistItem i2 = new ChecklistItem("i2", "two", false);
        ChecklistItem i1Done = new ChecklistItem("i1", "one", true);
        ChecklistItem i3 = new ChecklistItem("i3", "three", false);

        BoardMemento base = board(card("c1", "T", "D", BoardColor.BLUE, List.of(), List.of(i1)));
        BoardMemento local = board(card("c1", "T", "D", BoardColor.BLUE, List.of(), List.of(i1Done)));
        BoardMemento remote = board(card("c1", "T", "D", BoardColor.BLUE, List.of(), List.of(i1, i2, i3)));

        List<ChecklistItem> merged = find(SyncMerge.merge(base, local, remote).merged(), "c1").checklist();
        assertThat(merged).extracting(ChecklistItem::id).containsExactlyInAnyOrder("i1", "i2", "i3");
        assertThat(merged).filteredOn(item -> item.id().equals("i1"))
                .singleElement().satisfies(item -> assertThat(item.done()).isTrue());
    }

    // ------------------------------------------------------------------
    // Deletion vs edit
    // ------------------------------------------------------------------

    @Test
    void editBeatsDeleteAndRecordsConflict() {
        BoardMemento base = board(card("c1", "T", "D", BoardColor.BLUE, List.of(), List.of()));
        BoardMemento local = board(); // card removed locally
        BoardMemento remote = board(card("c1", "Edited", "D", BoardColor.BLUE, List.of(), List.of()));

        MergeOutcome outcome = SyncMerge.merge(base, local, remote);

        assertThat(outcome.merged().columns().get(0).cards()).hasSize(1);
        assertThat(find(outcome.merged(), "c1").title()).isEqualTo("Edited");
        assertThat(outcome.conflicts()).singleElement()
                .satisfies(conflict -> assertThat(conflict.field()).isEqualTo("deleted"));
    }

    @Test
    void unchangedObjectDeletedOnOneSideIsDropped() {
        BoardMemento base = board(card("c1", "T", "D", BoardColor.BLUE, List.of(), List.of()));
        BoardMemento local = board();
        BoardMemento remote = board(card("c1", "T", "D", BoardColor.BLUE, List.of(), List.of()));

        MergeOutcome outcome = SyncMerge.merge(base, local, remote);
        assertThat(outcome.merged().columns().get(0).cards()).isEmpty();
        assertThat(outcome.conflicts()).isEmpty();
    }

    @Test
    void cardAddedOnEitherSideIsKept() {
        BoardMemento base = board();
        BoardMemento local = board(card("c1", "L", "D", BoardColor.BLUE, List.of(), List.of()));
        BoardMemento remote = board(card("c2", "R", "D", BoardColor.BLUE, List.of(), List.of()));

        MergeOutcome outcome = SyncMerge.merge(base, local, remote);
        assertThat(outcome.merged().columns().get(0).cards())
                .extracting(CardSnapshot::title).containsExactly("L", "R");
    }

    // ------------------------------------------------------------------
    // Processes, links, timeline
    // ------------------------------------------------------------------

    @Test
    void timelineUnionAppendsRemoteEntries() {
        TimelineEntry localEntry = TimelineEntry.restore(CARD, new EntryId("e1"), T0, T0.plusSeconds(60), "a");
        TimelineEntry remoteEntry = TimelineEntry.restore(CARD, new EntryId("e2"), T0, T0.plusSeconds(30), "b");
        BoardMemento base = board();
        BoardMemento local = board(List.of(column()), List.of(), List.of(), List.of(localEntry));
        BoardMemento remote = board(List.of(column()), List.of(), List.of(), List.of(remoteEntry));

        MergeOutcome outcome = SyncMerge.merge(base, local, remote);
        assertThat(outcome.merged().timeline()).extracting(entry -> entry.id().value())
                .containsExactlyInAnyOrder("e1", "e2");
    }

    @Test
    void processNamesMergePerField() {
        BoardMemento base = board(List.of(column()),
                List.of(new ProcessSnapshot(PROC, "Old")), List.of(), List.of());
        BoardMemento local = board(List.of(column()),
                List.of(new ProcessSnapshot(PROC, "New")), List.of(), List.of());
        BoardMemento remote = base;

        MergeOutcome outcome = SyncMerge.merge(base, local, remote);
        assertThat(outcome.merged().processes()).singleElement()
                .satisfies(process -> assertThat(process.name()).isEqualTo("New"));
    }

    @Test
    void linkPresentOnBothSidesSurvives() {
        CardId a = new CardId("a");
        CardId b = new CardId("b");
        BoardMemento base = board(List.of(column()), List.of(), List.of(), List.of());
        BoardMemento local = board(List.of(column()), List.of(), List.of(new CardLink(a, b)), List.of());
        BoardMemento remote = local;

        MergeOutcome outcome = SyncMerge.merge(base, local, remote);
        assertThat(outcome.merged().links()).containsExactly(new CardLink(a, b));
    }
}
