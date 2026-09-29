package com.personalkanban.application;

import com.personalkanban.application.command.AddCardCommand;
import com.personalkanban.application.command.AddColumnCommand;
import com.personalkanban.application.command.BoardCommand;
import com.personalkanban.application.command.ClearBoardCommand;
import com.personalkanban.application.command.ClearColumnCommand;
import com.personalkanban.application.command.EditCardCommand;
import com.personalkanban.application.command.EditColumnCommand;
import com.personalkanban.application.command.MoveCardCommand;
import com.personalkanban.application.command.MoveColumnCommand;
import com.personalkanban.application.command.RemoveCardCommand;
import com.personalkanban.application.command.RemoveColumnCommand;
import com.personalkanban.application.command.RenameColumnCommand;
import com.personalkanban.application.port.BoardRepository;
import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardColumn;
import com.personalkanban.domain.board.BoardMemento;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.WipLimit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Single entry point for the UI (GoF Facade). Executes commands against the
 * board, keeps bounded memento-based undo/redo, and persists the whole board
 * after every mutation. The UI never touches JDBC; the domain never touches
 * JavaFX.
 */
public final class BoardService {

    private static final int UNDO_CAPACITY = 100;

    private final Board board = new Board();
    private final BoardRepository repository;
    private final Deque<BoardMemento> undoStack = new ArrayDeque<>();
    private final Deque<BoardMemento> redoStack = new ArrayDeque<>();

    public BoardService(BoardRepository repository) {
        this.repository = Objects.requireNonNull(repository);
        board.restore(repository.load());
    }

    // ------------------------------------------------------------------
    // Column use cases
    // ------------------------------------------------------------------

    public ColumnId addColumn(String title, String description, BoardColor color, WipLimit wipLimit) {
        AddColumnCommand command = new AddColumnCommand(title, description, color, wipLimit);
        return execute(command, command::createdColumnId);
    }

    public void renameColumn(ColumnId columnId, String newTitle) {
        execute(new RenameColumnCommand(columnId, newTitle));
    }

    public void editColumn(ColumnId columnId, String description, BoardColor color, WipLimit wipLimit) {
        execute(new EditColumnCommand(columnId, description, color, wipLimit));
    }

    public void removeColumn(ColumnId columnId) {
        execute(new RemoveColumnCommand(columnId));
    }

    public void moveColumn(ColumnId columnId, int targetIndex) {
        execute(new MoveColumnCommand(columnId, targetIndex));
    }

    // ------------------------------------------------------------------
    // Card use cases
    // ------------------------------------------------------------------

    public CardId addCard(ColumnId columnId, String title, String description, BoardColor color) {
        AddCardCommand command = new AddCardCommand(columnId, title, description, color);
        return execute(command, command::createdCardId);
    }

    public void editCard(CardId cardId, String title, String description, BoardColor color) {
        execute(new EditCardCommand(cardId, title, description, color));
    }

    public void removeCard(CardId cardId) {
        execute(new RemoveCardCommand(cardId));
    }

    public void clearColumn(ColumnId columnId) {
        execute(new ClearColumnCommand(columnId));
    }

    public void moveCard(CardId cardId, ColumnId targetColumnId, int targetIndex) {
        execute(new MoveCardCommand(cardId, targetColumnId, targetIndex));
    }

    // ------------------------------------------------------------------
    // Whole-board use cases
    // ------------------------------------------------------------------

    public void clearBoard() {
        execute(new ClearBoardCommand());
    }

    // ------------------------------------------------------------------
    // Queries (read model for the UI)
    // ------------------------------------------------------------------

    public Board board() {
        return board;
    }

    public BoardColumn column(ColumnId columnId) {
        return board.columnOrThrow(columnId);
    }

    // ------------------------------------------------------------------
    // Undo / redo
    // ------------------------------------------------------------------

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        BoardMemento current = BoardMemento.capture(board);
        BoardMemento target = undoStack.pop();
        redoStack.push(current);
        board.restore(target);
        finishTransaction();
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        BoardMemento current = BoardMemento.capture(board);
        BoardMemento target = redoStack.pop();
        undoStack.push(current);
        board.restore(target);
        finishTransaction();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private <T> T execute(BoardCommand command, Supplier<T> result) {
        BoardMemento before = BoardMemento.capture(board);
        command.execute(board);
        pushUndo(before);
        finishTransaction();
        return result.get();
    }

    private void execute(BoardCommand command) {
        BoardMemento before = BoardMemento.capture(board);
        command.execute(board);
        pushUndo(before);
        finishTransaction();
    }

    private void pushUndo(BoardMemento before) {
        undoStack.push(before);
        while (undoStack.size() > UNDO_CAPACITY) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    private void finishTransaction() {
        persist();
        board.drainEvents();
    }

    private void persist() {
        repository.save(BoardMemento.capture(board));
    }
}
