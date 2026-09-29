package com.personalkanban.domain.board;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Public immutable DTO describing one card. Used as the canonical shape for
 * both persistence (repository adapters) and undo/redo (mementos), so the
 * aggregate's internal constructors can stay hidden.
 */
public record CardSnapshot(CardId id, String title, String description, BoardColor color,
                           LocalDate dueDate, List<String> labels, Instant createdAt) {

    public CardSnapshot {
        if (id == null || title == null || color == null || createdAt == null) {
            throw new IllegalArgumentException("Card snapshot fields must not be null");
        }
        labels = labels == null ? List.of() : List.copyOf(labels);
    }

    /** Backward-compatible constructor: no due date, no labels. */
    public CardSnapshot(CardId id, String title, String description, BoardColor color, Instant createdAt) {
        this(id, title, description, color, null, List.of(), createdAt);
    }

    static CardSnapshot from(Card card) {
        return new CardSnapshot(card.id(), card.title(), card.description(), card.color(),
                card.dueDate(), card.labels(), card.createdAt());
    }

    Card toCard(ColumnId ownerId) {
        return Card.restore(id(), ownerId, title(), description(), color(), dueDate(), labels(), createdAt());
    }
}
