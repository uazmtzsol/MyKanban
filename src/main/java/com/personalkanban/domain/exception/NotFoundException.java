package com.personalkanban.domain.exception;

import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.ProcessId;

/** Thrown when a referenced column, card or process does not exist. */
public class NotFoundException extends DomainException {

    public NotFoundException(ColumnId columnId) {
        super("Column not found: " + columnId);
    }

    public NotFoundException(CardId cardId) {
        super("Card not found: " + cardId);
    }

    public NotFoundException(ProcessId processId) {
        super("Process not found: " + processId);
    }
}
