package com.personalkanban.domain.board;

import java.time.Instant;

/** Emitted after a card was removed from the board. */
public record CardRemoved(CardId cardId, ColumnId columnId, Instant occurredAt) implements DomainEvent {
}
