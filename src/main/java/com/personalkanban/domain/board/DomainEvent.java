package com.personalkanban.domain.board;

import java.time.Instant;

/** Marker for domain events; carriers of "something happened" for observers. */
public sealed interface DomainEvent permits CardAdded, CardMoved, CardRemoved {

    Instant occurredAt();
}
