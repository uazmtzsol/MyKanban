package com.personalkanban.application;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.personalkanban.application.command.AddCardCommand;
import com.personalkanban.application.command.AddColumnCommand;
import com.personalkanban.application.command.AddProcessCommand;
import com.personalkanban.application.command.AssignProcessCommand;
import com.personalkanban.application.command.BoardCommand;
import com.personalkanban.application.command.ClearColumnCommand;
import com.personalkanban.application.command.ConvertChecklistItemCommand;
import com.personalkanban.application.command.EditCardCommand;
import com.personalkanban.application.command.EditColumnCommand;
import com.personalkanban.application.command.AddChecklistItemCommand;
import com.personalkanban.application.command.AddLabelsCommand;
import com.personalkanban.application.command.LinkCardsCommand;
import com.personalkanban.application.command.MoveCardCommand;
import com.personalkanban.application.command.MoveCardToSlotCommand;
import com.personalkanban.application.command.MoveColumnCommand;
import com.personalkanban.application.command.MoveCardsCommand;
import com.personalkanban.application.command.NoteCardCommand;
import com.personalkanban.application.command.RecolorCardsCommand;
import com.personalkanban.application.command.RemoveChecklistItemCommand;
import com.personalkanban.application.command.ToggleChecklistItemCommand;
import com.personalkanban.application.command.RenameChecklistItemCommand;
import com.personalkanban.application.command.RemoveProcessCommand;
import com.personalkanban.application.command.ToggleLabelCommand;
import com.personalkanban.application.command.RemoveCardsCommand;
import com.personalkanban.application.command.RemoveLabelsCommand;
import com.personalkanban.application.command.RemoveCardCommand;
import com.personalkanban.application.command.RemoveColumnCommand;
import com.personalkanban.application.command.RenameColumnCommand;
import com.personalkanban.application.command.RenameProcessCommand;
import com.personalkanban.application.command.SetChecklistFromLinesCommand;
import com.personalkanban.application.command.SortColumnByPriorityCommand;
import com.personalkanban.application.command.UnlinkCardsCommand;
import com.personalkanban.application.port.BoardRepository;
import com.personalkanban.application.port.SettingsStore;
import com.personalkanban.application.port.UndoHistory;
import com.personalkanban.domain.board.Board;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardColumn;
import com.personalkanban.domain.board.BoardDescriptor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;
import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ChecklistItem;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.DependencyGuard;
import com.personalkanban.domain.board.Process;
import com.personalkanban.domain.board.ProcessId;
import com.personalkanban.domain.board.WipLimit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Single entry point for the UI (GoF Facade). Coordinates the active board,
 * the board catalog (multi-board), command execution with memento snapshots,
 * persistent undo/redo, and JSON export/import of any board. The JSON
 * serialization lives here — Jackson is a technology-neutral library, not a
 * layer violation.
 */
public final class BoardService {

    private static final String LAST_BOARD_KEY = "board.last";
    private static final String COLUMN_STATE_PREFIX = "ui.columnstate.";
    private static final String CARD_VIEW_PREFIX = "ui.cardview.";
    private static final String BACKGROUND_KEY = "ui.background"; // "path|opacity"
    private static final String UI_BACKGROUND_PREFIX = "ui.background.";

    private final BoardRepository repository;
    private final UndoHistory history;
    private final SettingsStore settings;

    private final List<BoardDescriptor> catalog = new ArrayList<>();
    private Board activeBoard;
    private BoardMemento current = BoardMemento.empty();
    private final Deque<BoardMemento> redoStack = new ArrayDeque<>();

    private final ObjectMapper mapper;

    public BoardService(BoardRepository repository, UndoHistory history, SettingsStore settings) {
        this.repository = Objects.requireNonNull(repository);
        this.history = Objects.requireNonNull(history);
        this.settings = Objects.requireNonNull(settings);
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.catalog.addAll(repository.listBoards());
        this.activeBoard = selectInitialBoard();
    }

    /** Opens the last-used board, else the oldest one, else creates the first board. */
    private Board selectInitialBoard() {
        Optional<String> lastId = settings.get(LAST_BOARD_KEY);
        if (lastId.isPresent()) {
            BoardId candidate = new BoardId(lastId.get());
            if (catalog.stream().anyMatch(descriptor -> descriptor.id().equals(candidate))) {
                return hydrate(candidate);
            }
        }
        if (!catalog.isEmpty()) {
            return hydrate(catalog.get(0).id());
        }
        BoardDescriptor first = repository.createBoard("My Board");
        catalog.add(first);
        activeBoard = new Board(first.id());
        current = BoardMemento.empty();
        persist();
        return activeBoard;
    }

    // ------------------------------------------------------------------
    // Multi-board use cases
    // ------------------------------------------------------------------

    /** Immutable view of the board catalog, ordered by creation time. */
    public List<BoardDescriptor> boards() {
        return List.copyOf(catalog);
    }

    public BoardId activeBoardId() {
        return activeBoard.id();
    }

    public void openBoard(BoardId boardId) {
        requireInCatalog(boardId);
        flushRedo();
        activeBoard = hydrate(boardId);
        settings.put(LAST_BOARD_KEY, boardId.value());
    }

    public BoardId createBoard(String name) {
        BoardDescriptor descriptor = repository.createBoard(name);
        catalog.add(descriptor);
        history.clear(descriptor.id());
        return descriptor.id();
    }

    public void renameBoard(BoardId boardId, String newName) {
        repository.renameBoard(boardId, newName);
        for (int i = 0; i < catalog.size(); i++) {
            BoardDescriptor descriptor = catalog.get(i);
            if (descriptor.id().equals(boardId)) {
                catalog.set(i, new BoardDescriptor(boardId, newName, descriptor.createdAt()));
            }
        }
    }

    public void deleteBoard(BoardId boardId) {
        if (catalog.size() <= 1) {
            throw new IllegalStateException("The last board cannot be deleted");
        }
        repository.deleteBoard(boardId);
        history.clear(boardId);
        catalog.removeIf(descriptor -> descriptor.id().equals(boardId));
        if (activeBoard.id().equals(boardId)) {
            openBoard(catalog.get(0).id());
        }
    }

    private void requireInCatalog(BoardId boardId) {
        if (catalog.stream().noneMatch(descriptor -> descriptor.id().equals(boardId))) {
            throw new IllegalStateException("Board not in catalog: " + boardId);
        }
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
        return addCard(columnId, title, description, color, null, List.of());
    }

    public CardId addCard(ColumnId columnId, String title, String description, BoardColor color,
                          LocalDate dueDate, List<String> labels) {
        AddCardCommand command = new AddCardCommand(columnId, title, description, color, dueDate, labels);
        return execute(command, command::createdCardId);
    }

    /** Full card creation (session 4): notes, checklist and process in one step. */
    public CardId addCard(ColumnId columnId, String title, String description, BoardColor color,
                          LocalDate dueDate, List<String> labels,
                          String notes, List<ChecklistItem> checklist, ProcessId processId) {
        AddCardCommand command = new AddCardCommand(columnId, title, description, color,
                dueDate, labels, notes, checklist, processId);
        return execute(command, command::createdCardId);
    }

    public void editCard(CardId cardId, String title, String description, BoardColor color) {
        editCard(cardId, title, description, color,
                currentDueDateOf(cardId), currentLabelsOf(cardId));
    }

    public void editCard(CardId cardId, String title, String description, BoardColor color,
                         LocalDate dueDate, List<String> labels) {
        execute(new EditCardCommand(cardId, title, description, color, dueDate, labels));
    }

    public void removeCard(CardId cardId) {
        execute(new RemoveCardCommand(cardId));
    }

    // ------------------------------------------------------------------
    // Card notes (session 4.4)
    // ------------------------------------------------------------------

    /** Sets the plain-text notes of one card, undoable. */
    public void setCardNotes(CardId cardId, String notes) {
        execute(new NoteCardCommand(cardId, notes));
    }

    // ------------------------------------------------------------------
    // Card checklist (session 4.5)
    // ------------------------------------------------------------------

    /** Adds a checklist item to one card; returns the created item's id. */
    public String addChecklistItem(CardId cardId, String text) {
        AddChecklistItemCommand command = new AddChecklistItemCommand(cardId, text);
        return execute(command, command::createdItemId);
    }

    /** Marks or unmarks one checklist item, undoable. */
    public void setChecklistItemDone(CardId cardId, String itemId, boolean done) {
        execute(new ToggleChecklistItemCommand(cardId, itemId, done));
    }

    /** Renames one checklist item, undoable. */
    public void renameChecklistItem(CardId cardId, String itemId, String newText) {
        execute(new RenameChecklistItemCommand(cardId, itemId, newText));
    }

    /** Removes one checklist item, undoable. */
    public void removeChecklistItem(CardId cardId, String itemId) {
        execute(new RemoveChecklistItemCommand(cardId, itemId));
    }

    /**
     * Converts a checklist item into its own card (same column); returns the
     * created card's id. One undoable step: undo removes the card and
     * restores the item on the source checklist.
     */
    public CardId convertChecklistItemToCard(CardId cardId, String itemId) {
        ConvertChecklistItemCommand command = new ConvertChecklistItemCommand(cardId, itemId);
        return execute(command, command::createdCardId);
    }

    /**
     * Rebuilds a card's checklist from plain lines (dialog advanced form),
     * one undoable step. "[x] t" = done, "[ ] t"/"t" = pending; matching
     * by text keeps existing item identities.
     */
    public void setChecklistFromLines(CardId cardId, List<String> lines) {
        execute(new SetChecklistFromLinesCommand(cardId, lines));
    }

    /**
     * Stable priority sort of ONE column: (★+!) first, then (!), then (★);
     * the rest keeps its relative order. One undoable step.
     */
    public void sortColumnByPriority(ColumnId columnId) {
        execute(new SortColumnByPriorityCommand(columnId));
    }

    // ------------------------------------------------------------------
    // Processes (session 4.6)
    // ------------------------------------------------------------------

    /** Creates a process and returns its id. */
    public ProcessId addProcess(String name) {
        AddProcessCommand command = new AddProcessCommand(name);
        return execute(command, () -> command.createdProcess().id());
    }

    /** Renames a process. */
    public void renameProcess(ProcessId processId, String newName) {
        execute(new RenameProcessCommand(processId, newName));
    }

    /** Deletes a process; member cards become unassigned. */
    public void removeProcess(ProcessId processId) {
        execute(new RemoveProcessCommand(processId));
    }

    /** Processes of the active board, in creation order. */
    public List<Process> processes() {
        return activeBoard.processList();
    }

    /** Assigns one card to a process (null = unassign). */
    public void assignCardToProcess(CardId cardId, ProcessId processId) {
        execute(new AssignProcessCommand(cardId, processId));
    }

    // ------------------------------------------------------------------
    // Precedence links (session 4.6)
    // ------------------------------------------------------------------

    /** Links {@code from → to} ("from precedes to"); rejects cycles. */
    public void linkCards(CardId from, CardId to) {
        execute(new LinkCardsCommand(from, to));
    }

    /** Removes the {@code from → to} precedence link. */
    public void unlinkCards(CardId from, CardId to) {
        execute(new UnlinkCardsCommand(from, to));
    }

    /**
     * Suggested execution order of the given cards under the precedence
     * links (pure domain logic, delegated for testability).
     */
    public DependencyGuard.Result suggestedOrder(List<CardId> cards) {
        return DependencyGuard.topologicalOrder(activeBoard, cards);
    }

    // ------------------------------------------------------------------
    // Bulk card use cases (multi-selection)
    // ------------------------------------------------------------------

    public void addLabelsToCards(List<CardId> cardIds, List<String> labels) {
        execute(new AddLabelsCommand(cardIds, labels));
    }

    public void removeLabelsFromCards(List<CardId> cardIds, List<String> labels) {
        execute(new RemoveLabelsCommand(cardIds, labels));
    }

    public void recolorCards(List<CardId> cardIds, BoardColor color) {
        execute(new RecolorCardsCommand(cardIds, color));
    }

    public void removeCards(List<CardId> cardIds) {
        execute(new RemoveCardsCommand(cardIds));
    }

    public void moveCardsToColumn(List<CardId> cardIds, ColumnId targetColumnId) {
        execute(new MoveCardsCommand(cardIds, targetColumnId));
    }

    /** Toggles a quick flag (e.g. Urgente/Importante) on one card, undoable. */
    public void toggleCardLabel(CardId cardId, String label) {
        execute(new ToggleLabelCommand(cardId, label));
    }

    /**
     * Autocomplete vocabulary for label fields: the system flags first, then
     * every label in use on the active board (first-seen spelling, stable
     * order). Pure read — no persistence involved.
     */
    public List<String> labelVocabulary() {
        List<String> vocabulary = new ArrayList<>();
        vocabulary.add(Card.LABEL_IMPORTANT);
        vocabulary.add(Card.LABEL_URGENT);
        for (BoardColumn column : activeBoard.columns()) {
            for (Card card : column.cards()) {
                for (String label : card.labels()) {
                    boolean duplicate = vocabulary.stream().anyMatch(label::equalsIgnoreCase);
                    if (!duplicate) {
                        vocabulary.add(label);
                    }
                }
            }
        }
        return vocabulary;
    }

    /** Reads per-column widths of the given board ("ui.colwidths.<boardId>"). */
    public java.util.Map<String, Integer> columnWidthsOf(BoardId boardId) {
        return settings.get("ui.colwidths." + boardId.value())
                .map(BoardService::parseWidthMap)
                .orElse(java.util.Map.of());
    }

    /** Persists per-column widths of the given board (UI-only preference). */
    public void setColumnWidths(BoardId boardId, java.util.Map<String, Integer> widths) {
        StringBuilder stored = new StringBuilder();
        widths.forEach((id, width) -> {
            if (stored.length() > 0) {
                stored.append(';');
            }
            stored.append(id).append('=').append(width);
        });
        settings.put("ui.colwidths." + boardId.value(), stored.toString());
    }

    private static java.util.Map<String, Integer> parseWidthMap(String stored) {
        java.util.Map<String, Integer> widths = new java.util.LinkedHashMap<>();
        if (stored == null || stored.isBlank()) {
            return widths;
        }
        for (String entry : stored.split(";")) {
            int equals = entry.indexOf('=');
            if (equals > 0) {
                try {
                    widths.put(entry.substring(0, equals).strip(),
                            Integer.parseInt(entry.substring(equals + 1).strip()));
                } catch (NumberFormatException ignored) {
                    // skip malformed entries; the column falls back to default width
                }
            }
        }
        return widths;
    }

    /** Reads the collapsed-columns set of the given board ("ui.columnstate.<boardId>"). */
    public java.util.Set<String> collapsedColumnsOf(BoardId boardId) {
        return settings.get(COLUMN_STATE_PREFIX + boardId.value())
                .map(BoardService::parseIdSet)
                .orElse(java.util.Set.of());
    }

    /** Reads the card view preferences of the given board (defaults if none). */
    public CardViewSettings cardViewSettingsOf(BoardId boardId) {
        return CardViewSettings.fromJson(
                settings.get(CARD_VIEW_PREFIX + boardId.value()).orElse(null));
    }

    /** Persists the card view preferences of the given board. */
    public void setCardViewSettings(BoardId boardId, CardViewSettings cardViewSettings) {
        settings.put(CARD_VIEW_PREFIX + boardId.value(), cardViewSettings.toJson());
    }

    /** Reads the background preference of the given board (null = none). */
    public String boardBackgroundOf(BoardId boardId) {
        return settings.get(UI_BACKGROUND_PREFIX + boardId.value()).orElse(null);
    }

    /** Persists the background preference of the given board (null clears it). */
    public void setBoardBackground(BoardId boardId, String stored) {
        settings.put(UI_BACKGROUND_PREFIX + boardId.value(), stored == null ? "" : stored);
    }

    /** Global background (any board); format "path|opacity" or null. */
    public String background() {
        return settings.get(BACKGROUND_KEY)
                .filter(value -> !value.isBlank())
                .orElse(null);
    }

    /** Sets the global background; format "path|opacity", null clears it. */
    public void setBackground(String stored) {
        settings.put(BACKGROUND_KEY, stored == null ? "" : stored);
    }

    /** Persists the collapsed-columns set of the given board (UI-only preference). */
    public void setCollapsedColumns(BoardId boardId, java.util.Set<String> collapsed) {
        String key = COLUMN_STATE_PREFIX + boardId.value();
        if (collapsed.isEmpty()) {
            settings.put(key, "");
        } else {
            settings.put(key, String.join(";", collapsed));
        }
    }

    private static java.util.Set<String> parseIdSet(String stored) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for (String part : stored.split(";")) {
            if (!part.isBlank()) {
                ids.add(part.strip());
                }
        }
        return ids;
    }

    public void clearColumn(ColumnId columnId) {
        execute(new ClearColumnCommand(columnId));
    }

    public void moveCard(CardId cardId, ColumnId targetColumnId, int targetIndex) {
        execute(new MoveCardCommand(cardId, targetColumnId, targetIndex));
    }

    /** Moves a card into the visual slot between two neighbors (drop indicator). */
    public void moveCardToSlot(CardId cardId, ColumnId targetColumnId, int slotIndex) {
        execute(new MoveCardToSlotCommand(cardId, targetColumnId, slotIndex));
    }

    // ------------------------------------------------------------------
    // Queries (read model for the UI)
    // ------------------------------------------------------------------

    public Board board() {
        return activeBoard;
    }

    public BoardColumn column(ColumnId columnId) {
        return activeBoard.columnOrThrow(columnId);
    }

    // ------------------------------------------------------------------
    // Undo / redo (persistent via the UndoHistory port)
    // ------------------------------------------------------------------

    public boolean canUndo() {
        return history.depth(activeBoard.id()) > 0;
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void undo() {
        BoardMemento target = history.peek(activeBoard.id()).orElse(null);
        if (target == null) {
            return;
        }
        BoardMemento undone = BoardMemento.capture(activeBoard);
        applySnapshot(target);
        try {
            persist();
        } catch (RuntimeException failure) {
            applySnapshot(undone); // keep memory consistent with the database
            throw failure;         // history entry untouched (peek did not pop)
        }
        history.pop(activeBoard.id());
        redoStack.push(undone);
        activeBoard.drainEvents();
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        BoardMemento target = redoStack.pop();
        BoardMemento redone = BoardMemento.capture(activeBoard);
        applySnapshot(target);
        try {
            persist();
        } catch (RuntimeException failure) {
            applySnapshot(redone); // keep memory consistent with the database
            throw failure;
        }
        history.push(activeBoard.id(), redone);
        activeBoard.drainEvents();
    }

    // ------------------------------------------------------------------
    // JSON export / import
    // ------------------------------------------------------------------

    public void exportBoard(Path targetFile) {
        Objects.requireNonNull(targetFile);
        String name = catalog.stream()
                .filter(descriptor -> descriptor.id().equals(activeBoard.id()))
                .findFirst()
                .map(BoardDescriptor::name)
                .orElse("board");
        BoardExport export = new BoardExport(
                new BoardExportPayload(activeBoard.id().value(), name, current));
        try (OutputStream out = Files.newOutputStream(targetFile)) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(out, export);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not export board to " + targetFile, e);
        }
    }

    /** Imports a board file as a NEW board with a unique name; returns its id. */
    public BoardId importBoard(Path sourceFile) {
        Objects.requireNonNull(sourceFile);
        BoardExport export;
        try (InputStream in = Files.newInputStream(sourceFile)) {
            export = mapper.readValue(in, BoardExport.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read board file " + sourceFile, e);
        }
        if (export == null || export.payload() == null || export.payload().board() == null) {
            throw new IllegalArgumentException("Not a valid Personal Kanban board file");
        }
        String requested = export.payload().name();
        String base = (requested == null || requested.isBlank()) ? "imported" : requested.strip();
        String candidate = base;
        int suffix = 2;
        while (true) {
            String probe = candidate; // effectively-final view for the lambda below
            if (catalog.stream().noneMatch(descriptor -> descriptor.name().equalsIgnoreCase(probe))) {
                break;
            }
            candidate = base + " (" + suffix++ + ")";
        }
        BoardId id = createBoard(candidate);
        repository.save(id, export.payload().board());
        return id;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private void applySnapshot(BoardMemento target) {
        activeBoard.restore(target);
        current = target;
    }

    private Board hydrate(BoardId boardId) {
        Board board = new Board(boardId);
        current = repository.load(boardId);
        board.restore(current);
        return board;
    }

    private void persist() {
        current = BoardMemento.capture(activeBoard);
        repository.save(activeBoard.id(), current);
    }

    /**
     * Persists the post-command state and only then records the undo entry.
     * If persistence fails, the aggregate is rolled back to {@code before} so
     * memory never diverges from the database, and the history stays clean.
     */
    private void finishTransaction(BoardMemento before) {
        try {
            persist();
        } catch (RuntimeException failure) {
            activeBoard.restore(before);
            current = before;
            throw failure;
        }
        history.push(activeBoard.id(), before); // adapter enforces the capacity bound
        redoStack.clear();
        activeBoard.drainEvents();
    }

    private <T> T execute(BoardCommand command, java.util.function.Supplier<T> result) {
        BoardMemento before = BoardMemento.capture(activeBoard);
        command.execute(activeBoard);
        finishTransaction(before);
        return result.get();
    }

    private void execute(BoardCommand command) {
        BoardMemento before = BoardMemento.capture(activeBoard);
        command.execute(activeBoard);
        finishTransaction(before);
    }

    private void flushRedo() {
        redoStack.clear();
    }

    private LocalDate currentDueDateOf(CardId cardId) {
        return activeBoard.findCard(cardId).map(Card::dueDate).orElse(null);
    }

    private List<String> currentLabelsOf(CardId cardId) {
        return activeBoard.findCard(cardId).map(Card::labels).orElse(List.of());
    }

    // ------------------------------------------------------------------
    // JSON DTOs (records: Jackson maps them via their canonical constructors)
    // ------------------------------------------------------------------

    public record BoardExportPayload(String boardId, String name, BoardMemento board) {
    }

    public record BoardExport(BoardExportPayload payload) {
    }
}
