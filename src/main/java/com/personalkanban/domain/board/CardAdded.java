package com.personalkanban.domain.board;

import java.time.Instant;

/** Emitted after a card was added to a column. */
public record CardAdded(CardId cardId, ColumnId columnId, Instant occurredAt) implements DomainEvent {
}
