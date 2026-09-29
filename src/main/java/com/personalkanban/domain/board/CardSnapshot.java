package com.personalkanban.domain.board;

import java.time.Instant;

/**
 * Public immutable DTO describing one card. Used as the canonical shape for
 * both persistence (repository adapters) and undo/redo (mementos), so the
 * aggregate's internal constructors can stay hidden.
 */
public record CardSnapshot(CardId id, String title, String description, BoardColor color, Instant createdAt) {

    public CardSnapshot {
        if (id == null || title == null || color == null || createdAt == null) {
            throw new IllegalArgumentException("Card snapshot fields must not be null");
        }
    }

    static CardSnapshot from(Card card) {
        return new CardSnapshot(card.id(), card.title(), card.description(), card.color(), card.createdAt());
    }

    Card toCard(ColumnId ownerId) {
        return Card.restore(id(), ownerId, title(), description(), color(), createdAt());
    }
}
