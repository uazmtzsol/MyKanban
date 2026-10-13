package com.personalkanban.domain.board;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A work item. Deliberately immutable: every mutation goes through the
 * aggregate root ({@link Board}), which owns the invariants. Cards carry an
 * optional due date, a small set of free-form labels, plain-text notes
 * (session 4.4: distinct from the markdown description), a flat checklist
 * of verifiable steps (session 4.5, user decision: one level, items
 * convertible into cards later), and an optional process assignment
 * (session 4.6).
 */
public final class Card {

    private static final int MAX_LABELS = 8;
    /** Upper bound of a label name; public because the global catalog validates against it. */
    public static final int MAX_LABEL_LENGTH = 40;
    private static final int MAX_CHECKLIST_ITEMS = 50;

    /**
     * System labels with one-click affordances (star = important, bang =
     * urgent). Deliberately plain strings — they are ordinary labels in the
     * set (they join any bulk operation, filter, and future chip coloring);
     * only the UI gives them dedicated buttons. Case-insensitive matching
     * keeps "Urgente" and "urgente" the same label.
     */
    public static final String LABEL_IMPORTANT = "Importante";
    public static final String LABEL_URGENT = "Urgente";

    /**
     * Session 10 (board cleanup): an archived card is hidden from the board
     * until the user ticks "show archived". Implemented as a plain label so
     * archiving needs no schema migration and rides every existing feature
     * (undo, filters, export, sync) for free — the UI is what hides it.
     */
    public static final String LABEL_ARCHIVED = "Archivada";

    /** Predefined system labels used by the app (global, managed, not removable). */
    public static final Set<String> SYSTEM_LABELS = Set.of(
            LABEL_IMPORTANT, LABEL_URGENT, LABEL_ARCHIVED);

    private final CardId id;
    private final ColumnId columnId;
    private String title;
    private String description;
    private String notes = "";
    private BoardColor color;
    private LocalDate dueDate;
    private List<String> labels = List.of();
    private final List<ChecklistItem> checklist = new ArrayList<>();
    private ProcessId processId;
    private final Timeline timeline;
    private final Instant createdAt;

    Card(ColumnId columnId, String title, String description, BoardColor color) {
        this(Ids.newCardId(), columnId, title, description, color, null, List.of(), Instant.now());
    }

    Card(ColumnId columnId, String title, String description, BoardColor color,
         LocalDate dueDate, List<String> labels) {
        this(Ids.newCardId(), columnId, title, description, color, dueDate, labels, Instant.now());
    }

    Card(CardId id, ColumnId columnId, String title, String description, BoardColor color,
         LocalDate dueDate, List<String> labels, Instant createdAt) {
        if (id == null || columnId == null || color == null || createdAt == null) {
            throw new IllegalArgumentException("Card fields must not be null");
        }
        this.id = id;
        this.columnId = columnId;
        this.timeline = new Timeline(id);
        setTitle(title);
        setDescription(description);
        this.color = color;
        this.dueDate = dueDate;
        setLabels(labels);
        this.createdAt = createdAt;
    }

    static Card restore(CardId id, ColumnId columnId, String title, String description,
                        BoardColor color, LocalDate dueDate, List<String> labels, Instant createdAt) {
        return new Card(id, columnId, title, description, color, dueDate, labels, createdAt);
    }

    void rename(String newTitle) {
        setTitle(newTitle);
    }

    void describe(String newDescription) {
        setDescription(newDescription);
    }

    void annotate(String newNotes) {
        this.notes = newNotes == null ? "" : newNotes.strip();
    }

    void recolor(BoardColor newColor) {
        this.color = Objects.requireNonNull(newColor, "color");
    }

    void schedule(LocalDate newDueDate) {
        this.dueDate = newDueDate;
    }

    void tag(List<String> newLabels) {
        setLabels(newLabels);
    }

    /** Assigns this card to a process (or clears it with null); aggregate-checked. */
    void assignTo(ProcessId newProcessId) {
        this.processId = newProcessId;
    }

    // ------------------------------------------------------------------
    // Checklist (flat, one level; items may become cards later)
    // ------------------------------------------------------------------

    ChecklistItem addChecklistItem(String text) {
        return addChecklistItem(text, null);
    }

    /**
     * Adds a checklist item, optionally as a subtask of {@code parentId}
     * (T16 · B8, one level only: a subtask of a subtask is rejected).
     */
    ChecklistItem addChecklistItem(String text, String parentId) {
        if (checklist.size() >= MAX_CHECKLIST_ITEMS) {
            throw new IllegalArgumentException(
                    "A card may have at most " + MAX_CHECKLIST_ITEMS + " checklist items");
        }
        ChecklistItem item;
        if (parentId == null || parentId.isBlank()) {
            item = ChecklistItem.newItem(text);
        } else {
            ChecklistItem parent = itemOrThrow(parentId);
            if (parent.isSubtask()) {
                throw new IllegalArgumentException(
                        "A subtask cannot have subtasks: " + parentId);
            }
            item = ChecklistItem.newSubtask(text, parentId);
        }
        checklist.add(item);
        return item;
    }

    /**
     * Re-adopts an existing item (persistence/undo restore): no cap check by
     * design, so a snapshot is always loadable even if the cap changed.
     */
    void adoptChecklistItem(ChecklistItem item) {
        Objects.requireNonNull(item, "item");
        checklist.add(item);
    }

    void renameChecklistItem(String itemId, String newText) {
        replaceItem(itemId, item -> item.withText(newText));
    }

    void setChecklistItemDone(String itemId, boolean done) {
        replaceItem(itemId, item -> item.withDone(done));
    }

    private void replaceItem(String itemId, java.util.function.UnaryOperator<ChecklistItem> change) {
        for (int i = 0; i < checklist.size(); i++) {
            if (checklist.get(i).id().equals(itemId)) {
                checklist.set(i, change.apply(checklist.get(i)));
                return;
            }
        }
        throw new IllegalArgumentException("Unknown checklist item: " + itemId);
    }

    /**
     * Removes one checklist item; removing a parent also removes its subtasks
     * (they cannot outlive their parent in a one-level hierarchy).
     */
    void removeChecklistItem(String itemId) {
        itemOrThrow(itemId); // unknown ids must still fail
        checklist.removeIf(item -> item.id().equals(itemId)
                || itemId.equals(item.parentId()));
    }

    /** Direct children of one item, in list order. */
    public List<ChecklistItem> checklistChildrenOf(String parentId) {
        return checklist.stream()
                .filter(item -> parentId.equals(item.parentId()))
                .toList();
    }

    /** How many subtasks hang under one item. */
    public int subtaskCount(String parentId) {
        return checklistChildrenOf(parentId).size();
    }

    /**
     * Moves one item under {@code parentId} (null = back to top level), used
     * by the indent/outdent actions. One level is enforced in both directions:
     * the target cannot be a subtask, and an item with subtasks cannot become
     * one.
     */
    void setChecklistItemParent(String itemId, String parentId) {
        ChecklistItem item = itemOrThrow(itemId);
        if (parentId == null || parentId.isBlank()) {
            replaceItem(itemId, existing -> existing.withParent(null));
            return;
        }
        if (parentId.equals(itemId)) {
            throw new IllegalArgumentException("A checklist item cannot be its own parent");
        }
        ChecklistItem parent = itemOrThrow(parentId);
        if (parent.isSubtask()) {
            throw new IllegalArgumentException("A subtask cannot have subtasks: " + parentId);
        }
        if (!checklistChildrenOf(itemId).isEmpty()) {
            throw new IllegalArgumentException(
                    "An item with subtasks cannot become a subtask: " + itemId);
        }
        replaceItem(itemId, existing -> existing.withParent(parentId));
    }

    /** Total subtasks of the card (any parent). */
    public long subtaskTotalCount() {
        return checklist.stream().filter(ChecklistItem::isSubtask).count();
    }

    /** Lookup by id (used by convert-to-card); unknown ids throw. */
    public ChecklistItem checklistItem(String itemId) {
        return itemOrThrow(itemId);
    }

    /** Unmodifiable snapshot of the checklist in list order. */
    public List<ChecklistItem> checklist() {
        return List.copyOf(checklist);
    }

    private ChecklistItem itemOrThrow(String itemId) {
        return checklist.stream()
                .filter(item -> item.id().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown checklist item: " + itemId));
    }

    /** Replaces the whole checklist (dialog advanced form); aggregate-checked. */
    void replaceChecklist(List<ChecklistItem> items) {
        checklist.clear();
        checklist.addAll(items);
    }

    /** Normalizes labels: stripped, deduplicated, bounded. */
    private void setLabels(List<String> labels) {
        if (labels == null) {
            this.labels = List.of();
            return;
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String label : labels) {
            if (label == null) {
                continue;
            }
            String stripped = label.strip();
            if (stripped.isEmpty()) {
                continue;
            }
            if (stripped.length() > MAX_LABEL_LENGTH) {
                throw new IllegalArgumentException(
                        "Label longer than " + MAX_LABEL_LENGTH + " chars: " + stripped);
            }
            if (unique.size() >= MAX_LABELS) {
                throw new IllegalArgumentException("A card may have at most " + MAX_LABELS + " labels");
            }
            unique.add(stripped);
        }
        this.labels = List.copyOf(unique);
    }

    private void setTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Card title must not be blank");
        }
        this.title = title.strip();
    }

    private void setDescription(String description) {
        this.description = description == null ? "" : description.strip();
    }

    public CardId id() {
        return id;
    }

    public ColumnId columnId() {
        return columnId;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public String notes() {
        return notes;
    }

    public BoardColor color() {
        return color;
    }

    public LocalDate dueDate() {
        return dueDate;
    }

    public List<String> labels() {
        return labels;
    }

    /** Process this card belongs to, or null when unassigned. */
    public ProcessId processId() {
        return processId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean isOverdueOn(LocalDate today) {
        return dueDate != null && dueDate.isBefore(today);
    }

    /** True when this card carries the given label, ignoring case. */
    public boolean hasLabelIgnoreCase(String label) {
        return labels.stream().anyMatch(existing -> existing.equalsIgnoreCase(label));
    }

    /** True when this card carries a label whose identity matches {@code otherName} (case-insensitive). */
    public boolean hasLabelMatchingIdentity(String otherName) {
        if (otherName == null) {
            return false;
        }
        return labels.stream().anyMatch(existing -> existing.equalsIgnoreCase(otherName.strip()));
    }

    // Quick-flag priority helpers (sorting + card-front highlighting).
    public boolean isImportantCard() {
        return hasLabelIgnoreCase(LABEL_IMPORTANT);
    }

    public boolean isUrgentCard() {
        return hasLabelIgnoreCase(LABEL_URGENT);
    }

    public boolean isUrgentAndImportantCard() {
        return isImportantCard() && isUrgentCard();
    }

    public boolean isUrgentOnlyCard() {
        return isUrgentCard() && !isImportantCard();
    }

    public boolean isImportantOnlyCard() {
        return isImportantCard() && !isUrgentCard();
    }

    /** Convenience for the UI: done / total checklist counters. */
    public long doneChecklistCount() {
        return checklist.stream().filter(ChecklistItem::done).count();
    }

    public int checklistCount() {
        return checklist.size();
    }

    // ------------------------------------------------------------------
    // Timeline (time tracking)
    // ------------------------------------------------------------------

    /** Mutable timeline of time-tracking entries of this card. */
    public Timeline timeline() {
        return timeline;
    }

    /** Replaces the timeline contents from a snapshot (persistence/memento). */
    void restoreTimeline(List<TimelineEntry> entries) {
        timeline.restore(entries);
    }
}
