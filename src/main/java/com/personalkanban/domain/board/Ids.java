package com.personalkanban.domain.board;

import java.util.UUID;

/**
 * Single place where identifiers are minted (GRASP Creator): nobody else
 * fabricates {@link ColumnId} or {@link CardId} instances.
 */
public final class Ids {

    private Ids() {
    }

    public static ColumnId newColumnId() {
        return new ColumnId(UUID.randomUUID().toString());
    }

    public static CardId newCardId() {
        return new CardId(UUID.randomUUID().toString());
    }
}
