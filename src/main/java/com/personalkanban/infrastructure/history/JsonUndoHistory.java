package com.personalkanban.infrastructure.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalkanban.application.BoardJsonMapper;
import com.personalkanban.application.port.UndoHistory;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Persistent undo history adapter: one JSON file per board holding a bounded
 * stack of mementos. Survives restarts (undo lives beyond app close) and is
 * itself portable because it lives inside the app data directory.
 */
public final class JsonUndoHistory implements UndoHistory {

    // Same wire format as JSON export/import, so a memento survives both.
    private static final ObjectMapper MAPPER = BoardJsonMapper.create();

    private final Path directory;

    public JsonUndoHistory(Path directory) {
        this.directory = directory;
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create undo history directory " + directory, e);
        }
    }

    @Override
    public void push(BoardId boardId, BoardMemento snapshot) {
        Deque<BoardMemento> stack = new ArrayDeque<>(readAll(boardId));
        stack.push(snapshot);
        while (stack.size() > CAPACITY) {
            stack.removeLast();
        }
        writeAll(boardId, new ArrayList<>(stack));
    }

    @Override
    public Optional<BoardMemento> peek(BoardId boardId) {
        List<BoardMemento> stack = readAll(boardId);
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack.get(0));
    }

    @Override
    public Optional<BoardMemento> pop(BoardId boardId) {
        List<BoardMemento> stack = readAll(boardId);
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        BoardMemento top = stack.remove(0);
        writeAll(boardId, stack);
        return Optional.of(top);
    }

    @Override
    public void clear(BoardId boardId) {
        deleteFile(boardId);
    }

    @Override
    public int depth(BoardId boardId) {
        return readAll(boardId).size();
    }

    // ------------------------------------------------------------------
    // File format: a JSON array of board snapshots
    // ------------------------------------------------------------------

    private Path fileFor(BoardId boardId) {
        String safe = boardId.value().replaceAll("[^A-Za-z0-9._-]", "_");
        return directory.resolve(safe + ".history.json");
    }

    private List<BoardMemento> readAll(BoardId boardId) {
        Path file = fileFor(boardId);
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            List<BoardMemento> snapshots = List.of(MAPPER.readValue(file.toFile(), BoardMemento[].class));
            return new ArrayList<>(snapshots);
        } catch (IOException e) {
            // Corrupt history must never block the app: start empty.
            return new ArrayList<>();
        }
    }

    private void writeAll(BoardId boardId, List<BoardMemento> snapshots) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(fileFor(boardId).toFile(), snapshots);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not persist undo history for " + boardId, e);
        }
    }

    private void deleteFile(BoardId boardId) {
        try {
            Files.deleteIfExists(fileFor(boardId));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not clear undo history for " + boardId, e);
        }
    }
}
