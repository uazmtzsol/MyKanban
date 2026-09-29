package com.personalkanban.domain.exception;

import com.personalkanban.domain.board.ColumnId;

/** Thrown when adding a card would push a column past its WIP limit. */
public class WipLimitExceededException extends DomainException {

    public WipLimitExceededException(ColumnId columnId, int limit) {
        super("Column " + columnId + " has reached its WIP limit of " + limit);
    }
}
