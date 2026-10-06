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

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Three-way merge engine for the sync conflict policy (design §4): takes the
 * common ancestor (the last snapshot both devices agreed on), the local
 * snapshot and the remote snapshot, and produces a merged snapshot that keeps
 * every non-overlapping change.
 *
 * <p>Rules, applied per field rather than per record so editing the
 * description on one device and the color on the other never loses either:</p>
 * <ul>
 *   <li><b>Scalars</b> (title, description, color, date, notes, process,
 *       column fields): last-writer-wins by field. When only one side moved
 *       from the base, that side wins. When both moved to different values
 *       the engine cannot know the real clock (the protocol carries no
 *       per-field timestamps), so it breaks the tie deterministically by the
 *       greater canonical rendering — identical on every device — and records
 *       a {@link SyncConflict} so the losing value can be preserved (§4.1).</li>
 *   <li><b>Collections</b> (labels, checklist items): union by identity; an
 *       item present in the base but missing on one side was deleted there
 *       and the deletion propagates, unless the other side edited it.</li>
 *   <li><b>Timeline and links</b>: append-only union, with base-aware
 *       deletion detection (no tombstones needed while the base is stored).</li>
 *   <li><b>Deletions vs edits</b>: an edit beats a delete — the object
 *       survives and the clash is recorded as a conflict rather than silently
 *       resurrected or discarded.</li>
 * </ul>
 *
 * <p>Column and card order stays positional; local order is kept and
 * remote-only additions are appended. The design defers explicit ordering
 * keys (fractional index) to a later milestone.</p>
 */
public final class SyncMerge {

    private SyncMerge() {
    }

    /** Merges the three snapshots; never mutates its inputs. */
    public static MergeOutcome merge(BoardMemento base, BoardMemento local, BoardMemento remote) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(local, "local");
        Objects.requireNonNull(remote, "remote");

        List<SyncConflict> conflicts = new ArrayList<>();

        Map<ColumnId, ColumnSnapshot> baseCols = indexColumns(base.columns());
        Map<ColumnId, ColumnSnapshot> localCols = indexColumns(local.columns());
        Map<ColumnId, ColumnSnapshot> remoteCols = indexColumns(remote.columns());

        Map<ProcessId, ProcessSnapshot> baseProc = indexProcesses(base.processes());
        Map<ProcessId, ProcessSnapshot> localProc = indexProcesses(local.processes());
        Map<ProcessId, ProcessSnapshot> remoteProc = indexProcesses(remote.processes());

        // ---- columns: which survive and their merged scalars ----
        List<ColumnId> columnOrder = ordered(
                localCols.keySet(), remoteCols.keySet());
        Map<ColumnId, ColumnSnapshot> mergedColumns = new LinkedHashMap<>();
        for (ColumnId id : columnOrder) {
            ColumnSnapshot b = baseCols.get(id);
            ColumnSnapshot l = localCols.get(id);
            ColumnSnapshot r = remoteCols.get(id);
            Decision decision = decide(b != null, l != null, r != null,
                    () -> l != null && b != null && !columnKey(l).equals(columnKey(b)),
                    () -> r != null && b != null && !columnKey(r).equals(columnKey(b)));
            if (!decision.keep()) {
                continue;
            }
            if (decision.conflict()) {
                conflicts.add(new SyncConflict("column", id.value(), "deleted",
                        "kept", "deleted"));
            }
            mergedColumns.put(id, mergeColumn(b, l, r, conflicts));
        }

        // ---- cards: merged value plus target column ----
        Map<CardId, ColumnId> localCardColumn = cardColumns(local.columns());
        Map<CardId, ColumnId> remoteCardColumn = cardColumns(remote.columns());
        Map<CardId, CardSnapshot> baseCards = indexCards(base.columns());
        Map<CardId, CardSnapshot> localCards = indexCards(local.columns());
        Map<CardId, CardSnapshot> remoteCards = indexCards(remote.columns());

        List<CardId> cardOrder = ordered(localCards.keySet(), remoteCards.keySet());
        List<ColumnId> keptColumnOrder = new ArrayList<>(mergedColumns.keySet());
        List<ColumnSnapshot> assembled = new ArrayList<>();
        Map<ColumnId, List<CardSnapshot>> cardsByColumn = new LinkedHashMap<>();
        for (ColumnId id : keptColumnOrder) {
            cardsByColumn.put(id, new ArrayList<>());
        }
        for (CardId id : cardOrder) {
            CardSnapshot b = baseCards.get(id);
            CardSnapshot l = localCards.get(id);
            CardSnapshot r = remoteCards.get(id);
            Decision decision = decide(b != null, l != null, r != null,
                    () -> l != null && b != null && !cardKey(l).equals(cardKey(b)),
                    () -> r != null && b != null && !cardKey(r).equals(cardKey(b)));
            if (!decision.keep()) {
                continue;
            }
            if (decision.conflict() && decision.missing().equals("remote")) {
                conflicts.add(new SyncConflict("card", id.value(), "deleted",
                        "kept", "deleted"));
            } else if (decision.conflict()) {
                conflicts.add(new SyncConflict("card", id.value(), "deleted",
                        "deleted", "kept"));
            }
            CardSnapshot merged = mergeCard(b, l, r, conflicts);
            ColumnId target = targetColumn(localCardColumn.get(id), remoteCardColumn.get(id),
                    keptColumnOrder);
            cardsByColumn.get(target).add(merged);
        }
        for (ColumnId id : keptColumnOrder) {
            ColumnSnapshot column = mergedColumns.get(id);
            assembled.add(new ColumnSnapshot(column.id(), column.title(), column.description(),
                    column.color(), column.wipLimit(), column.createdAt(), column.done(),
                    column.backgroundColor(), cardsByColumn.get(id)));
        }

        // ---- processes ----
        List<ProcessId> processOrder = ordered(localProc.keySet(), remoteProc.keySet());
        List<ProcessSnapshot> mergedProcesses = new ArrayList<>();
        for (ProcessId id : processOrder) {
            ProcessSnapshot b = baseProc.get(id);
            ProcessSnapshot l = localProc.get(id);
            ProcessSnapshot r = remoteProc.get(id);
            // An edit beats a delete on either side; both absent means gone.
            if (l == null && r == null) {
                continue;
            }
            String name = lww("name", b == null ? null : b.name(),
                    l == null ? null : l.name(), r == null ? null : r.name(),
                    "process", id.value(), conflicts);
            if (name == null) {
                name = l != null ? l.name() : r.name();
            }
            mergedProcesses.add(new ProcessSnapshot(id, name));
        }

        // ---- links and timeline: append-only union with base-aware deletion ----
        List<CardLink> mergedLinks = mergeLinks(base.links(), local.links(), remote.links());
        List<TimelineEntry> mergedTimeline = mergeTimeline(base.timeline(), local.timeline(),
                remote.timeline());

        BoardMemento merged = new BoardMemento(assembled, mergedProcesses, mergedLinks, mergedTimeline);
        return new MergeOutcome(merged, conflicts);
    }

    // ------------------------------------------------------------------
    // Presence decision (add / delete / edit)
    // ------------------------------------------------------------------

    /**
     * @param changedLocal  the local side edited the object relative to base
     * @param changedRemote the remote side edited the object relative to base
     */
    private record Decision(boolean keep, boolean conflict, String missing) {
    }

    private static Decision decide(boolean inBase, boolean inLocal, boolean inRemote,
                                   java.util.function.BooleanSupplier changedLocal,
                                   java.util.function.BooleanSupplier changedRemote) {
        if (inLocal && inRemote) {
            return new Decision(true, false, "");
        }
        if (inLocal) { // remote absent
            if (!inBase) {
                return new Decision(true, false, ""); // added locally
            }
            return changedLocal.getAsBoolean()
                    ? new Decision(true, true, "remote")   // local edit beats remote delete
                    : new Decision(false, false, "");       // remote deleted it
        }
        if (inRemote) { // local absent
            if (!inBase) {
                return new Decision(true, false, ""); // added remotely
            }
            return changedRemote.getAsBoolean()
                    ? new Decision(true, true, "local")    // remote edit beats local delete
                    : new Decision(false, false, "");       // local deleted it
        }
        return new Decision(false, false, "");
    }

    // ------------------------------------------------------------------
    // Column / card / process merges
    // ------------------------------------------------------------------

    private static ColumnSnapshot mergeColumn(ColumnSnapshot b, ColumnSnapshot rawL, ColumnSnapshot rawR,
                                              List<SyncConflict> conflicts) {
        // A side absent here was deleted but kept because the other side
        // edited it: it contributes the base values, so only the surviving
        // side's real changes register (and never a null field).
        ColumnSnapshot l = rawL != null ? rawL : (b != null ? b : rawR);
        ColumnSnapshot r = rawR != null ? rawR : (b != null ? b : rawL);
        String id = l.id().value();
        ColumnId idRef = l.id();
        Instant createdAt = l.createdAt();
        return new ColumnSnapshot(
                idRef,
                lww("title", str(b, ColumnSnapshot::title), str(l, ColumnSnapshot::title),
                        str(r, ColumnSnapshot::title), "column", id, conflicts),
                lww("description", str(b, ColumnSnapshot::description),
                        str(l, ColumnSnapshot::description), str(r, ColumnSnapshot::description),
                        "column", id, conflicts),
                lww("color", col(b, ColumnSnapshot::color), col(l, ColumnSnapshot::color),
                        col(r, ColumnSnapshot::color), "column", id, conflicts),
                lww("wipLimit", wip(b, ColumnSnapshot::wipLimit), wip(l, ColumnSnapshot::wipLimit),
                        wip(r, ColumnSnapshot::wipLimit), "column", id, conflicts),
                createdAt,
                Boolean.TRUE.equals(lww("done", bool(b, ColumnSnapshot::done),
                        bool(l, ColumnSnapshot::done), bool(r, ColumnSnapshot::done),
                        "column", id, conflicts)),
                lww("backgroundColor", str(b, ColumnSnapshot::backgroundColor),
                        str(l, ColumnSnapshot::backgroundColor), str(r, ColumnSnapshot::backgroundColor),
                        "column", id, conflicts),
                List.of());
    }

    private static CardSnapshot mergeCard(CardSnapshot b, CardSnapshot rawL, CardSnapshot rawR,
                                          List<SyncConflict> conflicts) {
        // A missing side was deleted but kept because the other side edited
        // it; it contributes the base so no field comes back null.
        CardSnapshot l = rawL != null ? rawL : (b != null ? b : rawR);
        CardSnapshot r = rawR != null ? rawR : (b != null ? b : rawL);
        CardId id = l.id();
        String key = id.value();
        Instant createdAt = l.createdAt();
        List<String> labels = mergeLabels(
                b == null ? List.of() : b.labels(),
                l.labels(),
                r.labels());
        List<ChecklistItem> checklist = mergeChecklist(
                b == null ? List.of() : b.checklist(),
                l.checklist(),
                r.checklist(),
                key, conflicts);
        return new CardSnapshot(
                id,
                lww("title", str(b, CardSnapshot::title), str(l, CardSnapshot::title),
                        str(r, CardSnapshot::title), "card", key, conflicts),
                lww("description", str(b, CardSnapshot::description),
                        str(l, CardSnapshot::description), str(r, CardSnapshot::description),
                        "card", key, conflicts),
                lww("color", col(b, CardSnapshot::color), col(l, CardSnapshot::color),
                        col(r, CardSnapshot::color), "card", key, conflicts),
                lww("dueDate", date(b, CardSnapshot::dueDate), date(l, CardSnapshot::dueDate),
                        date(r, CardSnapshot::dueDate), "card", key, conflicts),
                labels,
                createdAt,
                lww("notes", str(b, CardSnapshot::notes), str(l, CardSnapshot::notes),
                        str(r, CardSnapshot::notes), "card", key, conflicts),
                checklist,
                lww("processId", proc(b, CardSnapshot::processId), proc(l, CardSnapshot::processId),
                        proc(r, CardSnapshot::processId), "card", key, conflicts));
    }

    private static List<String> mergeLabels(List<String> base, List<String> local, List<String> remote) {
        Set<String> b = new HashSet<>(base);
        Set<String> l = new HashSet<>(local);
        Set<String> r = new HashSet<>(remote);
        LinkedHashSet<String> order = new LinkedHashSet<>(local);
        order.addAll(remote);
        List<String> out = new ArrayList<>();
        for (String label : order) {
            boolean inBase = b.contains(label);
            boolean keep = inBase ? (l.contains(label) && r.contains(label))
                    : (l.contains(label) || r.contains(label));
            if (keep) {
                out.add(label);
            }
        }
        return out;
    }

    private static List<ChecklistItem> mergeChecklist(List<ChecklistItem> base, List<ChecklistItem> local,
                                                      List<ChecklistItem> remote, String cardId,
                                                      List<SyncConflict> conflicts) {
        Map<String, ChecklistItem> b = indexItems(base);
        Map<String, ChecklistItem> l = indexItems(local);
        Map<String, ChecklistItem> r = indexItems(remote);
        LinkedHashSet<String> order = new LinkedHashSet<>(l.keySet());
        order.addAll(r.keySet());
        List<ChecklistItem> out = new ArrayList<>();
        for (String id : order) {
            ChecklistItem bi = b.get(id);
            ChecklistItem li = l.get(id);
            ChecklistItem ri = r.get(id);
            ChecklistItem chosen = null;
            if (bi != null) {
                if (li != null && ri != null) {
                    chosen = mergeItem(bi, li, ri, cardId, conflicts);
                }
                // one side deleted the item → deletion propagates
            } else if (li != null && ri != null) {
                chosen = mergeItem(null, li, ri, cardId, conflicts);
            } else {
                chosen = li != null ? li : ri;
            }
            if (chosen != null) {
                out.add(chosen);
            }
        }
        return out;
    }

    private static ChecklistItem mergeItem(ChecklistItem b, ChecklistItem l, ChecklistItem r,
                                           String cardId, List<SyncConflict> conflicts) {
        String text = lww("checklist[" + r.id() + "].text",
                b == null ? null : b.text(), l.text(), r.text(),
                "card", cardId, conflicts);
        Boolean done = lww("checklist[" + r.id() + "].done",
                b == null ? null : b.done(), l.done(), r.done(),
                "card", cardId, conflicts);
        return new ChecklistItem(r.id(), text, Boolean.TRUE.equals(done));
    }

    private static List<CardLink> mergeLinks(List<CardLink> base, List<CardLink> local,
                                             List<CardLink> remote) {
        Set<CardLink> b = new HashSet<>(base);
        Set<CardLink> l = new HashSet<>(local);
        Set<CardLink> r = new HashSet<>(remote);
        LinkedHashSet<CardLink> order = new LinkedHashSet<>(local);
        order.addAll(remote);
        List<CardLink> out = new ArrayList<>();
        for (CardLink link : order) {
            boolean inBase = b.contains(link);
            boolean keep = inBase ? (l.contains(link) && r.contains(link))
                    : (l.contains(link) || r.contains(link));
            if (keep) {
                out.add(link);
            }
        }
        return out;
    }

    private static List<TimelineEntry> mergeTimeline(List<TimelineEntry> base, List<TimelineEntry> local,
                                                     List<TimelineEntry> remote) {
        Set<EntryId> b = new HashSet<>(idsOf(base));
        Set<EntryId> l = new HashSet<>(idsOf(local));
        Set<EntryId> r = new HashSet<>(idsOf(remote));
        Map<EntryId, TimelineEntry> localById = indexTimeline(local);
        Map<EntryId, TimelineEntry> remoteById = indexTimeline(remote);
        LinkedHashSet<EntryId> order = new LinkedHashSet<>(l);
        order.addAll(r);
        List<TimelineEntry> out = new ArrayList<>();
        for (EntryId id : order) {
            boolean inBase = b.contains(id);
            boolean keep = inBase ? (l.contains(id) && r.contains(id))
                    : (l.contains(id) || r.contains(id));
            if (keep) {
                TimelineEntry source = localById.getOrDefault(id, remoteById.get(id));
                out.add(source.copy());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Field-level last-writer-wins
    // ------------------------------------------------------------------

    /**
     * Resolves one scalar field. Only one side moved from base → that side
     * wins. Both moved to different values → deterministic tie-break by the
     * greater canonical rendering (same on every device) and a recorded
     * conflict, so the loser is not silently lost.
     */
    private static <T> T lww(String field, T base, T local, T remote,
                             String entity, String entityId, List<SyncConflict> conflicts) {
        String b = canonical(base);
        String l = canonical(local);
        String r = canonical(remote);
        if (l.equals(b)) {
            return remote != null ? remote : local;
        }
        if (r.equals(b)) {
            return local;
        }
        if (l.equals(r)) {
            return local != null ? local : remote;
        }
        conflicts.add(new SyncConflict(entity, entityId, field, l, r));
        return l.compareTo(r) >= 0 ? local : remote;
    }

    /** Stable, machine-independent rendering used for equality and tie-breaks. */
    static String canonical(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BoardColor color) {
            return color.name() + "|" + color.hex();
        }
        if (value instanceof WipLimit wip) {
            return wip.isUnlimited() ? "unlimited" : "wip:" + wip.asOptional().orElse(null);
        }
        if (value instanceof CardId id) {
            return id.value();
        }
        if (value instanceof ColumnId id) {
            return id.value();
        }
        if (value instanceof ProcessId id) {
            return id.value();
        }
        if (value instanceof EntryId id) {
            return id.value();
        }
        if (value instanceof Instant instant) {
            return String.valueOf(instant.toEpochMilli());
        }
        return String.valueOf(value);
    }

    // ------------------------------------------------------------------
    // Whole-object canonical keys (to detect "changed from base")
    // ------------------------------------------------------------------

    private static String columnKey(ColumnSnapshot c) {
        return String.join("\u0001", canonical(c.title()), canonical(c.description()),
                canonical(c.color()), canonical(c.wipLimit()), canonical(c.createdAt()),
                canonical(c.done()), canonical(c.backgroundColor()));
    }

    private static String cardKey(CardSnapshot c) {
        StringBuilder checklist = new StringBuilder();
        for (ChecklistItem item : c.checklist()) {
            checklist.append(item.id()).append('=').append(canonical(item.text()))
                    .append('=').append(item.done()).append(';');
        }
        return String.join("\u0001", canonical(c.title()), canonical(c.description()),
                canonical(c.color()), canonical(c.dueDate()), canonical(c.createdAt()),
                canonical(c.notes()), canonical(c.processId()),
                String.join(",", c.labels()), checklist.toString());
    }

    // ------------------------------------------------------------------
    // Index / ordering helpers
    // ------------------------------------------------------------------

    private static Map<ColumnId, ColumnSnapshot> indexColumns(List<ColumnSnapshot> columns) {
        Map<ColumnId, ColumnSnapshot> map = new LinkedHashMap<>();
        for (ColumnSnapshot column : columns) {
            map.put(column.id(), column);
        }
        return map;
    }

    private static Map<CardId, CardSnapshot> indexCards(List<ColumnSnapshot> columns) {
        Map<CardId, CardSnapshot> map = new LinkedHashMap<>();
        for (ColumnSnapshot column : columns) {
            for (CardSnapshot card : column.cards()) {
                map.put(card.id(), card);
            }
        }
        return map;
    }

    private static Map<CardId, ColumnId> cardColumns(List<ColumnSnapshot> columns) {
        Map<CardId, ColumnId> map = new HashMap<>();
        for (ColumnSnapshot column : columns) {
            for (CardSnapshot card : column.cards()) {
                map.put(card.id(), column.id());
            }
        }
        return map;
    }

    private static Map<ProcessId, ProcessSnapshot> indexProcesses(List<ProcessSnapshot> processes) {
        Map<ProcessId, ProcessSnapshot> map = new LinkedHashMap<>();
        for (ProcessSnapshot process : processes) {
            map.put(process.id(), process);
        }
        return map;
    }

    private static Map<String, ChecklistItem> indexItems(List<ChecklistItem> items) {
        Map<String, ChecklistItem> map = new LinkedHashMap<>();
        for (ChecklistItem item : items) {
            map.put(item.id(), item);
        }
        return map;
    }

    private static Map<EntryId, TimelineEntry> indexTimeline(List<TimelineEntry> entries) {
        Map<EntryId, TimelineEntry> map = new LinkedHashMap<>();
        for (TimelineEntry entry : entries) {
            map.put(entry.id(), entry);
        }
        return map;
    }

    private static List<EntryId> idsOf(List<TimelineEntry> entries) {
        List<EntryId> ids = new ArrayList<>();
        for (TimelineEntry entry : entries) {
            ids.add(entry.id());
        }
        return ids;
    }

    private static <T> List<T> ordered(Set<T> localFirst, Set<T> remoteOnly) {
        LinkedHashSet<T> order = new LinkedHashSet<>(localFirst);
        order.addAll(remoteOnly);
        return new ArrayList<>(order);
    }

    private static ColumnId targetColumn(ColumnId local, ColumnId remote, List<ColumnId> kept) {
        if (local != null && kept.contains(local)) {
            return local;
        }
        if (remote != null && kept.contains(remote)) {
            return remote;
        }
        return kept.isEmpty() ? null : kept.get(0);
    }

    // ------------------------------------------------------------------
    // Null-safe accessors on nullable snapshots
    // ------------------------------------------------------------------

    private static String str(ColumnSnapshot c, Function<ColumnSnapshot, String> getter) {
        return c == null ? null : getter.apply(c);
    }

    private static String str(CardSnapshot c, Function<CardSnapshot, String> getter) {
        return c == null ? null : getter.apply(c);
    }

    private static BoardColor col(ColumnSnapshot c, Function<ColumnSnapshot, BoardColor> getter) {
        return c == null ? null : getter.apply(c);
    }

    private static BoardColor col(CardSnapshot c, Function<CardSnapshot, BoardColor> getter) {
        return c == null ? null : getter.apply(c);
    }

    private static WipLimit wip(ColumnSnapshot c, Function<ColumnSnapshot, WipLimit> getter) {
        return c == null ? null : getter.apply(c);
    }

    private static Boolean bool(ColumnSnapshot c, Function<ColumnSnapshot, Boolean> getter) {
        return c == null ? null : getter.apply(c);
    }

    private static LocalDate date(CardSnapshot c, Function<CardSnapshot, LocalDate> getter) {
        return c == null ? null : getter.apply(c);
    }

    private static ProcessId proc(CardSnapshot c, Function<CardSnapshot, ProcessId> getter) {
        return c == null ? null : getter.apply(c);
    }
}
