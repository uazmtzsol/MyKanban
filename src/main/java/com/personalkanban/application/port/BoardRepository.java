package com.personalkanban.application.port;

import com.personalkanban.domain.board.BoardMemento;

/**
 * Outbound port for board persistence (DIP). The {@link BoardMemento} record
 * doubles as the exchanged DTO: it is already an immutable, complete picture
 * of the board. Splitting read/write into capabilities was considered, but
 * both halves always travel together here, so one focused interface wins
 * on simplicity without hurting segregation.
 */
public interface BoardRepository {

    /** Persists the complete board state, replacing whatever was stored. */
    void save(BoardMemento board);

    /** Loads the complete board state; {@link BoardMemento#empty()} when nothing is stored. */
    BoardMemento load();
}
