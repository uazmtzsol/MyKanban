package com.personalkanban.domain.board;

import java.time.Instant;

/** Emitted after a card moved between positions or columns. */
public record CardMoved(CardId cardId, ColumnId fromColumn, ColumnId toColumn, Instant occurredAt)
        implements DomainEvent {
}
