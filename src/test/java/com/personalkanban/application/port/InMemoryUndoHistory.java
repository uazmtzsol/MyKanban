package com.personalkanban.application.port;

import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory undo history test double; mirrors the JSON adapter's LIFO bound. */
public final class InMemoryUndoHistory implements UndoHistory {

    private static final int CAPACITY = 100;

    private final Map<BoardId, Deque<BoardMemento>> stacks = new HashMap<>();

    @Override
    public void push(BoardId boardId, BoardMemento snapshot) {
        Deque<BoardMemento> stack = stacks.computeIfAbsent(boardId, id -> new ArrayDeque<>());
        stack.push(snapshot);
        while (stack.size() > CAPACITY) {
            stack.removeLast();
        }
    }

    @Override
    public Optional<BoardMemento> peek(BoardId boardId) {
        Deque<BoardMemento> stack = stacks.get(boardId);
        return stack == null || stack.isEmpty() ? Optional.empty() : Optional.of(stack.peek());
    }

    @Override
    public Optional<BoardMemento> pop(BoardId boardId) {
        Deque<BoardMemento> stack = stacks.get(boardId);
        return stack == null || stack.isEmpty() ? Optional.empty() : Optional.of(stack.pop());
    }

    @Override
    public void clear(BoardId boardId) {
        stacks.remove(boardId);
    }

    @Override
    public int depth(BoardId boardId) {
        Deque<BoardMemento> stack = stacks.get(boardId);
        return stack == null ? 0 : stack.size();
    }
}
