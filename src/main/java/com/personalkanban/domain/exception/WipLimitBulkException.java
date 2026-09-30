package com.personalkanban.domain.exception;

import com.personalkanban.domain.board.ColumnId;

/**
 * Bulk variant of {@link WipLimitExceededException}: a batch move would push
 * the target column past its WIP limit. Carries how many cards over the
 * limit the batch is, so the UI can say \"how many to deselect\" instead of a
 * raw limit number.
 */
public class WipLimitBulkException extends WipLimitExceededException {

    private final int excess;

    public WipLimitBulkException(ColumnId columnId, int limit, int excess) {
        super(columnId, limit);
        this.excess = excess;
    }

    /** How many selected cards over the limit the batch is (>= 1). */
    public int excess() {
        return excess;
    }
}
