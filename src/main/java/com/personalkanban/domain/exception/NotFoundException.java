package com.personalkanban.domain.exception;

import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ColumnId;

/** Thrown when a referenced column or card does not exist. */
public class NotFoundException extends DomainException {

    public NotFoundException(ColumnId columnId) {
        super("Column not found: " + columnId);
    }

    public NotFoundException(CardId cardId) {
        super("Card not found: " + cardId);
    }
}
