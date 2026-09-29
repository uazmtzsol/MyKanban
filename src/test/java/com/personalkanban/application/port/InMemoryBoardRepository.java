package com.personalkanban.application.port;

import com.personalkanban.domain.board.BoardDescriptor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Minimal in-memory implementation of the multi-board persistence port. */
public final class InMemoryBoardRepository implements BoardRepository {

    public final Map<BoardId, BoardDescriptor> catalog = new LinkedHashMap<>();
    public final Map<BoardId, BoardMemento> boards = new LinkedHashMap<>();
    public int saveCalls;

    @Override
    public List<BoardDescriptor> listBoards() {
        return new ArrayList<>(catalog.values());
    }

    @Override
    public BoardDescriptor createBoard(String name) {
        BoardDescriptor descriptor = new BoardDescriptor(
                new BoardId(UUID.randomUUID().toString()), name, java.time.Instant.now());
        catalog.put(descriptor.id(), descriptor);
        boards.put(descriptor.id(), BoardMemento.empty());
        return descriptor;
    }

    @Override
    public void renameBoard(BoardId boardId, String newName) {
        BoardDescriptor descriptor = catalog.get(boardId);
        catalog.put(boardId, new BoardDescriptor(boardId, newName, descriptor.createdAt()));
    }

    @Override
    public void deleteBoard(BoardId boardId) {
        catalog.remove(boardId);
        boards.remove(boardId);
    }

    @Override
    public BoardMemento load(BoardId boardId) {
        return boards.getOrDefault(boardId, BoardMemento.empty());
    }

    @Override
    public void save(BoardId boardId, BoardMemento board) {
        boards.put(boardId, board);
        saveCalls++;
    }
}
