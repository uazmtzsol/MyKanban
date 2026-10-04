package com.personalkanban.domain.board;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Public immutable DTO describing one card. Canonical shape shared by
 * persistence adapters and undo/redo mementos. It carries the plain-text
 * notes, the flat checklist and the process assignment (null = none). The
 * card's time-tracking timeline is carried by {@link BoardMemento}, not here,
 * so this DTO stays focused on the card's own state.
 */
public record CardSnapshot(CardId id, String title, String description, BoardColor color,
                           LocalDate dueDate, List<String> labels, Instant createdAt,
                           String notes, List<ChecklistItem> checklist, ProcessId processId) {

    public CardSnapshot {
        if (id == null || title == null || color == null || createdAt == null) {
            throw new IllegalArgumentException("Card snapshot fields must not be null");
        }
        labels = labels == null ? List.of() : List.copyOf(labels);
        notes = notes == null ? "" : notes;
        checklist = checklist == null ? List.of() : List.copyOf(checklist);
    }

    /** Backward-compatible constructor: legacy shape without session-4 fields. */
    public CardSnapshot(CardId id, String title, String description, BoardColor color,
                        LocalDate dueDate, List<String> labels, Instant createdAt) {
        this(id, title, description, color, dueDate, labels, createdAt, "", List.of(), null);
    }

    /** Backward-compatible constructor: no due date, no labels. */
    public CardSnapshot(CardId id, String title, String description, BoardColor color, Instant createdAt) {
        this(id, title, description, color, null, List.of(), createdAt, "", List.of(), null);
    }

    static CardSnapshot from(Card card) {
        return new CardSnapshot(card.id(), card.title(), card.description(), card.color(),
                card.dueDate(), card.labels(), card.createdAt(),
                card.notes(), card.checklist(), card.processId());
    }

    Card toCard(ColumnId ownerId) {
        Card card = Card.restore(id(), ownerId, title(), description(), color(),
                dueDate(), labels(), createdAt());
        card.annotate(notes());
        for (ChecklistItem item : checklist()) {
            card.adoptChecklistItem(item);
        }
        card.assignTo(processId());
        return card;
    }
}
