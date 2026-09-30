package com.personalkanban.domain.exception;

import com.personalkanban.domain.board.ColumnId;

/** Thrown when adding a card would push a column past its WIP limit. */
public class WipLimitExceededException extends DomainException {

    private final int limit;

    public WipLimitExceededException(ColumnId columnId, int limit) {
        super("Column " + columnId + " has reached its WIP limit of " + limit);
        this.limit = limit;
    }

    /** The WIP limit that was exceeded (for friendlier UI messages). */
    public int limit() {
        return limit;
    }
}
