package com.personalkanban.domain.board;

import java.util.UUID;

/**
 * One checklist entry of a card (user decision, session 4; hierarchy added in
 * T16 · B8): a <b>one-level</b> list — an item is either a top-level task or a
 * subtask of one ({@code parentId} null vs. set; grandchildren are rejected by
 * {@link Card}). An immutable value object: text/done/hierarchy changes produce
 * a new instance via {@link #withText}/{@link #withDone}/{@link #withParent},
 * applied by the owning {@link Card}. The stable {@code id} is what lets a
 * checklist item be converted into its own card later. A record keeps the
 * undo-history/export JSON round trip annotation-free (same pattern as the
 * snapshot DTOs); a missing {@code parentId} in old JSON simply means a
 * top-level item.
 */
public record ChecklistItem(String id, String text, boolean done, String parentId) {

    public static final int MAX_TEXT_LENGTH = 120;

    public ChecklistItem {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Checklist item id must not be blank");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Checklist item text must not be blank");
        }
        String stripped = text.strip();
        if (stripped.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "Checklist item longer than " + MAX_TEXT_LENGTH + " chars");
        }
        text = stripped;
        parentId = (parentId == null || parentId.isBlank()) ? null : parentId.strip();
    }

    /** Convenience shape for a top-level item (no parent). */
    public ChecklistItem(String id, String text, boolean done) {
        this(id, text, done, null);
    }

    /** Creates a fresh, pending top-level item with a new identity. */
    public static ChecklistItem newItem(String text) {
        return new ChecklistItem(UUID.randomUUID().toString(), text, false, null);
    }

    /** Creates a fresh, pending subtask of {@code parentId}. */
    public static ChecklistItem newSubtask(String text, String parentId) {
        return new ChecklistItem(UUID.randomUUID().toString(), text, false, parentId);
    }

    /** Copy with new text (rename); keeps identity, done state and parent. */
    public ChecklistItem withText(String newText) {
        return new ChecklistItem(id, newText, done, parentId);
    }

    /** Copy with a new done state (check/uncheck). */
    public ChecklistItem withDone(boolean newDone) {
        return new ChecklistItem(id, text, newDone, parentId);
    }

    /** Copy under another parent (null = make it a top-level item). */
    public ChecklistItem withParent(String newParentId) {
        return new ChecklistItem(id, text, done, newParentId);
    }

    /** True when this item hangs under another one (one level only). */
    public boolean isSubtask() {
        return parentId != null;
    }

    @Override
    public String toString() {
        return (done ? "[x] " : "[ ] ") + text;
    }
}
