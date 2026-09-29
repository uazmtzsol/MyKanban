package com.personalkanban.application.port;

import com.personalkanban.domain.board.BoardDescriptor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for multi-board persistence (DIP). A store holds a catalog of
 * boards plus one complete snapshot per board. The {@link BoardMemento} record
 * doubles as the exchanged DTO.
 */
public interface BoardRepository {

    /** Lists the board catalog, ordered by creation time (GRASP Information Expert). */
    List<BoardDescriptor> listBoards();

    /** Creates a board entry in the catalog and stores its (initially empty) snapshot. */
    BoardDescriptor createBoard(String name);

    /** Renames a board in the catalog. */
    void renameBoard(BoardId boardId, String newName);

    /** Deletes a board and everything it contains. */
    void deleteBoard(BoardId boardId);

    /** Loads the complete state of one board; {@link BoardMemento#empty()} when unknown. */
    BoardMemento load(BoardId boardId);

    /** Persists the complete state of one board, replacing whatever was stored. */
    void save(BoardId boardId, BoardMemento board);
}
