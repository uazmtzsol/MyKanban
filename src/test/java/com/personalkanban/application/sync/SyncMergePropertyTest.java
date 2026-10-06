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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-style tests for the three-way merge (design §4). There is no
 * property-testing library on the (offline) classpath, so each property runs
 * over many deterministically-seeded random boards and asserts an invariant
 * that must hold for every input — the kind of bug an example-based test
 * misses.
 *
 * <p>Snapshot DTOs are records, but {@code TimelineEntry} is mutable and has
 * no value equality, so structural comparison goes through {@link #render}
 * (a stable text rendering of the whole memento) rather than {@code equals}.</p>
 */
class SyncMergePropertyTest {

    private static final long SEED = 20261005L;
    private static final int CASES = 150;
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final LocalDate D0 = LocalDate.parse("2026-01-01");
    private static final List<String> TITLES = List.of("Alpha", "Beta", "Gamma", "Delta", "Epsilon");
    private static final List<BoardColor> COLORS = BoardColor.palette();
    private static final List<String> COL_POOL = List.of("col0", "col1", "col2");

    // ------------------------------------------------------------------
    // Properties
    // ------------------------------------------------------------------

    @Test
    void mergeIsDeterministic() {
        Random r = new Random(SEED);
        for (int i = 0; i < CASES; i++) {
            BoardMemento base = randomBoard(r);
            BoardMemento local = mutateStructure(r, base);
            BoardMemento remote = mutateStructure(r, base);
            assertThat(render(SyncMerge.merge(base, local, remote).merged()))
                    .as("case %d", i)
                    .isEqualTo(render(SyncMerge.merge(base, local, remote).merged()));
        }
    }

    @Test
    void mergingIdenticalSnapshotsChangesNothing() {
        Random r = new Random(SEED);
        for (int i = 0; i < CASES; i++) {
            BoardMemento base = randomBoard(r);
            MergeOutcome outcome = SyncMerge.merge(base, base, base);
            assertThat(render(outcome.merged())).as("case %d", i).isEqualTo(render(base));
            assertThat(outcome.conflicts()).as("case %d", i).isEmpty();
        }
    }

    /** If the remote did not move, the merge must reproduce the local exactly. */
    @Test
    void mergeNeverLosesALocalChangeWhenRemoteIsUnchanged() {
        Random r = new Random(SEED);
        for (int i = 0; i < CASES; i++) {
            BoardMemento base = randomBoard(r);
            BoardMemento local = editFields(r, base);
            assertThat(render(SyncMerge.merge(base, local, base).merged()))
                    .as("case %d", i).isEqualTo(render(local));
        }
    }

    /** Symmetric case: if the local did not move, the merge reproduces the remote. */
    @Test
    void mergeNeverLosesARemoteChangeWhenLocalIsUnchanged() {
        Random r = new Random(SEED);
        for (int i = 0; i < CASES; i++) {
            BoardMemento base = randomBoard(r);
            BoardMemento remote = editFields(r, base);
            assertThat(render(SyncMerge.merge(base, base, remote).merged()))
                    .as("case %d", i).isEqualTo(render(remote));
        }
    }

    /** Merging an already-merged board with itself is a no-op (idempotence). */
    @Test
    void mergeIsAFixedPoint() {
        Random r = new Random(SEED);
        for (int i = 0; i < CASES; i++) {
            BoardMemento base = randomBoard(r);
            BoardMemento local = mutateStructure(r, base);
            BoardMemento remote = mutateStructure(r, base);
            BoardMemento merged = SyncMerge.merge(base, local, remote).merged();
            assertThat(render(SyncMerge.merge(base, merged, merged).merged()))
                    .as("case %d", i).isEqualTo(render(merged));
        }
    }

    /**
     * The merge must never produce a snapshot with a null required field,
     * whatever combination of adds, deletes and edits it faces — this is the
     * invariant the "edit beats delete" repair originally protected.
     */
    @Test
    void mergeNeverProducesNullRequiredFields() {
        Random r = new Random(SEED);
        for (int i = 0; i < CASES; i++) {
            BoardMemento base = randomBoard(r);
            BoardMemento local = mutateStructure(r, base);
            BoardMemento remote = mutateStructure(r, base);
            BoardMemento merged = SyncMerge.merge(base, local, remote).merged();
            assertValid(merged, i);
        }
    }

    /** Disjoint edits on the same card must both survive, with no conflict. */
    @Test
    void disjointEditsOnTheSameCardBothSurvive() {
        Random r = new Random(SEED);
        for (int i = 0; i < CASES; i++) {
            BoardMemento base = randomBoardWithACard(r);
            BoardMemento local = withCardTitle(base, "LOCAL-" + i);
            BoardMemento remote = withCardDescription(base, "REMOTE-" + i);

            MergeOutcome outcome = SyncMerge.merge(base, local, remote);
            CardSnapshot card = firstCard(outcome.merged());
            assertThat(card.title()).as("case %d", i).isEqualTo("LOCAL-" + i);
            assertThat(card.description()).as("case %d", i).isEqualTo("REMOTE-" + i);
            assertThat(outcome.conflicts()).as("case %d", i).isEmpty();
        }
    }

    /** When both sides edit the same field, the winner is the same either way. */
    @Test
    void tieBreakWinnerDoesNotDependOnSideOrder() {
        Random r = new Random(SEED);
        for (int i = 0; i < CASES; i++) {
            BoardMemento base = randomBoardWithACard(r);
            BoardMemento a = withCardTitle(base, "AAAA");
            BoardMemento b = withCardTitle(base, "ZZZZ");

            String forward = firstCard(SyncMerge.merge(base, a, b).merged()).title();
            String reversed = firstCard(SyncMerge.merge(base, b, a).merged()).title();
            assertThat(forward).as("case %d", i).isEqualTo(reversed);
        }
    }

    /**
     * Regression found by the properties above: deleting the only column on
     * one side while the other side edited a card inside it must neither lose
     * the edit nor build an orphan card (the failure was a NPE).
     */
    @Test
    void editedCardSurvivesWhenItsOnlyColumnWasDeletedOnTheOtherSide() {
        BoardMemento base = new BoardMemento(List.of(columnWithCard("Title")));
        BoardMemento local = new BoardMemento(List.of());   // the only column is gone
        BoardMemento remote = new BoardMemento(List.of(columnWithCard("Edited")));

        MergeOutcome outcome = SyncMerge.merge(base, local, remote);

        assertThat(outcome.merged().columns()).as("home column kept").isNotEmpty();
        assertThat(firstCard(outcome.merged()).title()).isEqualTo("Edited");
        assertThat(outcome.conflicts()).isNotEmpty();
    }

    private static ColumnSnapshot columnWithCard(String title) {
        CardSnapshot card = new CardSnapshot(new CardId("c0"), title, "Desc", BoardColor.BLUE,
                null, List.of(), T0, "", List.of(), null);
        return new ColumnSnapshot(new ColumnId("col0"), "Todo", "", BoardColor.BLUE,
                WipLimit.unlimited(), T0, false, null, List.of(card));
    }

    // ------------------------------------------------------------------
    // Structural validity
    // ------------------------------------------------------------------

    private static void assertValid(BoardMemento m, int caseIndex) {
        assertThat(m.columns()).as("case %d: columns", caseIndex).isNotNull();
        assertThat(m.processes()).as("case %d: processes", caseIndex).isNotNull();
        assertThat(m.links()).as("case %d: links", caseIndex).isNotNull();
        assertThat(m.timeline()).as("case %d: timeline", caseIndex).isNotNull();
        for (ColumnSnapshot c : m.columns()) {
            assertThat(c.id()).as("case %d: column id", caseIndex).isNotNull();
            assertThat(c.title()).as("case %d: column title", caseIndex).isNotNull();
            assertThat(c.color()).as("case %d: column color", caseIndex).isNotNull();
            assertThat(c.wipLimit()).as("case %d: column wip", caseIndex).isNotNull();
            assertThat(c.createdAt()).as("case %d: column createdAt", caseIndex).isNotNull();
            assertThat(c.cards()).as("case %d: column cards", caseIndex).isNotNull();
            for (CardSnapshot card : c.cards()) {
                assertThat(card.id()).as("case %d: card id", caseIndex).isNotNull();
                assertThat(card.title()).as("case %d: card title", caseIndex).isNotNull();
                assertThat(card.description()).as("case %d: card description", caseIndex).isNotNull();
                assertThat(card.color()).as("case %d: card color", caseIndex).isNotNull();
                assertThat(card.createdAt()).as("case %d: card createdAt", caseIndex).isNotNull();
                assertThat(card.notes()).as("case %d: card notes", caseIndex).isNotNull();
                assertThat(card.labels()).as("case %d: card labels", caseIndex).isNotNull();
                assertThat(card.checklist()).as("case %d: card checklist", caseIndex).isNotNull();
                for (ChecklistItem item : card.checklist()) {
                    assertThat(item.id()).as("case %d: item id", caseIndex).isNotNull();
                    assertThat(item.text()).as("case %d: item text", caseIndex).isNotNull();
                }
            }
        }
        for (ProcessSnapshot p : m.processes()) {
            assertThat(p.id()).as("case %d: process id", caseIndex).isNotNull();
            assertThat(p.name()).as("case %d: process name", caseIndex).isNotNull();
        }
    }

    // ------------------------------------------------------------------
    // Random-board generation (structure-preserving field edits)
    // ------------------------------------------------------------------

    private static BoardMemento randomBoard(Random r) {
        int[] seq = {0};
        List<ColumnSnapshot> columns = new ArrayList<>();
        int columnCount = 1 + r.nextInt(2);
        for (int c = 0; c < columnCount; c++) {
            columns.add(randomColumn(r, COL_POOL.get(c), seq));
        }
        List<ProcessSnapshot> processes = new ArrayList<>();
        for (int p = 0; p < r.nextInt(3); p++) {
            processes.add(new ProcessSnapshot(new ProcessId("p" + p), pick(r, TITLES)));
        }
        List<CardLink> links = new ArrayList<>();
        List<TimelineEntry> timeline = new ArrayList<>();
        if (r.nextBoolean()) {
            CardId from = new CardId("c0");
            CardId to = new CardId("c1");
            links.add(new CardLink(from, to));
        }
        if (r.nextInt(3) == 0) {
            timeline.add(TimelineEntry.restore(new CardId("c0"), new EntryId("e0"),
                    T0, T0.plusSeconds(60), "work"));
        }
        return new BoardMemento(columns, processes, links, timeline);
    }

    private static ColumnSnapshot randomColumn(Random r, String id, int[] seq) {
        List<CardSnapshot> cards = new ArrayList<>();
        int cardCount = r.nextInt(4);
        for (int i = 0; i < cardCount; i++) {
            cards.add(randomCard(r, "c" + seq[0]++));
        }
        return new ColumnSnapshot(new ColumnId(id), pick(r, TITLES), pick(r, TITLES),
                pick(r, COLORS), WipLimit.unlimited(), T0, false, null, cards);
    }

    private static CardSnapshot randomCard(Random r, String id) {
        List<ChecklistItem> checklist = new ArrayList<>();
        for (int i = 0; i < r.nextInt(3); i++) {
            checklist.add(new ChecklistItem("i" + i, pick(r, TITLES), r.nextBoolean()));
        }
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < r.nextInt(3); i++) {
            labels.add("label" + i);
        }
        return new CardSnapshot(new CardId(id), pick(r, TITLES), pick(r, TITLES), pick(r, COLORS),
                r.nextBoolean() ? D0 : null, labels, T0, pick(r, TITLES), checklist, null);
    }

    /** A board whose first column is guaranteed to hold at least one card. */
    private static BoardMemento randomBoardWithACard(Random r) {
        BoardMemento board = randomBoard(r);
        ColumnSnapshot first = board.columns().get(0);
        if (!first.cards().isEmpty()) {
            return board;
        }
        List<CardSnapshot> cards = new ArrayList<>(first.cards());
        cards.add(randomCard(r, uniqueId(usedIds(board))));
        List<ColumnSnapshot> columns = new ArrayList<>(board.columns());
        columns.set(0, withCards(first, cards));
        return new BoardMemento(columns, board.processes(), board.links(), board.timeline());
    }

    /** Edits random scalar fields (and labels/checklist) without changing structure. */
    private static BoardMemento editFields(Random r, BoardMemento board) {
        List<ColumnSnapshot> columns = new ArrayList<>();
        for (ColumnSnapshot column : board.columns()) {
            List<CardSnapshot> cards = new ArrayList<>();
            for (CardSnapshot card : column.cards()) {
                cards.add(r.nextBoolean() ? mutateCard(r, card) : card);
            }
            ColumnSnapshot c = r.nextInt(4) == 0
                    ? withTitle(column, pick(r, TITLES)) : column;
            columns.add(withCards(c, cards));
        }
        List<ProcessSnapshot> processes = new ArrayList<>();
        for (ProcessSnapshot p : board.processes()) {
            processes.add(r.nextBoolean() ? new ProcessSnapshot(p.id(), pick(r, TITLES)) : p);
        }
        return new BoardMemento(columns, processes, board.links(), board.timeline());
    }

    private static CardSnapshot mutateCard(Random r, CardSnapshot c) {
        String title = c.title();
        String description = c.description();
        BoardColor color = c.color();
        LocalDate dueDate = c.dueDate();
        String notes = c.notes();
        List<String> labels = new ArrayList<>(c.labels());
        List<ChecklistItem> checklist = new ArrayList<>(c.checklist());
        switch (r.nextInt(7)) {
            case 0 -> title = pick(r, TITLES);
            case 1 -> description = pick(r, TITLES);
            case 2 -> color = pick(r, COLORS);
            case 3 -> dueDate = dueDate == null ? D0 : dueDate.plusDays(1);
            case 4 -> notes = pick(r, TITLES);
            case 5 -> {
                if (!labels.contains("extra")) {
                    labels.add("extra");
                }
            }
            default -> checklist.add(new ChecklistItem("extra-i", pick(r, TITLES), r.nextBoolean()));
        }
        return new CardSnapshot(c.id(), title, description, color, dueDate, labels, c.createdAt(),
                notes, checklist, c.processId());
    }

    /** Adds/removes whole cards and columns on top of field edits. */
    private static BoardMemento mutateStructure(Random r, BoardMemento board) {
        BoardMemento edited = editFields(r, board);
        Set<String> used = usedIds(edited);
        List<ColumnSnapshot> columns = new ArrayList<>();
        for (ColumnSnapshot column : edited.columns()) {
            List<CardSnapshot> cards = new ArrayList<>(column.cards());
            if (!cards.isEmpty() && r.nextInt(3) == 0) {
                cards.remove(r.nextInt(cards.size()));
            }
            if (r.nextInt(3) == 0) {
                cards.add(randomCard(r, uniqueId(used)));
            }
            columns.add(withCards(column, cards));
        }
        if (columns.size() > 1 && r.nextInt(4) == 0) {
            columns.remove(r.nextInt(columns.size()));
        }
        return new BoardMemento(columns, edited.processes(), edited.links(), edited.timeline());
    }

    private static Set<String> usedIds(BoardMemento board) {
        Set<String> used = new HashSet<>();
        for (ColumnSnapshot column : board.columns()) {
            for (CardSnapshot card : column.cards()) {
                used.add(card.id().value());
            }
        }
        return used;
    }

    private static String uniqueId(Set<String> used) {
        int i = used.size();
        while (used.contains("x" + i)) {
            i++;
        }
        used.add("x" + i);
        return "x" + i;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static BoardMemento withCardTitle(BoardMemento board, String title) {
        return withFirstCard(board, card -> new CardSnapshot(card.id(), title, card.description(),
                card.color(), card.dueDate(), card.labels(), card.createdAt(), card.notes(),
                card.checklist(), card.processId()));
    }

    private static BoardMemento withCardDescription(BoardMemento board, String description) {
        return withFirstCard(board, card -> new CardSnapshot(card.id(), card.title(), description,
                card.color(), card.dueDate(), card.labels(), card.createdAt(), card.notes(),
                card.checklist(), card.processId()));
    }

    private static BoardMemento withFirstCard(BoardMemento board,
                                              java.util.function.UnaryOperator<CardSnapshot> op) {
        List<ColumnSnapshot> columns = new ArrayList<>();
        boolean replaced = false;
        for (ColumnSnapshot column : board.columns()) {
            List<CardSnapshot> cards = new ArrayList<>();
            for (CardSnapshot card : column.cards()) {
                if (!replaced) {
                    cards.add(op.apply(card));
                    replaced = true;
                } else {
                    cards.add(card);
                }
            }
            columns.add(withCards(column, cards));
        }
        return new BoardMemento(columns, board.processes(), board.links(), board.timeline());
    }

    private static CardSnapshot firstCard(BoardMemento board) {
        return board.columns().get(0).cards().get(0);
    }

    private static ColumnSnapshot withCards(ColumnSnapshot c, List<CardSnapshot> cards) {
        return new ColumnSnapshot(c.id(), c.title(), c.description(), c.color(), c.wipLimit(),
                c.createdAt(), c.done(), c.backgroundColor(), cards);
    }

    private static ColumnSnapshot withTitle(ColumnSnapshot c, String title) {
        return new ColumnSnapshot(c.id(), title, c.description(), c.color(), c.wipLimit(),
                c.createdAt(), c.done(), c.backgroundColor(), c.cards());
    }

    private static <T> T pick(Random r, List<T> values) {
        return values.get(r.nextInt(values.size()));
    }

    /** Stable rendering that includes every field, used instead of equals. */
    private static String render(BoardMemento m) {
        StringBuilder sb = new StringBuilder();
        for (ColumnSnapshot c : m.columns()) {
            sb.append("COL ").append(c.id().value()).append('|').append(c.title()).append('|')
                    .append(c.description()).append('|').append(stored(c.color())).append('|')
                    .append(c.wipLimit().isUnlimited() ? "U" : c.wipLimit().value()).append('|')
                    .append(c.createdAt()).append('|').append(c.done()).append('|')
                    .append(c.backgroundColor()).append('\n');
            for (CardSnapshot card : c.cards()) {
                sb.append("  CARD ").append(card.id().value()).append('|').append(card.title())
                        .append('|').append(card.description()).append('|').append(stored(card.color()))
                        .append('|').append(card.dueDate()).append('|').append(card.createdAt())
                        .append('|').append(card.notes()).append('|')
                        .append(card.processId() == null ? "" : card.processId().value()).append('|')
                        .append(String.join(",", card.labels())).append('|');
                for (ChecklistItem item : card.checklist()) {
                    sb.append(item.id()).append(':').append(item.text()).append(':')
                            .append(item.done()).append(';');
                }
                sb.append('\n');
            }
        }
        for (ProcessSnapshot p : m.processes()) {
            sb.append("PROC ").append(p.id().value()).append('|').append(p.name()).append('\n');
        }
        for (CardLink l : m.links()) {
            sb.append("LINK ").append(l.from().value()).append("->").append(l.to().value()).append('\n');
        }
        for (TimelineEntry e : m.timeline()) {
            sb.append("TIME ").append(e.id().value()).append('|').append(e.start()).append('|')
                    .append(e.end()).append('|').append(e.comment()).append('\n');
        }
        return sb.toString();
    }

    private static String stored(BoardColor color) {
        return color == null ? "" : color.stored();
    }
}
