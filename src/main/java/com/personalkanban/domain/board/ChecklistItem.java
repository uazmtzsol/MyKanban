package com.personalkanban.domain.board;

import java.util.UUID;

/**
 * One checklist entry of a card (user decision, session 4): a flat,
 * one-level list — no hierarchy. An immutable value object: text/done
 * changes produce a new instance via {@link #withText}/{@link #withDone},
 * applied by the owning {@link Card}. The stable {@code id} is what lets a
 * checklist item be converted into its own card later. A record keeps the
 * undo-history/export JSON round trip annotation-free (same pattern as the
 * snapshot DTOs).
 */
public record ChecklistItem(String id, String text, boolean done) {

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
    }

    /** Creates a fresh, pending item with a new identity. */
    public static ChecklistItem newItem(String text) {
        return new ChecklistItem(UUID.randomUUID().toString(), text, false);
    }

    /** Copy with new text (rename); keeps identity and done state. */
    public ChecklistItem withText(String newText) {
        return new ChecklistItem(id, newText, done);
    }

    /** Copy with a new done state (check/uncheck). */
    public ChecklistItem withDone(boolean newDone) {
        return new ChecklistItem(id, text, newDone);
    }

    @Override
    public String toString() {
        return (done ? "[x] " : "[ ] ") + text;
    }
}
