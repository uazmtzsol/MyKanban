package com.personalkanban.ui;

import com.personalkanban.AppContext;
import com.personalkanban.application.BoardService;
import com.personalkanban.application.BoardTemplate;
import com.personalkanban.application.CardViewSettings;
import com.personalkanban.application.DueDigest;
import com.personalkanban.application.GlobalShortcuts;
import com.personalkanban.application.QuickCapture;
import com.personalkanban.application.ThemeColors;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardColumn;
import com.personalkanban.domain.board.BoardDescriptor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.LabelFilter;
import com.personalkanban.domain.board.LabelSuggester;
import com.personalkanban.domain.board.WipLimit;
import com.personalkanban.ui.theme.ThemeManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Root controller (GRASP Controller): builds the board UI, owns all user
 * intents, and delegates every mutation to {@link BoardService}. Language
 * switches rebuild the whole scene graph — a small graph, so full rebuild is
 * the cheapest correct way to re-translate every text (tooltips included).
 */
public final class BoardController {

    // Non-final by design: {@link #rebind} rewires every collaborator when the
    // user opens or creates another database file; only the scene-root identity
    // of this controller survives the swap.
    private AppContext context;
    private BoardService service;
    private I18n i18n;
    private ThemeManager themeManager;
    private UndoRedoController undoRedo;
    private Dialogs dialogs;
    // Global shortcuts (ui.shortcuts): loaded from settings in the constructor
    // and again on rebind(), and mutated live by the shortcuts dialog.
    // Session 10 fix: persisted bindings used to be saved but never read back,
    // so a rebinding silently reset to the defaults on every restart.
    private GlobalShortcuts shortcuts;

    /** Result of the last bulk labels dialog (so the caller can read add vs remove). */
    private Boolean lastBulkLabelsAdd;

    /**
     * All card ids visible in the active board under the current filters:
     * process, label text/mode and quick flags, exactly what the columns
     * render — otherwise the arrow keys would stop on hidden cards.
     */
    private List<CardId> visibleCardIds() {
        return visibleColumnsCards().stream().flatMap(List::stream).toList();
    }

    /**
     * The same visible cards, grouped per column in display order — the
     * column-major shape {@link CardNavigator} reasons about (session 10).
     */
    private List<List<CardId>> visibleColumnsCards() {
        LabelFilter labelFilter = currentLabelFilter();
        LabelFilter quickFilter = currentQuickFlagFilter();
        List<List<CardId>> columns = new ArrayList<>();
        for (BoardColumn column : service.board().columns()) {
            columns.add(column.cards().stream()
                    .filter(this::matchesProcessFilter)
                    .filter(this::matchesDueFilter)
                    .filter(labelFilter::matches)
                    .filter(quickFilter::matches)
                    .map(card -> card.id())
                    .toList());
        }
        return columns;
    }


    private final StringProperty title = new SimpleStringProperty();
    private final VBox root;

    // The scene bound by bindScene(): the session-10 keyboard map needs the
    // focus owner to tell "the user is typing" apart from "navigate the board".
    private Scene boundScene;

    private HBox columnsRow;
    private ScrollPane boardScroller;
    // View mode: kanban (columns) or processes (one row per process).
    private boolean processView;
    // The card the arrow keys move around in the processes view.
    // Survives the rebuilds refresh() performs, so the keyboard
    // workflow keeps working after a link changes.
    private CardId processFocusId;
    private Menu boardMenu;
    private Menu databaseMenu;
    private Menu processMenu;
    private Menu viewMenu;
    private Menu labelsMenu;
    private Menu helpMenu;
    private Label boardNameLabel;
    private HBox toolbarReference;

    // Non-modal sync feedback (design §4.1: a discreet notice, not a modal
    // interruption). Rebuilt with the rest of the scene graph.
    private Label syncNotice;

    // Label filter state; survives language-driven rebuilds of the controls.
    private String activeFilterLabels = "";
    private LabelFilterMode activeFilterMode; // null = no mode chosen

    /**
     * How the label filter combines, and whether it excludes instead (user
     * request, 2026-10-13: «no tiene la etiqueta X» and «no tiene ninguna
     * etiqueta»). Each entry carries its i18n key so the control follows the
     * language without the value changing identity.
     */
    enum LabelFilterMode {
        /** Cards must carry every listed label. */
        ALL("filter.mode.all"),
        /** Cards must carry at least one listed label. */
        ANY("filter.mode.any"),
        /** Cards must carry none of the listed labels. */
        NOT("filter.mode.not"),
        /** Cards must carry no labels at all (the list is ignored). */
        NO_LABELS("filter.mode.no.labels");

        private final String key;

        LabelFilterMode(String key) {
            this.key = key;
        }

        String key() {
            return key;
        }
    }
    private TextField labelFilterField;
    private ComboBox<LabelFilterMode> labelFilterMode;
    // Quick flag filters (★ Importante / ! Urgente); both active = AND (must carry both).
    private final java.util.Set<String> quickFlagFilters = new java.util.LinkedHashSet<>();

    // Multi-selection mode: the selected cards all live in selectionColumn.
    private boolean selectionMode;
    private ColumnId selectionColumn;
    private final java.util.Set<CardId> selectedCards = new java.util.LinkedHashSet<>();
    private HBox selectionBar;
    // The card filter bar. It is hidden while selection mode is on: the two
    // bars are alternatives, never shown together (user request, 2026-10-13).
    private HBox filterBar;

    // Vertical scroll of each column's card list, remembered across the
    // rebuilds a refresh performs (user request, 2026-10-13, N-13: selecting a
    // card used to send the column back to the top).
    private final java.util.Map<String, Double> columnScroll = new java.util.HashMap<>();

    // Due-date filter (session 15, T5 = N-6 + B3): one scope at a time.
    private DueDigest.Scope dueScope;
    private final java.util.EnumMap<DueDigest.Scope, javafx.scene.control.ToggleButton> dueButtons =
            new java.util.EnumMap<>(DueDigest.Scope.class);
    // Discreet startup notice (a non-modal band, like the sync notice).
    private Label dueNotice;
    private boolean dueNoticeDismissed;
    // Where the notice takes the user when it is clicked.
    private DueDigest.Scope dueNoticeJump;

    // Collapsed columns of the active board (UI preference, per board).
    private java.util.Set<String> collapsedColumns = java.util.Set.of();

    // Per-column widths of the active board (UI preference, per board).
    private java.util.Map<String, Integer> columnWidths = java.util.Map.of();

    // Card view preferences of the active board (UI preference, per board).
    private CardViewSettings cardViewSettings = new CardViewSettings();

    // Process filter of the active board: null = no specific process
    // (either every card is shown, or — with processFilterNone — only
    // the cards that belong to no process at all).
    private com.personalkanban.domain.board.ProcessId activeProcessFilter;

    // "None" process filter: true = only cards outside every process.
    private boolean processFilterNone;

    // The process filter combo of the filter bar (rebuilt on rebuilds).
    private ComboBox<String> processFilterCombo;

    // Session 10 (board cleanup): archived cards (LABEL_ARCHIVED) stay in
    // the database but are hidden until this toggle says otherwise.
    // Session-scoped on purpose: hiding is a view decision, not data.
    private boolean showArchived;

    // Which database menus were built (the File menu must re-run its builder).

    public BoardController(AppContext context) {
        this.context = context;
        this.service = context.boardService();
        this.i18n = new I18n(context.savedLocale());
        this.themeManager = context.themeManager();
        this.undoRedo = new UndoRedoController(service, i18n, this::refresh);
        this.dialogs = new Dialogs(i18n);
        this.shortcuts = service.globalShortcuts();
        this.root = new VBox();
        this.root.getStyleClass().add("board-root");
        collapsedColumns = service.collapsedColumnsOf(context.boardService().activeBoardId());
        rebuildAll();
    }

    public VBox root() {
        return root;
    }

    public StringProperty titleProperty() {
        return title;
    }

    /** Appends the build stamp to the window title (set once at startup). */
    public void appendVersionToTitle(String stamp) {
        title.set(title.get() + "  [" + stamp + "]");
    }

    // ------------------------------------------------------------------
    // Intents: card view modes (board default + per-card overrides)
    // ------------------------------------------------------------------

    /** Live settings used by the card view builder while painting. */
    public CardViewSettings currentCardViewSettings() {
        return cardViewSettings;
    }

    /** Sets the BOARD default mode (toolbar menu / Ctrl+1-2-3). */
    public void onSetBoardCardViewMode(CardViewSettings.Mode mode) {
        cardViewSettings.setBoardDefault(mode);
        service.setCardViewSettings(service.activeBoardId(), cardViewSettings);
        refresh();
    }

    /** Per-card override (context menu); null clears it back to the default. */
    public void onSetCardViewMode(com.personalkanban.domain.board.CardId cardId,
                                  CardViewSettings.Mode mode) {
        cardViewSettings.setOverride(cardId.value(), mode);
        service.setCardViewSettings(service.activeBoardId(), cardViewSettings);
        refresh();
    }

    /**
     * Per-card content choice (user request N-14): what this card's front
     * shows below the title — description, tasks or notes. No board default:
     * the user picks it card by card from the button on the card.
     */
    /** Saved chip colors of one label style-rule key, if the user chose any (T15 · B7). */
    public Optional<com.personalkanban.application.StylePrefs.StyleColors> labelChipColors(
            String key, boolean dark) {
        return service.labelStyle(key, dark);
    }

    public void onSetCardContent(CardId cardId, CardViewSettings.Content content) {
        cardViewSettings.setContentOverride(cardId.value(), content);
        service.setCardViewSettings(service.activeBoardId(), cardViewSettings);
        refresh();
    }

    /** Clears every per-card override (all cards follow the board default). */
    public void onResetCardViewModes() {
        cardViewSettings.clearOverrides();
        service.setCardViewSettings(service.activeBoardId(), cardViewSettings);
        refresh();
    }

    public void bindScene(Scene scene) {
        undoRedo.bindScene(scene);
        applyThemeOverride();
        scene.getAccelerators().put(javafx.scene.input.KeyCombination.valueOf("Shortcut+1"),
                () -> onSetBoardCardViewMode(CardViewSettings.Mode.TITLE_ONLY));
        scene.getAccelerators().put(javafx.scene.input.KeyCombination.valueOf("Shortcut+2"),
                () -> onSetBoardCardViewMode(CardViewSettings.Mode.TITLE_PREVIEW));
        scene.getAccelerators().put(javafx.scene.input.KeyCombination.valueOf("Shortcut+3"),
                () -> onSetBoardCardViewMode(CardViewSettings.Mode.FULL));
        // Session 4 (fixed shortcuts + F1 help, user decision).
        scene.getAccelerators().put(javafx.scene.input.KeyCombination.valueOf("F1"),
                this::onShowShortcutsHelp);
        scene.getAccelerators().put(javafx.scene.input.KeyCombination.valueOf("Shortcut+N"),
                this::onNewBoard);
        // Session 14 (user request B2): search the whole catalogue. A plain
        // scene accelerator like Ctrl+N / Ctrl+D, not one of the re-bindable
        // card actions (those are about the card on screen).
        scene.getAccelerators().put(javafx.scene.input.KeyCombination.valueOf("Shortcut+Shift+F"),
                this::onGlobalSearch);
        // Filter shortcuts read the persisted bindings like the arrow keys,
        // so File → Keyboard shortcuts can rewire them (defaults Ctrl+F/Ctrl+P).
        scene.getAccelerators().put(keyComboFor(GlobalShortcuts.Action.LABEL_FILTER),
                this::onFocusFilter);
        // Add/remove labels (default Ctrl+L): same dialog as the bulk bar,
        // scoped to the selection or to the keyboard-focused card.
        scene.getAccelerators().put(keyComboFor(GlobalShortcuts.Action.LABELS),
                this::onLabelsForCurrentCards);
        scene.getAccelerators().put(javafx.scene.input.KeyCombination.valueOf("Shortcut+D"),
                this::onToggleDarkMode);
        scene.getAccelerators().put(javafx.scene.input.KeyCombination.valueOf("Shortcut+Q"),
                this::onExit);
        // Session 5 (user question: "¿Ctrl+S?"): yes — a manual "save now"
        // is cheap reassurance even though every change is already committed.
        scene.getAccelerators().put(javafx.scene.input.KeyCombination.valueOf("Shortcut+S"),
                this::onSaveNow);
        // Process filter: focus and open the filter combo, like Ctrl+F
        // does for the label filter.
        scene.getAccelerators().put(keyComboFor(GlobalShortcuts.Action.PROCESS_FILTER),
                this::onFocusProcessFilter);
        // Session 10 (user request): the whole card keyboard map runs as a
        // scene-level event FILTER instead of accelerators. A filter fires
        // before the focused control gets the event, so the keys work no
        // matter what has focus — which is exactly what the toolbar buttons
        // did that the keyboard did not. The configurable bindings
        // (Enter/Escape/Left/Right by default) are matched here too, so
        // File → Keyboard shortcuts still rewires them.
        this.boundScene = scene;
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, this::onBoardKeyPressed);
    }

    GlobalShortcuts globalShortcuts() {
        return shortcuts;
    }

    /** KeyCombination for a global shortcut action, or an empty binding when unset. */
    private javafx.scene.input.KeyCombination keyComboFor(GlobalShortcuts.Action action) {
        String combination = shortcuts.get(action);
        return combination.isBlank()
                ? javafx.scene.input.KeyCombination.valueOf("Shortcut++")
                : javafx.scene.input.KeyCombination.valueOf(combination);
    }

    /** Focuses the next visible card (default: Right). */
    private void onMoveFocusNext() {
        kanbanFocusCard(cardFocusNext());
    }

    /** Focuses the previous visible card (default: Left). */
    private void onMoveFocusPrev() {
        kanbanFocusCard(cardFocusPrev());
    }

    /** Edits the focused card (default: Enter). */
    private void onEditFocusedCard() {
        if (cardFocused != null) {
            onEditCard(cardFocused);
        }
    }

    /**
     * The configured «Exit» key (Esc by default) in the kanban view. It works
     * in stages so one key covers the three things a user expects:
     * first it drops the current selection, then it leaves selection mode, and
     * finally it clears the focused card (the original behavior).
     */
    void onExitKey() {
        if (dialogs.modalActive()) {
            return;
        }
        if (selectionMode && !selectedCards.isEmpty()) {
            selectedCards.clear();
            rebuildSelectionBar();
            refresh();
            return;
        }
        if (selectionMode) {
            exitSelectionMode();
            return;
        }
        onClearFocus();
    }

    /** Drops the kanban focus, or cancels the active modal dialog if any. */
    private void onClearFocus() {
        if (!dialogs.modalActive()) {
            cardFocused = null;
            syncCurrentCardHighlight();
            // Move the focus owner off the card so the highlight goes away.
            root.requestFocus();
        }
    }

    /** The card with keyboard focus in the kanban view (null = none). */
    private CardId cardFocused;

    // ------------------------------------------------------------------
    // Session 10: the full keyboard map (scene event filter)
    // ------------------------------------------------------------------

    /**
     * Runs before the focused control can consume the key, so navigation
     * works no matter what has focus (card, toolbar button, scroller…).
     * While the user types in a field or drives a list/combo the keys stay
     * with that control; only Escape is taken over (drop the card focus).
     * The processes view keeps its own keys (ProcessViewBuilder.installKeys)
     * except Escape and Enter, which are handled here for consistency.
     */
    private void onBoardKeyPressed(javafx.scene.input.KeyEvent event) {
        if (event.isConsumed() || dialogs.modalActive()) {
            return;
        }
        javafx.scene.input.KeyCode code = event.getCode();
        boolean alt = event.isAltDown();
        boolean ctrl = event.isShortcutDown();
        boolean plain = !alt && !ctrl && !event.isShiftDown() && !event.isMetaDown();

        if (processView) {
            // Arrows, Delete and modifiers belong to the diagram here.
            if (matches(GlobalShortcuts.Action.EXIT, event)) {
                onClearProcessFocus();
                event.consume();
            } else if (matches(GlobalShortcuts.Action.EDIT_CARD, event)) {
                CardId target = processFocusId != null ? processFocusId : cardFocused;
                if (target != null) {
                    onEditCard(target);
                    event.consume();
                }
            }
            return;
        }

        if (!inTextEntryContext()) {
            if (alt && !ctrl && !event.isMetaDown()) {
                if (code == javafx.scene.input.KeyCode.LEFT) {
                    focusNeighbouringColumn(-1);
                    event.consume();
                    return;
                }
                if (code == javafx.scene.input.KeyCode.RIGHT) {
                    focusNeighbouringColumn(1);
                    event.consume();
                    return;
                }
                if (code == javafx.scene.input.KeyCode.I) {
                    toggleFocusedFlag(com.personalkanban.domain.board.Card.LABEL_IMPORTANT);
                    event.consume();
                    return;
                }
                if (code == javafx.scene.input.KeyCode.U) {
                    toggleFocusedFlag(com.personalkanban.domain.board.Card.LABEL_URGENT);
                    event.consume();
                    return;
                }
            }
            if (ctrl && !alt && !event.isShiftDown() && !event.isMetaDown()) {
                if (code == javafx.scene.input.KeyCode.LEFT) {
                    switchBoard(-1);
                    event.consume();
                    return;
                }
                if (code == javafx.scene.input.KeyCode.RIGHT) {
                    switchBoard(1);
                    event.consume();
                    return;
                }
            }
            if (plain) {
                if (code == javafx.scene.input.KeyCode.DOWN) {
                    focusVertical(1);
                    event.consume();
                    return;
                }
                if (code == javafx.scene.input.KeyCode.UP) {
                    focusVertical(-1);
                    event.consume();
                    return;
                }
                int position = digitPosition(code);
                if (position >= 0) {
                    focusCardAtPosition(position);
                    event.consume();
                    return;
                }
                if (code == javafx.scene.input.KeyCode.DELETE) {
                    deleteFocusedCard();
                    event.consume();
                    return;
                }
            }
        }

        // Configurable bindings (defaults: Enter edits, Escape drops focus).
        if (matches(GlobalShortcuts.Action.EXIT, event)) {
            onExitKey();
            event.consume();
            return;
        }
        if (inTextEntryContext()) {
            return; // arrows / digits / Enter stay with the field or list
        }
        if (matches(GlobalShortcuts.Action.EDIT_CARD, event)) {
            onEditFocusedCard();
            event.consume();
            return;
        }
        if (matches(GlobalShortcuts.Action.NEXT_CARD, event)) {
            onMoveFocusNext();
            event.consume();
            return;
        }
        if (matches(GlobalShortcuts.Action.PREV_CARD, event)) {
            onMoveFocusPrev();
            event.consume();
        }
    }

    /** Whether the keyboard currently belongs to a text field or a list control. */
    private boolean inTextEntryContext() {
        javafx.scene.Node node = boundScene == null ? null : boundScene.getFocusOwner();
        while (node != null) {
            if (node instanceof javafx.scene.control.TextInputControl input && input.isEditable()) {
                return true;
            }
            if (node instanceof javafx.scene.control.ComboBoxBase
                    || node instanceof javafx.scene.control.ListView
                    || node instanceof javafx.scene.control.TableView
                    || node instanceof javafx.scene.control.TreeView
                    || node instanceof javafx.scene.control.TabPane) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    /** Match of a configurable shortcut (blank or unparsable = never matches). */
    private boolean matches(GlobalShortcuts.Action action, javafx.scene.input.KeyEvent event) {
        String combination = shortcuts.get(action);
        if (combination.isBlank()) {
            return false;
        }
        try {
            return javafx.scene.input.KeyCombination.valueOf(combination).match(event);
        } catch (IllegalArgumentException unparsable) {
            return false;
        }
    }

    /** 0-based position of a plain digit key (top row or keypad), or -1. */
    private static int digitPosition(javafx.scene.input.KeyCode code) {
        return switch (code) {
            case DIGIT1, NUMPAD1 -> 0;
            case DIGIT2, NUMPAD2 -> 1;
            case DIGIT3, NUMPAD3 -> 2;
            case DIGIT4, NUMPAD4 -> 3;
            case DIGIT5, NUMPAD5 -> 4;
            case DIGIT6, NUMPAD6 -> 5;
            case DIGIT7, NUMPAD7 -> 6;
            case DIGIT8, NUMPAD8 -> 7;
            case DIGIT9, NUMPAD9 -> 8;
            default -> -1;
        };
    }

    /** Down / Up inside the current column (wrapping at both ends). */
    private void focusVertical(int direction) {
        kanbanFocusCard(CardNavigator.stepInColumn(visibleColumnsCards(), cardFocused, direction));
    }

    /** Alt+Left / Alt+Right: first card of the neighbouring column. */
    private void focusNeighbouringColumn(int direction) {
        kanbanFocusCard(CardNavigator.neighbouringColumnFirst(
                visibleColumnsCards(), cardFocused, direction));
    }

    /** Digit keys: the n-th card of the current column. */
    private void focusCardAtPosition(int position) {
        kanbanFocusCard(CardNavigator.cardAtPosition(visibleColumnsCards(), cardFocused, position));
    }

    /** Alt+I / Alt+U: quick flags on the focused card (undoable like a click). */
    private void toggleFocusedFlag(String label) {
        if (cardFocused != null) {
            onToggleCardLabel(cardFocused, label);
        }
    }

    /** Delete: removes the focused card after the usual confirmation. */
    private void deleteFocusedCard() {
        if (cardFocused != null) {
            onRemoveCard(cardFocused);
        }
    }

    /** Ctrl+Left / Ctrl+Right: previous / next board, focusing its first card. */
    private void switchBoard(int direction) {
        List<com.personalkanban.domain.board.BoardDescriptor> boards = service.boards();
        if (boards.size() < 2) {
            return;
        }
        int index = -1;
        for (int i = 0; i < boards.size(); i++) {
            if (boards.get(i).id().equals(service.activeBoardId())) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return;
        }
        int next = (index + direction + boards.size()) % boards.size();
        if (next == index) {
            return;
        }
        cardFocused = null;
        onOpenBoard(boards.get(next).id());
        kanbanFocusCard(CardNavigator.firstCard(visibleColumnsCards()));
    }

    /** Sets kanban focus onto a card, highlighting and revealing it. */
    private void kanbanFocusCard(CardId id) {
        if (id == null) {
            return;
        }
        if (cardFocused != null && cardFocused.equals(id)) {
            return;
        }
        cardFocused = id;
        syncCurrentCardHighlight();
        requestFocusOnCardNode(id);
    }

    /** Focuses and reveals the rendered node of a card, when it is built. */
    private void requestFocusOnCardNode(CardId id) {
        findKanbanCardNode(id).ifPresent(node -> {
            node.requestFocus();
            scrollCardIntoView(node);
        });
    }

    /**
     * Scrolls the board so the focused card is fully visible (a card reached
     * with the arrow keys may sit outside the current viewport).
     */
    private void scrollCardIntoView(Node node) {
        if (boardScroller == null || node == null || boardScroller.getContent() == null) {
            return;
        }
        boardScroller.applyCss();
        boardScroller.layout();
        Bounds viewport = boardScroller.getViewportBounds();
        Bounds nodeBounds = node.localToScene(node.getBoundsInLocal());
        Bounds viewBounds = boardScroller.localToScene(boardScroller.getBoundsInLocal());
        if (viewport == null || nodeBounds == null || viewBounds == null) {
            return;
        }
        double extraVertical = boardScroller.getContent().getBoundsInLocal().getHeight()
                - viewport.getHeight();
        if (extraVertical > 0) {
            double delta = (nodeBounds.getMinY() - viewBounds.getMinY()) / extraVertical;
            if (delta < 0 || nodeBounds.getMaxY() > viewBounds.getMaxY()) {
                boardScroller.setVvalue(Math.clamp(boardScroller.getVvalue() + delta, 0.0, 1.0));
            }
        }
        double extraHorizontal = boardScroller.getContent().getBoundsInLocal().getWidth()
                - viewport.getWidth();
        if (extraHorizontal > 0) {
            double delta = (nodeBounds.getMinX() - viewBounds.getMinX()) / extraHorizontal;
            if (delta < 0 || nodeBounds.getMaxX() > viewBounds.getMaxX()) {
                boardScroller.setHvalue(Math.clamp(boardScroller.getHvalue() + delta, 0.0, 1.0));
            }
        }
    }

    /**
     * The next visible card by natural board order: across and then down,
     * wrapping within the visible cards of the active board. Reasonable for
     * populated boards without inventing a global navigation mode.
     */
    private CardId cardFocusNext() {
        List<CardId> visible = visibleCardIds();
        if (visible.isEmpty()) {
            return null;
        }
        int index = cardFocused == null
                ? -1
                : visible.indexOf(cardFocused);
        return visible.get((index + 1) % visible.size());
    }

    /** The previous visible card (reverse of {@link #cardFocusNext}). */
    private CardId cardFocusPrev() {
        List<CardId> visible = visibleCardIds();
        if (visible.isEmpty()) {
            return null;
        }
        int index = cardFocused == null
                ? 0
                : visible.indexOf(cardFocused);
        return visible.get((index - 1 + visible.size()) % visible.size());
    }

    private Optional<Node> findKanbanCardNode(CardId id) {
        Node content = boardScroller.getContent();
        if (content == null || processView) {
            return Optional.empty();
        }
        return findNodeWithData(content, id);
    }

    private void onFocusFilter() {
        if (labelFilterField != null) {
            labelFilterField.requestFocus();
        }
    }

    /** Focuses the process filter combo and opens it (Ctrl+P). */
    private void onFocusProcessFilter() {
        if (processFilterCombo != null) {
            processFilterCombo.requestFocus();
            processFilterCombo.show();
        }
    }

    // ------------------------------------------------------------------
    // Full UI construction (also used to apply a new language/theme state)
    // ------------------------------------------------------------------

    private void rebuildAll() {
        // A rebuild (board/language/database switch) leaves selection mode.
        selectionMode = false;
        selectedCards.clear();
        selectionColumn = null;
        collapsedColumns = service.collapsedColumnsOf(service.activeBoardId());
        cardViewSettings = service.cardViewSettingsOf(service.activeBoardId());
        columnWidths = service.columnWidthsOf(service.activeBoardId());

        HBox toolbar = buildToolbar();
        filterBar = buildFilterBar();
        MenuBar menuBar = buildMenuBar();

        columnsRow = new HBox(12);
        columnsRow.setPadding(new Insets(14));

        boardScroller = new ScrollPane(columnsRow);
        boardScroller.setFitToHeight(true);
        // Transparent scroller: the board background image (or the
        // plain fallback) must show through the scroll area.
        boardScroller.getStyleClass().add("board-scroller");
        VBox.setVgrow(boardScroller, Priority.ALWAYS);

        buildSelectionBar();
        syncNotice = new Label();
        syncNotice.getStyleClass().add("sync-notice");
        syncNotice.setVisible(false);
        syncNotice.setManaged(false);
        syncNotice.setWrapText(true);
        syncNotice.setPadding(new Insets(4, 12, 4, 12));
        // Session 15 (T5): a discreet due-date band, same shape as the sync one.
        // A new board/language/database starts a new session: the user gets to
        // see the reminder again.
        dueNoticeDismissed = false;
        dueNotice = new Label();
        dueNotice.getStyleClass().addAll("sync-notice", "due-notice");
        dueNotice.setVisible(false);
        dueNotice.setManaged(false);
        dueNotice.setWrapText(true);
        dueNotice.setPadding(new Insets(4, 12, 4, 12));
        dueNotice.setOnMouseClicked(e -> onDueNoticeClicked());
        root.getChildren().setAll(menuBar, syncNotice, dueNotice, toolbar, selectionBar, filterBar,
                boardScroller);
        updateBoardIdentity();
        refresh();
    }

    private HBox buildToolbar() {
        Label brand = new Label("Personal Kanban");
        brand.getStyleClass().add("brand");

        // User request N-3: no «add column» button here any more — the action
        // lives in the Boards menu and in each column's own menu (add to the
        // left / to the right), which is where the user was looking for it.
        Button viewToggle = toolButton("\u21C4", "toolbar.view.toggle");
        viewToggle.setOnAction(e -> onToggleView());

        Button undoButton = toolButton("\u21B6", "toolbar.undo");
        undoButton.disableProperty().bind(undoRedo.canUndoProperty().not());
        undoButton.setOnAction(e -> undoRedo.undo());

        Button redoButton = toolButton("\u21B7", "toolbar.redo");
        redoButton.disableProperty().bind(undoRedo.canRedoProperty().not());
        redoButton.setOnAction(e -> undoRedo.redo());

        Button darkMode = toolButton("\uD83C\uDF19", "toolbar.dark.mode");
        darkMode.setOnAction(e -> onToggleDarkMode());

        Button exportPdf = toolButton("\uD83D\uDCC4", "toolbar.export.pdf");
        exportPdf.setOnAction(e -> onExportBoardPdf());

        // Archive/unarchive (user request): toggles the "Archivada" system
        // label on the selected cards, or on the focused one when nothing
        // is selected. Archived cards stay hidden until the 🗄 filter.
        Button archive = toolButton("\uD83D\uDCE6", "card.archive.menu");
        archive.setOnAction(e -> onArchiveCurrentCards());

        // "Guardar" (session 5, user request): every mutation already
        // commits instantly; this confirms the state to the user.
        Button save = toolButton("\uD83D\uDCBE", "toolbar.save");
        save.setOnAction(e -> onSaveNow());

        // Global search (session 14, B2): finding something in a board full of
        // cards is a daily move, so it gets a button next to the accelerators
        // and the menu entry.
        Button search = toolButton("\uD83D\uDD0D", "search.tooltip");
        search.setOnAction(e -> onGlobalSearch());

        // Card view mode: one button, radio menu (board-wide default).
        MenuButton cardViewMenu = new MenuButton("\u2637");
        cardViewMenu.getStyleClass().addAll("tool-button");
        cardViewMenu.setTooltip(new Tooltip(i18n.text("cardview.button.tip")));
        for (CardViewSettings.Mode mode : CardViewSettings.Mode.values()) {
            RadioMenuItem item = new RadioMenuItem(i18n.text(cardViewTextKey(mode)));
            item.setUserData(mode);
            item.setOnAction(e -> onSetBoardCardViewMode(mode));
            cardViewMenu.getItems().add(item);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        boardNameLabel = new Label();
        boardNameLabel.getStyleClass().add("board-name");

        // Session 10: the navigation buttons (prev / next / edit) are gone on
        // purpose — the whole map lives on the keyboard now
        // (onBoardKeyPressed): arrows, Alt+arrows, Ctrl+arrows, digits,
        // Enter, Alt+I/U and Delete.

        HBox toolbar = new HBox(8, brand, boardNameLabel, viewToggle,
                undoButton, redoButton, save, search, darkMode, exportPdf, archive, cardViewMenu,
                spacer);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("toolbar");
        toolbar.setPadding(new Insets(10));
        toolbarReference = toolbar;
        return toolbar;
    }

    /**
     * The label filter bar: free-text labels plus an AND/OR selector.
     * Empty selector = no filtering (the domain's {@link LabelFilter#none()}).
     */
    private HBox buildFilterBar() {
        Label filterLabel = new Label(i18n.text("filter.labels"));

        labelFilterField = new TextField(activeFilterLabels);
        labelFilterField.setPromptText(i18n.text("filter.labels.prompt"));
        labelFilterField.setPrefWidth(240);
        // A rebuild (language, board, database) must restore the state of the
        // «sin etiquetas» mode too.
        labelFilterField.setDisable(activeFilterMode == LabelFilterMode.NO_LABELS);
        labelFilterField.textProperty().addListener((obs, old, value) -> {
            activeFilterLabels = value == null ? "" : value;
            refresh();
        });
        // Autocomplete on the filter too: same vocabulary as the card dialog.
        LabelAutoComplete.attach(labelFilterField, new LabelSuggester(java.util.List.of()),
                List::of).setVocabularySupplier(this::filterVocabulary);

        labelFilterMode = new ComboBox<>();
        labelFilterMode.getItems().setAll(LabelFilterMode.values());
        labelFilterMode.setPromptText(i18n.text("filter.mode.empty"));
        labelFilterMode.setValue(activeFilterMode);
        labelFilterMode.setPrefWidth(150);
        labelFilterMode.setTooltip(new Tooltip(i18n.text("filter.mode.tooltip")));
        // The value is a stable enum; only its rendering is localized.
        labelFilterMode.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(LabelFilterMode mode) {
                return mode == null ? "" : i18n.text(mode.key());
            }

            @Override
            public LabelFilterMode fromString(String text) {
                return activeFilterMode;
            }
        });
        // React to the VALUE, not to a user action: a programmatic change (the
        // chip click clearing the mode, a programmatic selection in a test) has
        // to filter the same way a click does.
        labelFilterMode.valueProperty().addListener(
                (observable, was, mode) -> onFilterModeChanged(mode));

        Button clearFilter = toolButton("\u2715", "filter.clear");
        clearFilter.setOnAction(e -> clearFilters());

        javafx.scene.control.ToggleButton importantFilter = quickFlagFilterButton(
                "\u2605", com.personalkanban.domain.board.Card.LABEL_IMPORTANT, "filter.flag.important");
        javafx.scene.control.ToggleButton urgentFilter = quickFlagFilterButton(
                "!", com.personalkanban.domain.board.Card.LABEL_URGENT, "filter.flag.urgent");

        // Due-date filters (session 15, T5 = N-6): overdue, today, this week.
        // One scope at a time; clicking the active one clears the filter.
        dueButtons.clear();
        javafx.scene.control.ToggleButton overdueFilter = dueScopeButton(
                "\u26A0", "filter.due.overdue", DueDigest.Scope.OVERDUE);
        javafx.scene.control.ToggleButton dueTodayFilter = dueScopeButton(
                "\uD83D\uDCC5", "filter.due.today", DueDigest.Scope.TODAY);
        javafx.scene.control.ToggleButton dueWeekFilter = dueScopeButton(
                "\uD83D\uDDD3", "filter.due.week", DueDigest.Scope.WEEK);

        // Process filter: "All" and "None" are explicit entries, so the
        // filter is a real tri-state — a chosen process can be undone again
        // and the cards that belong to no process at all can be shown
        // (user request). Every entry is reachable by keyboard.
        ComboBox<String> processFilter = new ComboBox<>();
        processFilterCombo = processFilter;
        processFilter.setPrefWidth(150);
        processFilter.setTooltip(new Tooltip(i18n.text("filter.process.tooltip")));
        var processes = service.processes();
        // A filter chosen on another board must not linger invisibly here.
        if (activeProcessFilter != null && processes.stream()
                .noneMatch(process -> process.id().equals(activeProcessFilter))) {
            activeProcessFilter = null;
            processFilterNone = false;
        }
        processFilter.getItems().add(i18n.text("filter.process.all"));
        processFilter.getItems().add(i18n.text("filter.process.none"));
        for (var process : processes) {
            processFilter.getItems().add(process.name());
        }
        if (activeProcessFilter != null) {
            processes.stream()
                    .filter(process -> process.id().equals(activeProcessFilter))
                    .findFirst()
                    .ifPresent(process -> processFilter.setValue(process.name()));
        } else {
            processFilter.setValue(i18n.text(
                    processFilterNone ? "filter.process.none" : "filter.process.all"));
        }
        processFilter.setOnAction(e -> {
            String selected = processFilter.getValue();
            if (selected == null || selected.equals(i18n.text("filter.process.all"))) {
                onFilterProcessAll();
            } else if (selected.equals(i18n.text("filter.process.none"))) {
                onFilterProcessNone();
            } else {
                processes.stream()
                        .filter(process -> process.name().equals(selected))
                        .findFirst()
                        .ifPresent(process -> onFilterProcess(process.id()));
            }
        });

        // Archived cards are hidden by default (session 10); this toggle
        // brings them back without touching any data.
        javafx.scene.control.ToggleButton archivedFilter = new javafx.scene.control.ToggleButton("\uD83D\uDDC4");
        archivedFilter.getStyleClass().addAll("tool-button", "filter-flag");
        archivedFilter.setSelected(showArchived);
        archivedFilter.setTooltip(new Tooltip(i18n.text("filter.archived.show")));
        archivedFilter.setOnAction(e -> {
            showArchived = archivedFilter.isSelected();
            refresh();
        });

        HBox bar = new HBox(8, filterLabel, labelFilterField, labelFilterMode, clearFilter,
                importantFilter, urgentFilter, overdueFilter, dueTodayFilter, dueWeekFilter,
                processFilter, archivedFilter);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().addAll("toolbar", "filter-bar");
        bar.setPadding(new Insets(6, 10, 6, 10));
        return bar;
    }

    /**
     * Clears every filter of the bar — label text and mode, process choice and
     * the quick flags ★/! — and refreshes. Returns true when something was
     * actually filtered out, so the callers that rebuild the whole scene
     * (opening a search hit, session 14 B2) only pay for a rebuild when the
     * visible cards really changed.
     */
    private boolean clearFilters() {
        boolean changed = !activeFilterLabels.isBlank()
                || activeFilterMode != null
                || activeProcessFilter != null
                || processFilterNone
                || dueScope != null
                || !quickFlagFilters.isEmpty();
        labelFilterField.clear();
        labelFilterMode.setValue(null);
        labelFilterField.setDisable(false);
        activeFilterLabels = "";
        activeFilterMode = null;
        activeProcessFilter = null;
        processFilterNone = false;
        dueScope = null;
        syncDueButtons();
        quickFlagFilters.clear();
        if (processFilterCombo != null) {
            processFilterCombo.setValue(i18n.text("filter.process.all"));
        }
        refresh();
        return changed;
    }

    /** One toggle in the quick-flag filter row; both active together = AND. */
    private javafx.scene.control.ToggleButton quickFlagFilterButton(String glyph, String label, String tipKey) {
        javafx.scene.control.ToggleButton button = new javafx.scene.control.ToggleButton(glyph);
        button.getStyleClass().addAll("tool-button", "filter-flag");
        button.setSelected(quickFlagFilters.contains(label));
        button.setTooltip(new Tooltip(i18n.text(tipKey)));
        button.setFocusTraversable(false);
        button.setOnAction(e -> {
            if (button.isSelected()) {
                quickFlagFilters.add(label);
            } else {
                quickFlagFilters.remove(label);
            }
            refresh();
        });
        return button;
    }

    // ------------------------------------------------------------------
    // Multi-selection mode (bulk actions on cards of one column)
    // ------------------------------------------------------------------

    /** Hidden unless selection mode is on; rebuilt on refresh. */
    private void buildSelectionBar() {
        selectionBar = new HBox(8);
        selectionBar.getStyleClass().addAll("toolbar", "selection-bar");
        selectionBar.setPadding(new Insets(6, 10, 6, 10));
        selectionBar.setVisible(false);
        selectionBar.setManaged(false);
    }

    /** Toggles selection mode for a column; clears stale selections. */
    public void onToggleSelectionMode(ColumnId columnId) {
        boolean sameColumn = columnId.equals(selectionColumn);
        if (selectionMode && sameColumn) {
            exitSelectionMode();
            return;
        }
        if (!sameColumn) {
            selectedCards.clear();
            selectionColumn = columnId;
        }
        selectionMode = true;
        rebuildSelectionBar();
        refresh();
    }

    private void exitSelectionMode() {
        selectionMode = false;
        selectedCards.clear();
        rebuildSelectionBar();
        refresh();
    }

    public void onToggleCardSelection(CardId cardId) {
        if (selectedCards.contains(cardId)) {
            selectedCards.remove(cardId);
        } else {
            selectedCards.add(cardId);
        }
        refresh();
        updateSelectionBarState();
    }

    /** Package-visible query used by the view builders while painting cards. */
    boolean isCardSelected(CardId cardId) {
        return selectedCards.contains(cardId);
    }

    /**
     * Mouse click on a card (session 13, user request): makes it the "current"
     * card — the very state the arrow keys move — so the click selects the
     * card, its appearance changes, and the shortcuts that act on the current
     * card (labels, archive, quick flags) apply to whatever was clicked.
     * Exactly one card is current at a time.
     */
    void onCardSelected(CardId cardId) {
        if (cardId == null || cardId.equals(cardFocused)) {
            return;
        }
        cardFocused = cardId;
        syncCurrentCardHighlight();
    }

    /** Whether this card is the current one ("actual"). */
    boolean isCardCurrent(CardId cardId) {
        return cardId != null && cardId.equals(cardFocused);
    }

    /**
     * Paints .card-current on exactly one card node (the current one) without
     * rebuilding the board, so a click only restyles nodes and never disturbs
     * the scroll position or a drag in progress.
     */
    private void syncCurrentCardHighlight() {
        Node content = boardScroller == null ? null : boardScroller.getContent();
        if (content instanceof Parent parent) {
            markCurrentCard(parent);
        }
    }

    /**
     * Recursively tags the card nodes; the current one carries the class.
     *
     * <p>A refresh rebuilds the columns, and a freshly built ScrollPane has no
     * skin yet, so its content is not among its children until the next layout
     * pass — it has to be visited through {@link ScrollPane#getContent()}, or
     * the highlight would silently disappear after any board change.</p>
     */
    private void markCurrentCard(Node node) {
        if (node.getUserData() instanceof CardId id) {
            node.getStyleClass().removeAll("card-current");
            if (id.equals(cardFocused)) {
                node.getStyleClass().add("card-current");
            }
            return;
        }
        if (node instanceof ScrollPane scroll && scroll.getContent() != null) {
            markCurrentCard(scroll.getContent());
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                markCurrentCard(child);
            }
        }
    }

    /** Rebuilds the bulk action bar (after language/selection-mode changes). */
    private void rebuildSelectionBar() {
        if (selectionBar == null) {
            return; // not built yet (first refresh during construction)
        }
        java.util.List<Button> buttons = new java.util.ArrayList<>();
        if (selectionMode) {
            Button exit = toolButton("\u2715", "bulk.exit");
            exit.setOnAction(e -> exitSelectionMode());
            buttons.add(exit);

            Button labelButton = toolButton("\uD83C\uDFF7", "bulk.labels");
            labelButton.setOnAction(e -> onBulkLabels());

            Button colorButton = toolButton("\uD83C\uDFA8", "bulk.color");
            colorButton.setOnAction(e -> onBulkColor());

            // The ⬫ glyph alone is not recognizable, so the
            // process action also carries a text label — bulk
            // actions must be discoverable without hovering.
            Button processButton = toolButton("\u26AD", "bulk.process");
            processButton.setText("\u26AD " + i18n.text("card.process.label"));
            processButton.setOnAction(e -> onBulkProcess());

            Button moveButton = toolButton("\u27A1", "bulk.move");
            moveButton.setOnAction(e -> onBulkMove());

            Button removeButton = toolButton("\uD83D\uDDD1", "bulk.remove.action");
            removeButton.setOnAction(e -> onBulkRemove());

            Label count = new Label();
            count.getStyleClass().add("selection-count");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            selectionBar.getChildren().setAll(
                    exit, labelButton, colorButton, processButton, moveButton, removeButton,
                    spacer, count);
        } else {
            selectionBar.getChildren().setAll();
        }
        boolean visible = selectionMode;
        selectionBar.setVisible(visible);
        selectionBar.setManaged(visible);
        syncFilterBarVisibility();
        updateSelectionBarState();
    }

    /**
     * The filter bar and the selection bar alternate: in selection mode the
     * card filters are hidden, and they come back when the mode is left.
     */
    private void syncFilterBarVisibility() {
        if (filterBar == null) {
            return;
        }
        boolean visible = !selectionMode;
        filterBar.setVisible(visible);
        filterBar.setManaged(visible);
    }

    /** Enables actions only when at least one card is selected. */
    private void updateSelectionBarState() {
        if (selectionBar == null) {
            return;
        }
        Label count = (Label) selectionBar.getChildren().stream()
                .filter(node -> node instanceof Label)
                .findFirst().orElse(null);
        if (count == null) {
            return;
        }
        int selected = selectedCards.size();
        count.setText(i18n.text("bulk.selected.count", selected));
        boolean any = selected > 0;
        selectionBar.getChildren().stream()
                .filter(node -> node instanceof Button)
                .map(node -> (Button) node)
                .filter(button -> !"\u2715".equals(button.getText()))
                .forEach(button -> button.setDisable(!any));
    }

    // ------------------------------------------------------------------
    // Intents: bulk actions on the current selection
    // ------------------------------------------------------------------

    private void onBulkLabels() {
        applyBulkLabels(List.copyOf(selectedCards), true);
    }

    /**
     * Labels shortcut (default Ctrl+L, user request): the add/remove dialog
     * for the selected cards, or — with no selection — for the card the
     * arrow keys are focused on.
     */
    private void onLabelsForCurrentCards() {
        if (!selectedCards.isEmpty()) {
            applyBulkLabels(List.copyOf(selectedCards), true);
        } else if (cardFocused != null) {
            applyBulkLabels(List.of(cardFocused), false);
        } else {
            dialogs.info(i18n.text("labels.shortcut.no.card"));
        }
    }

    /**
     * Toolbar archive toggle (user request): targets the selected cards
     * first, else the card the arrow keys are focused on. If every target
     * is already archived the label is taken off (unarchive), otherwise it
     * is added to all of them in one undoable step.
     */
    private void onArchiveCurrentCards() {
        List<CardId> targets = new ArrayList<>();
        if (selectionMode && !selectedCards.isEmpty()) {
            targets.addAll(selectedCards);
        } else if (cardFocused != null) {
            targets.add(cardFocused);
        }
        if (targets.isEmpty()) {
            dialogs.info(i18n.text("toolbar.archive.no.card"));
            return;
        }
        boolean allArchived = targets.stream()
                .map(service.board()::findCard)
                .flatMap(java.util.Optional::stream)
                .allMatch(card -> card.hasLabelIgnoreCase(
                        com.personalkanban.domain.board.Card.LABEL_ARCHIVED));
        guarded(() -> {
            if (allArchived) {
                service.removeLabelsFromCards(targets,
                        List.of(com.personalkanban.domain.board.Card.LABEL_ARCHIVED));
            } else {
                service.addLabelsToCards(targets,
                        List.of(com.personalkanban.domain.board.Card.LABEL_ARCHIVED));
            }
            refresh();
        });
    }

    /** Shared body of the bulk bar button and the labels shortcut. */
    private void applyBulkLabels(List<CardId> ids, boolean fromSelectionMode) {
        if (ids.isEmpty()) {
            return;
        }
        dialogs.bulkLabelsDialog(ids.size(), globalLabelVocabulary())
                .ifPresent(form -> {
                    lastBulkLabelsAdd = form.add();
                    if (form.labels().isEmpty()) {
                        return; // nothing typed: keep the selection active
                    }
                    guarded(() -> {
                        if (lastBulkLabelsAdd) {
                            service.addLabelsToCards(ids, form.labels());
                        } else {
                            service.removeLabelsFromCards(ids, form.labels());
                        }
                        if (fromSelectionMode) {
                            exitSelectionMode();
                        } else {
                            refresh();
                        }
                    });
                });
    }

    private void onBulkColor() {
        List<CardId> ids = List.copyOf(selectedCards);
        if (dialogs.bulkColorDialog(ids.size())
                .map(c -> {
                    guarded(() -> {
                        service.recolorCards(ids, c);
                        exitSelectionMode();
                    });
                    return true;
                }).orElse(false)) {
            return;
        }
    }

    private void onBulkProcess() {
        List<CardId> ids = List.copyOf(selectedCards);
        // "Ninguno" (null) takes the cards out of their process
        // without touching their arrow relations — bulk actions
        // must never destroy links silently.
        if (dialogs.bulkProcessDialog(ids.size(), service.processes())
                .map(f -> {
                    guarded(() -> {
                        service.assignProcessToCards(ids, f.processId());
                        exitSelectionMode();
                    });
                    return true;
                }).orElse(false)) {
            return;
        }
    }

    private void onBulkMove() {
        List<CardId> ids = List.copyOf(selectedCards);
        var columns = service.board().columns();
        dialogs.bulkMoveDialog(columns, selectionColumn, ids.size()).ifPresent(target -> {
            try {
                service.moveCardsToColumn(ids, target);
            } catch (com.personalkanban.domain.exception.WipLimitBulkException e) {
                dialogs.error(i18n.text("bulk.move.wip", e.excess(), ids.size()));
                return;
            } catch (RuntimeException e) {
                dialogs.error(Dialogs.describeFailure(e));
                return;
            }
            dialogs.info(i18n.text("bulk.move.done", ids.size()));
            exitSelectionMode();
        });
    }

    private void onBulkRemove() {
        List<CardId> ids = List.copyOf(selectedCards);
        if (!dialogs.confirm(i18n.text("bulk.remove.confirm", ids.size()))) {
            return;
        }
        guarded(() -> {
            ids.forEach(this::trashAttachments); // files follow the card (undo can bring them back)
            service.removeCards(ids);
            exitSelectionMode();
        });
    }

    /**
     * Vocabulary for the filter's autocomplete: only labels in use on the
     * active board make sense as filter criteria (unlike the card dialogs,
     * which offer the whole global catalog).
     */
    private List<String> filterVocabulary() {
        return service.labelVocabulary();
    }

    /**
     * Autocomplete vocabulary for label fields: the global catalog (reserved
     * labels first), available on every board.
     */
    private List<String> globalLabelVocabulary() {
        return context.globalLabels().vocabularyForActiveBoard(List.of());
    }

    /** Localized label of a view mode; null = "follow the board default". */
    public String cardViewModeText(CardViewSettings.Mode mode) {
        return i18n.text(mode == null ? "cardview.mode.board" : cardViewTextKey(mode));
    }

    /** i18n key of a view mode. */
    private static String cardViewTextKey(CardViewSettings.Mode mode) {
        return switch (mode) {
            case TITLE_ONLY -> "cardview.mode.title";
            case TITLE_PREVIEW -> "cardview.mode.preview";
            case FULL -> "cardview.mode.full";
        };
    }

    /** i18n key of a per-card content choice (N-14). */
    static String cardContentTextKey(CardViewSettings.Content content) {
        return switch (content) {
            case DESCRIPTION -> "card.content.description";
            case CHECKLIST -> "card.content.checklist";
            case NOTES -> "card.content.notes";
        };
    }

    /** Marks the radio item matching the current board default. */
    private void syncCardViewMenu() {
        if (toolbarReference == null) {
            return;
        }
        toolbarReference.getChildren().stream()
                .filter(node -> node instanceof MenuButton)
                .map(node -> (MenuButton) node)
                .filter(menu -> "\u2637".equals(menu.getText()))
                .findFirst()
                .ifPresent(menu -> {
                    for (MenuItem item : menu.getItems()) {
                        if (item instanceof RadioMenuItem radio) {
                            radio.setSelected(radio.getUserData() == cardViewSettings.boardDefault());
                        }
                    }
                });
    }

    /** Builds the domain filter from the current UI state (Translator). */
    /**
     * Filters by one label when its chip is clicked; clicking the very same
     * label again clears the filter, so a chip works as a toggle.
     */
    void onFilterByLabel(String label) {
        if (labelFilterField == null || label == null || label.isBlank()) {
            return;
        }
        labelFilterField.setText(LabelChipFilter.toggle(labelFilterField.getText(), label));
        // Clicking a chip means "show me this label": drop any excluding or
        // no-labels mode and go back to the plain (implicit AND) filter.
        activeFilterMode = null;
        if (labelFilterMode != null) {
            labelFilterMode.setValue(null);
            labelFilterField.setDisable(false);
        }
    }

    /** Applies a label-filter mode chosen in the filter bar. */
    void onFilterModeChanged(LabelFilterMode mode) {
        activeFilterMode = mode;
        // «Sin etiquetas» filters by the absence of labels, so the text field
        // takes no part: grey it out to say so visibly.
        if (labelFilterField != null) {
            labelFilterField.setDisable(mode == LabelFilterMode.NO_LABELS);
        }
        refresh();
    }

    private LabelFilter currentLabelFilter() {
        if (activeFilterMode == LabelFilterMode.NO_LABELS) {
            return LabelFilter.withoutLabels();
        }
        var labels = Dialogs.parseLabels(activeFilterLabels);
        if (labels.isEmpty()) {
            return LabelFilter.none();
        }
        LabelFilter.Mode mode = switch (activeFilterMode == null
                ? LabelFilterMode.ALL
                : activeFilterMode) {
            case ALL, NO_LABELS -> LabelFilter.Mode.ALL;
            case ANY -> LabelFilter.Mode.ANY;
            case NOT -> LabelFilter.Mode.EXCLUDE;
        };
        return new LabelFilter(labels, mode);
    }

    /** Quick-flag filter (★/!): ALL mode so both active together require both. */
    private LabelFilter currentQuickFlagFilter() {
        return quickFlagFilters.isEmpty()
                ? LabelFilter.none()
                : new LabelFilter(List.copyOf(quickFlagFilters), LabelFilter.Mode.ALL);
    }

    private MenuBar buildMenuBar() {
        databaseMenu = new Menu(i18n.text("menu.database"));
        rebuildDatabaseMenu();

        boardMenu = new Menu();
        boardMenu.textProperty().set(i18n.text("menu.boards"));
        rebuildBoardMenu();

        Menu languageMenu = new Menu(i18n.text("menu.language"));
        for (Locale locale : I18n.SUPPORTED) {
            MenuItem item = new MenuItem(locale.getDisplayLanguage(locale) + " (" + locale.getLanguage() + ")");
            item.setOnAction(e -> onSwitchLanguage(locale));
            languageMenu.getItems().add(item);
        }
        // Session 4: File (renamed from "Base de datos"), Boards, Processes,
        // Language, Help — Preferences and the shortcuts help live in their
        // natural menus.
        // Session 13 (user request): a "Ver" menu, so both view modes are
        // reachable with the keyboard (Alt opens the menu bar) and each one
        // carries its own accelerator.
        viewMenu = new Menu(i18n.text("menu.view"));
        rebuildViewMenu();
        processMenu = new Menu(i18n.text("menu.processes"));
        rebuildProcessMenu();
        // Global labels: the vocabulary is shared by every board, so its
        // manager gets its own menu (between Processes and Language).
        labelsMenu = new Menu(i18n.text("menu.labels"));
        rebuildLabelsMenu();
        helpMenu = new Menu(i18n.text("menu.help"));
        rebuildHelpMenu();
        return new MenuBar(databaseMenu, boardMenu, viewMenu, processMenu,
                labelsMenu, languageMenu, helpMenu);
    }

    private void rebuildDatabaseMenu() {
        if (databaseMenu == null) {
            return;
        }
        MenuItem currentFile = new MenuItem(i18n.text("db.current") + " "
                + context.databasePath());
        currentFile.setDisable(true);
        // Online sync: status line (so the user sees at a glance
        // whether this database syncs) and the configuration entry,
        // which holds everything sync-related (URL, key, test,
        // remote catalog).
        MenuItem syncStatus = new MenuItem(
                i18n.text("sync.menu.status") + " " + syncStatusText());
        syncStatus.setDisable(true);
        databaseMenu.getItems().setAll(
                itemOf("file.save", this::onSaveNow),
                itemOf("prefs.title", this::onShowPreferences),
                itemOf("cleanup.attachments", this::onCleanupAttachments),
                new SeparatorMenuItem(),
                syncStatus,
                itemOf("sync.now", this::onSyncNow),
                itemOf("sync.config.title", this::onShowSyncConfig),
                new SeparatorMenuItem(),
                itemOf("db.new", this::onNewDatabase),
                itemOf("db.open", this::onOpenDatabase),
                new SeparatorMenuItem());

        MenuItem recentHeader = new MenuItem(i18n.text("db.recent"));
        recentHeader.setDisable(true);
        databaseMenu.getItems().add(recentHeader);
        for (java.nio.file.Path recent : context.recentDatabases()) {
            boolean current = isCurrentDatabase(recent);
            MenuItem recentItem = new MenuItem((current ? "\u25CF " : "\u25CB ") + recent);
            recentItem.setDisable(current);
            recentItem.setOnAction(e -> switchDatabase(() -> context.openDatabase(recent)));
            databaseMenu.getItems().add(recentItem);
        }

        databaseMenu.getItems().addAll(new SeparatorMenuItem(), currentFile,
                new SeparatorMenuItem(), itemOf("file.exit", this::onExit));
    }

    private void rebuildBoardMenu() {
        if (boardMenu == null) {
            return;
        }
        boardMenu.getItems().clear();
        BoardId activeId = service.activeBoardId();
        List<BoardDescriptor> boards = service.boards();
        for (BoardDescriptor board : boards) {
            String marker = board.id().equals(activeId) ? "\u25CF " : "\u25CB ";
            MenuItem item = new MenuItem(marker + board.name());
            item.setOnAction(e -> onOpenBoard(board.id()));
            boardMenu.getItems().add(item);
        }
        boardMenu.getItems().addAll(
                new SeparatorMenuItem(),
                itemOf("board.new", this::onNewBoard),
                itemOf("board.rename", this::onRenameBoard),
                itemOf("board.delete", this::onDeleteBoard),
                new SeparatorMenuItem(),
                itemOf("board.add.column", this::onAddColumn),
                new SeparatorMenuItem(),
                itemOf("board.save.template", this::onSaveBoardTemplate),
                templatesMenu(),
                new SeparatorMenuItem(),
                itemOf("cleanup.archived", this::onPurgeArchived),
                new SeparatorMenuItem(),
                itemOf("board.export", this::onExportBoard),
                itemOf("board.export.pdf", this::onExportBoardPdf),
                itemOf("board.import", this::onImportBoard),
                new SeparatorMenuItem(),
                itemOf("view.conflicts.menu", this::onShowConflicts));
    }

    /** Rebuilds the (single-item) global label manager menu. */
    private void rebuildLabelsMenu() {
        if (labelsMenu == null) {
            return;
        }
        labelsMenu.getItems().setAll(itemOf("labels.manage", this::onManageLabels));
    }

    /** Opens the global label manager (add / rename / remove across boards). */
    private void onManageLabels() {
        new LabelManagerDialog(i18n, context.globalLabels(), service, dialogs, this::refresh).show();
    }

    /**
     * Reviews the conflict copies the sync layer preserved for the active
     * board (design §4.1): list on the left, field-level manifest on the
     * right. Empty list is a valid "nothing was ever lost" state.
     */
    private void onShowConflicts() {
        Path dataDirectory = context.databasePath().getParent();
        com.personalkanban.application.CurrentConflicts conflicts =
                new com.personalkanban.application.CurrentConflicts(dataDirectory);
        java.util.List<com.personalkanban.application.CurrentConflicts.ConflictEntry> entries =
                conflicts.listFor(service.activeBoardId());
        new ConflictReviewDialog(null, i18n, dataDirectory, entries).showAndWait();
    }

    /** Rebuilds the process management menu (creation order, like boards). */
    private void rebuildProcessMenu() {
        if (processMenu == null) {
            return;
        }
        processMenu.getItems().setAll(
                itemOf("process.new", this::onNewProcess));
        // Process filter (user request): reachable from the menu — and
        // therefore from the keyboard (Alt opens the menu bar) — with the
        // two special states on accelerators. The active filter is marked
        // like the active board in the boards menu.
        MenuItem filterHeader = new MenuItem(i18n.text("menu.process.filter"));
        filterHeader.setDisable(true);
        MenuItem allProcesses = new MenuItem(
                (activeProcessFilter == null && !processFilterNone ? "\u25CF " : "\u25CB ")
                        + i18n.text("filter.process.all"));
        allProcesses.setAccelerator(
                javafx.scene.input.KeyCombination.valueOf("Shortcut+Shift+P"));
        allProcesses.setOnAction(e -> onFilterProcessAll());
        MenuItem noProcess = new MenuItem(
                (processFilterNone ? "\u25CF " : "\u25CB ") + i18n.text("filter.process.none"));
        noProcess.setAccelerator(
                javafx.scene.input.KeyCombination.valueOf("Shortcut+Shift+N"));
        noProcess.setOnAction(e -> onFilterProcessNone());
        processMenu.getItems().addAll(filterHeader, allProcesses, noProcess,
                new SeparatorMenuItem());
        for (com.personalkanban.domain.board.Process process : service.processes()) {
            boolean filtered = activeProcessFilter != null
                    && activeProcessFilter.equals(process.id());
            MenuItem pick = new MenuItem((filtered ? "\u25CF " : "\u25CB ") + process.name());
            pick.setOnAction(e -> onFilterProcess(process.id()));
            MenuItem rename = itemOf("process.rename", () -> onRenameProcess(process.id()));
            MenuItem delete = itemOf("process.delete", () -> onDeleteProcess(process.id()));
            processMenu.getItems().addAll(new SeparatorMenuItem(),
                    pick, rename, delete);
        }
    }

    /**
     * "Ver" menu (session 13, user request): the two view modes with their own
     * accelerator, so the mode can be chosen with the keyboard — either the
     * accelerator directly, or the menu itself (Alt, arrows, Enter). The active
     * mode carries the radio dot, like the active board / process filter.
     */
    private void rebuildViewMenu() {
        if (viewMenu == null) {
            return;
        }
        RadioMenuItem boardView = new RadioMenuItem(i18n.text("view.board"));
        boardView.setUserData(Boolean.FALSE);
        boardView.setAccelerator(
                javafx.scene.input.KeyCombination.valueOf("Shortcut+Shift+1"));
        boardView.setOnAction(e -> onSelectView(false));
        RadioMenuItem processViewItem = new RadioMenuItem(i18n.text("view.processes"));
        processViewItem.setUserData(Boolean.TRUE);
        processViewItem.setAccelerator(
                javafx.scene.input.KeyCombination.valueOf("Shortcut+Shift+2"));
        processViewItem.setOnAction(e -> onSelectView(true));
        // Session 14 (user request B1): the time report is a way of LOOKING at
        // the board, so it lives in the "Ver" menu next to the view modes —
        // no new top-level menu for a single entry.
        viewMenu.getItems().setAll(boardView, processViewItem,
                new SeparatorMenuItem(),
                itemOf("report.time", this::onShowTimeReport),
                itemOf("search.menu", this::onGlobalSearch));
        syncViewMenu();
    }

    /** Marks the radio item of the view mode currently on screen. */
    private void syncViewMenu() {
        if (viewMenu == null) {
            return;
        }
        for (MenuItem item : viewMenu.getItems()) {
            if (item instanceof RadioMenuItem radio
                    && radio.getUserData() instanceof Boolean wantsProcesses) {
                radio.setSelected(wantsProcesses == processView);
            }
        }
    }

    /** Rebuilds the help menu contents (shortcuts reference lives here). */
    private void rebuildHelpMenu() {
        if (helpMenu == null) {
            return;
        }
        helpMenu.getItems().setAll(
                itemOf("help.shortcuts", this::onShowShortcutsHelp),
                itemOf("help.edit.shortcuts", this::onShowShortcutsSettings));
    }

    private MenuItem itemOf(String textKey, Runnable action) {
        MenuItem item = new MenuItem(i18n.text(textKey));
        item.setOnAction(e -> action.run());
        return item;
    }

    // ------------------------------------------------------------------
    // Intents: database files (create / open / transport)
    // ------------------------------------------------------------------

    /** Rewires the controller after the underlying database changed. */
    private void rebind() {
        this.service = context.boardService();
        this.i18n = new I18n(context.savedLocale());
        this.themeManager = context.themeManager();
        this.undoRedo = new UndoRedoController(service, i18n, this::refresh);
        this.dialogs = new Dialogs(i18n);
        // A new database carries its own ui.shortcuts value.
        this.shortcuts = service.globalShortcuts();
        rebuildAll();
        applyThemeOverride();
    }

    private void onNewDatabase() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.text("db.new"));
        chooser.setInitialFileName("kanban.db");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("SQLite database", "*.db", "*.sqlite"));
        java.io.File file = chooser.showSaveDialog(window());
        if (file == null) {
            return;
        }
        if (isCurrentDatabase(file.toPath())) {
            dialogs.info(i18n.text("db.same.file"));
            return;
        }
        if (file.exists() && !dialogs.confirm(i18n.text("db.overwrite.confirm"))) {
            return;
        }
        switchDatabase(() -> context.openDatabase(file.toPath()));
    }

    private void onOpenDatabase() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.text("db.open"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("SQLite database", "*.db", "*.sqlite"));
        java.io.File file = chooser.showOpenDialog(window());
        if (file == null) {
            return;
        }
        if (isCurrentDatabase(file.toPath())) {
            dialogs.info(i18n.text("db.same.file"));
            return;
        }
        switchDatabase(() -> context.openDatabase(file.toPath()));
    }

    private boolean isCurrentDatabase(java.nio.file.Path candidate) {
        return candidate.toAbsolutePath().normalize().equals(context.databasePath());
    }

    /** Runs a database switch, rebinds on success, shows the error on failure. */
    private void switchDatabase(Runnable openAction) {
        try {
            openAction.run();
            rebind();
        } catch (RuntimeException e) {
            dialogs.error(i18n.text("db.open.failed") + "\n" + Dialogs.describeFailure(e));
        }
    }

    private javafx.stage.Window window() {
        return root.getScene() == null ? null : root.getScene().getWindow();
    }

    // ------------------------------------------------------------------
    // Intents: boards
    // ------------------------------------------------------------------

    private void onOpenBoard(BoardId boardId) {
        service.openBoard(boardId);
        // Session 13: the current card belongs to the board that was on
        // screen; opening another board starts with none (exactly one card
        // can be current, and switching boards is not a selection).
        cardFocused = null;
        rebuildAll();
    }

    /** Toggles a column between expanded and collapsed (persisted per board). */
    public void onToggleColumnCollapsed(ColumnId columnId) {
        java.util.Set<String> collapsed = new java.util.LinkedHashSet<>(collapsedColumns);
        String id = columnId.value();
        if (!collapsed.remove(id)) {
            collapsed.add(id);
        }
        service.setCollapsedColumns(service.activeBoardId(), collapsed);
        collapsedColumns = java.util.Set.copyOf(collapsed);
        refresh();
    }

    /** Quick flag (★ Importante / ! Urgente) toggle on one card. */
    public void onToggleCardLabel(com.personalkanban.domain.board.CardId cardId, String label) {
        guarded(() -> {
            service.toggleCardLabel(cardId, label);
            refresh(); // without this the click "did nothing" on screen
        });
    }

    /** Persists a column width after a drag-resize (per board). */
    public void onColumnWidthChanged(String columnId, int width) {
        java.util.Map<String, Integer> widths = new java.util.LinkedHashMap<>(columnWidths);
        widths.put(columnId, width);
        columnWidths = java.util.Map.copyOf(widths);
        service.setColumnWidths(service.activeBoardId(), widths);
        // No refresh: the live drag already left the column at its new width.
    }

    private void onNewBoard() {
        Optional<String> name = dialogs.promptText(i18n.text("board.new"), "");
        name.flatMap(this::validatedBoardName).ifPresent(n -> {
            // The user's own templates are offered next to the fixed ones
            // (T14 · B5.1).
            List<String> templateNames = service.templates().stream()
                    .map(BoardTemplate::name)
                    .toList();
            Optional<Dialogs.NewBoardChoice> choice = dialogs.newBoardColumnsDialog(templateNames);
            if (choice.isEmpty()) {
                return; // cancelled: create nothing
            }
            Dialogs.NewBoardChoice picked = choice.get();
            if (picked.templateName() != null) {
                service.templates().stream()
                        .filter(template -> template.name().equals(picked.templateName()))
                        .findFirst()
                        .ifPresent(template -> {
                            service.createBoardFromTemplate(template, n);
                            rebuildAll();
                            dialogs.info(i18n.text("board.columns.created",
                                    template.columns().size(), n));
                        });
                return;
            }
            List<String> columnTitles;
            if (picked.kind() == Dialogs.NewBoardColumns.STANDARD) {
                columnTitles = List.of(
                        i18n.text("board.kanban.todo"),
                        i18n.text("board.kanban.doing"),
                        i18n.text("board.kanban.done"));
            } else if (picked.kind() == Dialogs.NewBoardColumns.CUSTOM) {
                String columnsAnswer = dialogs.promptText(
                        i18n.text("board.columns.header"),
                        i18n.text("board.columns.prompt"), "").orElse("");
                columnTitles = Dialogs.parseNewBoardColumns(
                        columnsAnswer, index -> i18n.text("board.column.default", index));
                if (columnTitles == null) {
                    dialogs.info(i18n.text("board.columns.invalid",
                            Dialogs.MAX_NEW_BOARD_COLUMNS));
                    return; // board not created: ask again from the start
                }
            } else {
                columnTitles = List.of();
            }
            BoardId id = service.createBoard(n);
            service.openBoard(id);
            java.util.List<ColumnId> createdColumns = new java.util.ArrayList<>();
            for (String title : columnTitles) {
                createdColumns.add(
                        service.addColumn(title, "", BoardColor.DEFAULT, WipLimit.unlimited()));
            }
            if (picked.kind() == Dialogs.NewBoardColumns.STANDARD
                    && !createdColumns.isEmpty()) {
                // The standard template ends with "Hecho": that is
                // the board's single "finalizado" column (user
                // request; the service keeps the one-column invariant).
                service.setColumnDone(createdColumns.getLast(), true);
            }
            rebuildAll();
            dialogs.info(i18n.text("board.columns.created", columnTitles.size(), n));
        });
    }

    /** Submenu listing the user's saved board templates (T14 · B5.1). */
    private Menu templatesMenu() {
        Menu menu = new Menu(i18n.text("board.templates"));
        List<BoardTemplate> templates = service.templates();
        if (templates.isEmpty()) {
            MenuItem none = new MenuItem(i18n.text("template.empty"));
            none.setDisable(true);
            menu.getItems().add(none);
            return menu;
        }
        for (BoardTemplate template : templates) {
            Menu entry = new Menu(template.name());
            MenuItem apply = new MenuItem(i18n.text("template.apply"));
            apply.setOnAction(e -> onApplyTemplate(template));
            MenuItem delete = new MenuItem(i18n.text("template.delete"));
            delete.setOnAction(e -> onDeleteTemplate(template));
            entry.getItems().setAll(apply, delete);
            menu.getItems().add(entry);
        }
        return menu;
    }

    /** Saves the active board's structure (no cards) as a named template. */
    private void onSaveBoardTemplate() {
        BoardDescriptor active = service.boards().stream()
                .filter(descriptor -> descriptor.id().equals(service.activeBoardId()))
                .findFirst()
                .orElseThrow();
        Optional<String> name = dialogs.promptText(i18n.text("board.save.template"), active.name());
        name.flatMap(this::validatedBoardName).ifPresent(templateName -> {
            if (service.hasTemplate(templateName)
                    && !dialogs.confirm(i18n.text("template.name.exists"))) {
                return;
            }
            guarded(() -> {
                service.saveAsTemplate(templateName);
                rebuildAll();
                dialogs.info(i18n.text("template.saved", templateName));
            });
        });
    }

    /** Creates a brand-new board from a saved template. */
    private void onApplyTemplate(BoardTemplate template) {
        Optional<String> name = dialogs.promptText(i18n.text("template.apply"), template.name());
        name.flatMap(this::validatedBoardName).ifPresent(boardName -> guarded(() -> {
            service.createBoardFromTemplate(template, boardName);
            rebuildAll();
            dialogs.info(i18n.text("board.columns.created", template.columns().size(), boardName));
        }));
    }

    /** Deletes a saved template (the boards already created from it stay). */
    private void onDeleteTemplate(BoardTemplate template) {
        if (!dialogs.confirm(i18n.text("template.delete") + ": " + template.name())) {
            return;
        }
        service.removeTemplate(template.name());
        rebuildAll();
    }

    /**
     * Deletes every archived card of every board (T14 · B5.2), sending their
     * attachments to the trash. Each affected board gets its own undo step.
     */
    private void onPurgeArchived() {
        int count = service.archivedCardCount();
        if (count == 0) {
            dialogs.info(i18n.text("cleanup.archived.none"));
            return;
        }
        if (!dialogs.confirm(i18n.text("cleanup.archived.confirm", count))) {
            return;
        }
        service.boards().forEach(descriptor ->
                service.archivedCardsOf(descriptor.id()).forEach(this::trashAttachments));
        guarded(() -> {
            int removed = service.purgeArchivedCards();
            rebuildAll();
            dialogs.info(i18n.text("cleanup.archived.done", removed));
        });
    }

    private void onRenameBoard() {
        BoardDescriptor active = service.boards().stream()
                .filter(descriptor -> descriptor.id().equals(service.activeBoardId()))
                .findFirst()
                .orElseThrow();
        Optional<String> name = dialogs.promptText(i18n.text("board.rename"), active.name());
        name.flatMap(this::validatedBoardName).ifPresent(n -> {
            service.renameBoard(active.id(), n);
            rebuildAll();
        });
    }

    private void onDeleteBoard() {
        BoardId activeId = service.activeBoardId();
        if (service.boards().size() <= 1) {
            dialogs.info(i18n.text("board.delete.last"));
            return;
        }
        if (dialogs.confirm(i18n.text("board.delete.confirm"))) {
            try {
                // Files of every card of the board follow it to the trash.
                service.columnsOf(activeId).forEach(column -> column.cards().forEach(card ->
                        trashAttachments(card.id())));
                service.deleteBoard(activeId);
            } catch (RuntimeException e) {
                // Never leave the user wondering: surface the root cause
                // (e.g. a DB lock or a missing row) instead of failing silently.
                dialogs.error(Dialogs.describeFailure(e));
                return;
            }
            rebuildAll();
        }
    }

    private Optional<String> validatedBoardName(String raw) {
        if (raw == null || raw.isBlank()) {
            dialogs.info(i18n.text("board.name.required"));
            return Optional.empty();
        }
        return Optional.of(raw.strip());
    }

    // ------------------------------------------------------------------
    // Intents: export / import
    // ------------------------------------------------------------------

    /**
     * Where file choosers start: the last folder used for export/import
     * (shared memory, machine-local), or — only when it is unknown or no
     * longer exists (e.g. an unplugged USB drive) — the user's home folder.
     * On every OS including Windows the home folder is a real, always
     * navigable directory (Documents lives under it), unlike "This PC",
     * which is a virtual location without a filesystem path.
     */
    private java.io.File initialDirectory() {
        Path last = context.lastTransferDirectory().orElse(null);
        if (last != null) {
            return last.toFile();
        }
        Path home = Path.of(System.getProperty("user.home", "."));
        return Files.isDirectory(home) ? home.toFile() : null;
    }

    private void onExportBoard() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.text("board.export"));
        chooser.setInitialFileName("board.json");
        chooser.setInitialDirectory(initialDirectory());
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Personal Kanban board", "*.json"));
        java.io.File file = chooser.showSaveDialog(root.getScene() == null ? null : root.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            service.exportBoard(file.toPath());
            context.rememberTransferDirectory(file.getParentFile().toPath());
        } catch (RuntimeException e) {
            dialogs.error(Dialogs.describeFailure(e));
        }
    }

    private void onImportBoard() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.text("board.import"));
        chooser.setInitialDirectory(initialDirectory());
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Personal Kanban board", "*.json"));
        java.io.File file = chooser.showOpenDialog(root.getScene() == null ? null : root.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            BoardId imported = service.importBoard(file.toPath());
            context.rememberTransferDirectory(file.getParentFile().toPath());
            service.openBoard(imported);
            rebuildAll();
        } catch (RuntimeException e) {
            dialogs.error(Dialogs.describeFailure(e));
        }
    }

    /** Exports the currently visible board (all columns) as a paginated PDF snapshot. */
    private void onExportBoardPdf() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.text("board.export.pdf"));
        chooser.setInitialFileName("board.pdf");
        chooser.setInitialDirectory(initialDirectory());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        java.io.File file = chooser.showSaveDialog(window());
        if (file == null) {
            return;
        }
        try {
            com.personalkanban.ui.pdf.BoardPdfExporter.export(columnsRow, file.toPath());
            context.rememberTransferDirectory(file.getParentFile().toPath());
        } catch (java.io.IOException e) {
            dialogs.error(Dialogs.describeFailure(new RuntimeException(e.getMessage(), e)));
        } catch (RuntimeException e) {
            dialogs.error(Dialogs.describeFailure(e));
        }
    }

    // ------------------------------------------------------------------
    // Intents: settings & language
    // ------------------------------------------------------------------

    private void onToggleDarkMode() {
        themeManager.toggle();
        context.saveTheme(themeManager.theme());
        applyThemeOverride();
    }

    /**
     * Session 11 (S11-2): (re)generates the user color-scheme override for
     * the active theme and re-applies the full stylesheet list to the scene.
     * A write failure only means the built-in theme keeps rendering.
     */
    private void applyThemeOverride() {
        ThemeColors colors = service.themeColors();
        boolean dark = themeManager.isDark();
        themeManager.overrideStylesheet(ThemeOverride.write(
                com.personalkanban.Main.dataDirectory(), colors, dark));
        Scene scene = root.getScene();
        if (scene != null) {
            scene.getStylesheets().setAll(themeManager.stylesheets());
        }
    }

    private void onSwitchLanguage(Locale locale) {
        context.saveLocale(locale);
        i18n.setLocale(locale);
        rebuildAll(); // full rebuild re-translates every text, tooltips included
    }

    // ------------------------------------------------------------------
    // Intents: shortcuts help, exit, preferences (session 4)
    // ------------------------------------------------------------------

    /** Opens (or focuses) the fixed-shortcuts reference window (F1). */
    private void onShowShortcutsHelp() {
        ShortcutsHelpWindow.show(i18n, themeManager.stylesheets());
    }

    /**
     * Searches every board of the catalogue (session 14, B2) and opens the hit
     * the user picks: the board comes on screen and the card becomes the
     * "current" one, so the arrow keys and the card shortcuts continue from it.
     */
    private void onGlobalSearch() {
        new GlobalSearchDialog(new com.personalkanban.application.GlobalSearch(service), i18n)
                .show()
                .ifPresent(hit -> focusCardInBoard(hit.boardId(), hit.card().id()));
    }

    /**
     * Brings a card of any board on screen as the current one. The board is
     * opened only when it is not the one already displayed; filters are
     * cleared (a filtered-out card cannot be focused) and an archived hit also
     * switches the archived view on, so "open this result" never lands on an
     * empty screen.
     */
    public void focusCardInBoard(BoardId boardId, CardId cardId) {
        boolean boardChanged = !boardId.equals(service.activeBoardId());
        if (boardChanged) {
            service.openBoard(boardId);
            cardFocused = null;
        }
        boolean needsRebuild = clearFilters();
        if (!showArchived && isArchived(cardId)) {
            showArchived = true;
            needsRebuild = true;
        }
        if (boardChanged || needsRebuild) {
            rebuildAll();
        }
        // kanbanFocusCard skips the work when the card is already current, and
        // this entry point must always land on the hit (the search dialog may
        // have opened it twice).
        if (cardId.equals(cardFocused)) {
            cardFocused = null;
        }
        kanbanFocusCard(cardId);
        // A rebuilt column exposes its cards to the node walk only after the
        // next layout pass, so the focus and the scroll are retried one pulse
        // later; the "current" highlight is already applied.
        javafx.application.Platform.runLater(() -> {
            if (cardId.equals(cardFocused)) {
                requestFocusOnCardNode(cardId);
            }
        });
    }

    private boolean isArchived(CardId cardId) {
        return service.board().findCard(cardId)
                .map(card -> card.hasLabelIgnoreCase(
                        com.personalkanban.domain.board.Card.LABEL_ARCHIVED))
                .orElse(false);
    }

    /** Opens the (non-modal) time report of the board on screen (B1). */
    private void onShowTimeReport() {
        TimeReportWindow.show(service, i18n, themeManager.stylesheets());
    }

    /** Opens the editable keyboard-shortcuts dialog (File → Keyboard shortcuts). */
    private void onShowShortcutsSettings() {
        KeyboardShortcutsDialog dialog = new KeyboardShortcutsDialog(i18n, service, shortcuts);
        dialog.show();
    }

    /** Closes the app cleanly (File → Exit / Ctrl+Q / window X). */
    private void onExit() {
        shutdown();
        javafx.application.Platform.exit();
    }

    /**
     * Clean shutdown (user request): every mutation already commits
     * instantly (rollback journal mode), so closing the database is
     * all the persistence there is. Also wired to the window close
     * (X) via {@code Main}.
     */
    public void shutdown() {
        context.close();
    }

    /** Ctrl+S / menu "Guardar": confirms the state; changes were already saved. */
    public void onSaveNow() {
        dialogs.info(i18n.text("file.save.confirm"));
    }

    /** Whether this database syncs, and with which server. */
    private String syncStatusText() {
        return context.syncServerUrl().orElseGet(() -> i18n.text("sync.off"));
    }

    /**
     * Reconciles the active board with the server on a background thread, so
     * a slow server never freezes the UI. The outcome is reported as a
     * discreet notice; conflicts additionally point at the preserved copy.
     */
    private void onSyncNow() {
        java.util.Optional<com.personalkanban.application.sync.SyncService> syncService =
                context.syncService();
        if (syncService.isEmpty()) {
            dialogs.info(i18n.text("sync.test.noconfig"));
            return;
        }
        showSyncNotice(i18n.text("sync.running"), 0);
        javafx.concurrent.Task<com.personalkanban.application.sync.SyncReport> task =
                new javafx.concurrent.Task<>() {
                    @Override
                    protected com.personalkanban.application.sync.SyncReport call() {
                        return syncService.get().syncActiveBoard();
                    }
                };
        task.setOnSucceeded(event -> {
            refresh();
            rebuildDatabaseMenu();
            com.personalkanban.application.sync.SyncReport report = task.getValue();
            showSyncNotice(syncNoticeText(report), report.hasConflicts() ? 12 : 5);
        });
        task.setOnFailed(event ->
                showSyncNotice(syncFailureText(task.getException()), 12));
        new Thread(task, "sync-run").start();
    }

    /** Localized, one-line summary of a finished sync. */
    private String syncNoticeText(com.personalkanban.application.sync.SyncReport report) {
        String base = i18n.text(switch (report.action()) {
            case PUSHED_NEW -> "sync.done.pushed_new";
            case PUSHED_LOCAL -> "sync.done.pushed_local";
            case FAST_FORWARD -> "sync.done.pull";
            case MERGED -> "sync.done.merged";
        });
        if (!report.hasConflicts()) {
            return base;
        }
        String copy = report.conflictCopyReference()
                .map(path -> " " + i18n.text("sync.conflict.copy", path))
                .orElse("");
        return base + " " + i18n.text("sync.conflict.notice", report.conflicts().size()) + copy;
    }

    /** Maps a sync failure to the localized, actionable message. */
    private String syncFailureText(Throwable failure) {
        if (failure instanceof com.personalkanban.application.sync.SyncException sync) {
            return switch (sync.kind()) {
                case NETWORK -> i18n.text("sync.error.network", failure.getMessage());
                case UNAUTHORIZED -> i18n.text("sync.error.unauthorized");
                case BAD_REQUEST -> i18n.text("sync.error.bad_request");
                case TOO_LARGE -> i18n.text("sync.error.too_large");
                case SERVER -> i18n.text("sync.error.server", sync.statusCode());
            };
        }
        String message = failure == null || failure.getMessage() == null
                ? String.valueOf(failure)
                : failure.getMessage();
        return i18n.text("sync.error.network", message);
    }

    /**
     * Shows the non-modal sync notice; {@code seconds <= 0} keeps it visible
     * until the next sync (used while a sync is running).
     */
    private void showSyncNotice(String message, int seconds) {
        if (syncNotice == null) {
            return;
        }
        syncNotice.setText(message);
        syncNotice.setVisible(true);
        syncNotice.setManaged(true);
        if (seconds > 0) {
            javafx.animation.PauseTransition hide =
                    new javafx.animation.PauseTransition(javafx.util.Duration.seconds(seconds));
            hide.setOnFinished(event -> {
                syncNotice.setVisible(false);
                syncNotice.setManaged(false);
            });
            hide.play();
        }
    }

    /** Online-sync configuration: server URL, API key, connection test, catalog. */
    private void onShowSyncConfig() {
        new SyncConfigDialog(i18n, context).show()
                .ifPresent(settings -> {
                    context.configureSync(settings.url(), settings.key());
                    rebuildDatabaseMenu(); // keep the status line current
                });
    }

    /** Preferences: color scheme, priority and label colors. */
    private void onShowPreferences() {
        new PreferencesDialog(i18n, service, themeManager.isDark()).show();
        // The dialog may have changed the scheme: repaint with the new colors.
        applyThemeOverride();
        refresh();
    }

    // ------------------------------------------------------------------
    // Intents: processes (session 4.6, menu + filter)
    // ------------------------------------------------------------------

    private void onNewProcess() {
        dialogs.promptText(i18n.text("process.new"), "").ifPresent(name ->
                guarded(() -> {
                    if (name == null || name.isBlank()) {
                        dialogs.info(i18n.text("process.name.required"));
                        return;
                    }
                    service.addProcess(name);
                    refresh(); // rebuilds the process menu too
                }));
    }

    private void onRenameProcess(com.personalkanban.domain.board.ProcessId processId) {
        var process = service.processes().stream()
                .filter(p -> p.id().equals(processId))
                .findFirst();
        if (process.isEmpty()) {
            return;
        }
        dialogs.promptText(i18n.text("process.rename"), process.get().name()).ifPresent(name ->
                guarded(() -> {
                    service.renameProcess(processId, name);
                    refresh();
                }));
    }

    private void onDeleteProcess(com.personalkanban.domain.board.ProcessId processId) {
        if (dialogs.confirm(i18n.text("process.delete.confirm"))) {
            if (activeProcessFilter != null && activeProcessFilter.equals(processId)) {
                activeProcessFilter = null; // filter would dangle
                processFilterNone = false;
            }
            guarded(() -> {
                service.removeProcess(processId);
                refresh();
            });
        }
    }

    /** Package-visible lookup for the card dialog's process combo. */
    List<com.personalkanban.domain.board.Process> currentProcesses() {
        return service.processes();
    }

    /** The process the cards are currently filtered by (null = all). */
    com.personalkanban.domain.board.ProcessId activeProcessFilter() {
        return activeProcessFilter;
    }

    /** True when the card passes the active process filter (null = all pass). */
    boolean matchesProcessFilter(com.personalkanban.domain.board.Card card) {
        // Session 10 (board cleanup): every filter chain (rendering AND the
        // keyboard navigator) passes through here, so hiding archived cards
        // in this one place keeps views, counts and navigation consistent.
        if (!showArchived && card.hasLabelIgnoreCase(
                com.personalkanban.domain.board.Card.LABEL_ARCHIVED)) {
            return false;
        }
        if (activeProcessFilter != null) {
            return activeProcessFilter.equals(card.processId());
        }
        return !processFilterNone || card.processId() == null;
    }

    /**
     * True when the card passes the active due-date filter (null = all pass).
     * Package-visible because the column renderer applies it too, next to
     * {@link #matchesProcessFilter(com.personalkanban.domain.board.Card)}: one
     * gate for both the drawn cards and the keyboard navigator.
     */
    boolean matchesDueFilter(com.personalkanban.domain.board.Card card) {
        return dueScope == null || DueDigest.matches(card, dueScope, LocalDate.now());
    }

    /**
     * Due-date filter intent (session 15, T5 = N-6): one scope at a time;
     * passing null clears the filter.
     */
    void onDueScope(DueDigest.Scope scope) {
        this.dueScope = scope;
        syncDueButtons();
        refresh();
    }

    /** Remembered vertical scroll of a column (0 = top). */
    double columnScrollOf(String columnId) {
        return columnScroll.getOrDefault(columnId, 0.0);
    }

    /**
     * Reads the scroll position of every column currently on screen, so the
     * rebuild that follows can put each one back where it was.
     */
    private void rememberColumnScroll() {
        if (columnsRow == null) {
            return;
        }
        for (Node child : columnsRow.getChildren()) {
            if (!(child.getUserData() instanceof ColumnId columnId)) {
                continue;
            }
            ScrollPane scroll = firstScrollPane(child);
            if (scroll != null) {
                columnScroll.put(columnId.value(), scroll.getVvalue());
            }
        }
    }

    private static ScrollPane firstScrollPane(Node node) {
        if (node instanceof ScrollPane scroll) {
            return scroll;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                ScrollPane found = firstScrollPane(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Keeps the three due toggles in sync with the chosen scope. */
    private void syncDueButtons() {
        dueButtons.forEach((scope, button) -> button.setSelected(scope == dueScope));
    }

    /** One of the three mutually exclusive due-date filter toggles. */
    private javafx.scene.control.ToggleButton dueScopeButton(String glyph, String tipKey,
                                                            DueDigest.Scope scope) {
        javafx.scene.control.ToggleButton button = new javafx.scene.control.ToggleButton(glyph);
        button.getStyleClass().addAll("tool-button", "filter-flag");
        button.setTooltip(new Tooltip(i18n.text(tipKey)));
        button.setFocusTraversable(false);
        button.setSelected(scope == dueScope);
        button.setOnAction(e -> onDueScope(button.isSelected() ? scope : null));
        dueButtons.put(scope, button);
        return button;
    }

    /**
     * The due-date reminder (session 15, T5/B3): how many cards are overdue or
     * due today, as a discreet band that takes the user to the matching filter
     * when clicked. Never modal, never interrupting; only the cards that the
     * current process/archived filter already shows are counted, so the numbers
     * match exactly what the filters will show.
     */
    private void updateDueNotice() {
        if (dueNotice == null) {
            return;
        }
        List<com.personalkanban.domain.board.Card> candidates = new ArrayList<>();
        for (BoardColumn column : service.board().columns()) {
            for (com.personalkanban.domain.board.Card card : column.cards()) {
                if (matchesProcessFilter(card)) {
                    candidates.add(card);
                }
            }
        }
        DueDigest.Counts counts = DueDigest.of(candidates, LocalDate.now()).counts();

        boolean show = !dueNoticeDismissed && dueScope == null && counts.anything();
        dueNotice.setVisible(show);
        dueNotice.setManaged(show);
        if (!show) {
            return;
        }
        List<String> parts = new ArrayList<>();
        if (counts.overdue() > 0) {
            parts.add(i18n.text("due.notice.overdue", counts.overdue()));
        }
        if (counts.dueToday() > 0) {
            parts.add(i18n.text("due.notice.today", counts.dueToday()));
        }
        dueNotice.setText(String.join("  \u00B7  ", parts));
        dueNotice.setTooltip(new Tooltip(i18n.text("due.notice.hint")));
        dueNoticeJump = counts.overdue() > 0 ? DueDigest.Scope.OVERDUE : DueDigest.Scope.TODAY;
    }

    private void onDueNoticeClicked() {
        dueNoticeDismissed = true;
        if (dueNoticeJump != null) {
            onDueScope(dueNoticeJump);
        }
    }

    /** Quick reschedule of one card from its menu (null clears the date). */
    public void onRescheduleCard(CardId cardId, LocalDate dueDate) {
        guarded(() -> {
            service.rescheduleCard(cardId, dueDate);
            refresh();
        });
    }

    /** Process filter intent: every card is visible again (no filter). */
    void onFilterProcessAll() {
        activeProcessFilter = null;
        processFilterNone = false;
        refresh();
    }

    /** Process filter intent: only the cards outside every process. */
    void onFilterProcessNone() {
        activeProcessFilter = null;
        processFilterNone = true;
        refresh();
    }

    /** Process filter intent: only the cards of the given process. */
    void onFilterProcess(com.personalkanban.domain.board.ProcessId processId) {
        activeProcessFilter = processId;
        processFilterNone = false;
        refresh();
    }

    /** Direct predecessors of a card, for the card-front badges. */
    int predecessorCountOf(com.personalkanban.domain.board.CardId cardId) {
        return service.board().incomingPredecessorsOf(cardId).size();
    }

    /** Direct successors of a card, for the card-front badges. */
    int successorCountOf(com.personalkanban.domain.board.CardId cardId) {
        return service.board().outgoingSuccessorsOf(cardId).size();
    }

    // ------------------------------------------------------------------
    // Intents: checklist, notes and card relations (session 4)
    // ------------------------------------------------------------------

    /** Package-visible card lookup for the non-modal detail window. */
    java.util.Optional<com.personalkanban.domain.board.Card> cardById(
            com.personalkanban.domain.board.CardId cardId) {
        return service.board().findCard(cardId);
    }

    /** Package-visible confirmation for windows that own their own buttons. */
    boolean confirm(String message) {
        return dialogs.confirm(message);
    }

    public void onChecklistAdd(com.personalkanban.domain.board.CardId cardId, String text) {
        guarded(() -> {
            service.addChecklistItem(cardId, text);
            refresh();
        });
    }

    public void onChecklistRename(com.personalkanban.domain.board.CardId cardId,
                                  String itemId, String newText) {
        guarded(() -> {
            service.renameChecklistItem(cardId, itemId, newText);
            refresh();
        });
    }

    public void onChecklistToggle(com.personalkanban.domain.board.CardId cardId,
                                  String itemId, boolean done) {
        guarded(() -> {
            service.setChecklistItemDone(cardId, itemId, done);
            refresh();
        });
    }

    public void onChecklistRemove(com.personalkanban.domain.board.CardId cardId, String itemId) {
        guarded(() -> {
            service.removeChecklistItem(cardId, itemId);
            refresh();
        });
    }

    /** Converts a checklist item into a card; reports where it landed. */
    public void onChecklistConvert(com.personalkanban.domain.board.CardId cardId, String itemId) {
        guarded(() -> {
            com.personalkanban.domain.board.CardId created =
                    service.convertChecklistItemToCard(cardId, itemId);
            refresh();
            dialogs.info(i18n.text("checklist.converted",
                    service.board().findCard(created).map(c -> c.title()).orElse("")));
        });
    }

    public void onNotesSaved(com.personalkanban.domain.board.CardId cardId, String notes) {
        guarded(() -> {
            service.setCardNotes(cardId, notes);
            refresh();
        });
    }

    // ------------------------------------------------------------------
    // Intents: time tracking (session 6)
    // ------------------------------------------------------------------

    /** First stopwatch click: starts tracking time on the card. */
    public void onStartTimeTracking(com.personalkanban.domain.board.CardId cardId) {
        guarded(() -> {
            service.startTimeTracking(cardId);
            refresh();
        });
    }

    /** Second stopwatch click: closes the running entry. */
    public void onStopTimeTracking(com.personalkanban.domain.board.CardId cardId) {
        guarded(() -> {
            service.stopTimeTracking(cardId);
            refresh();
        });
    }

    /** Saves the comment typed while tracking; blank text simply clears it. */
    public void onCommentTimeEntry(com.personalkanban.domain.board.CardId cardId, String comment) {
        guarded(() -> {
            service.commentRunningTimeEntry(cardId, comment);
            refresh();
        });
    }

    /** Deletes one time-tracking record (records are never editable). */
    public void onRemoveTimeEntry(com.personalkanban.domain.board.CardId cardId,
                                  com.personalkanban.domain.board.EntryId entryId) {
        guarded(() -> {
            service.removeTimeEntry(cardId, entryId);
            refresh();
        });
    }

    /** True while the card is being timed right now. */
    boolean isTracking(com.personalkanban.domain.board.CardId cardId) {
        return service.isTracking(cardId);
    }

    public void onLinkCards(com.personalkanban.domain.board.CardId from,
                            com.personalkanban.domain.board.CardId to) {
        guarded(() -> {
            service.linkCards(from, to);
            refresh();
        });
    }

    public void onUnlinkCards(com.personalkanban.domain.board.CardId from,
                              com.personalkanban.domain.board.CardId to) {
        guarded(() -> {
            service.unlinkCards(from, to);
            refresh();
        });
    }

    // ------------------------------------------------------------------
    // Intents: process view (session 6)
    // ------------------------------------------------------------------

    /** Arrow between two cards in a process row: links, or unlinks when linked. */
    public void onArrangeArrow(com.personalkanban.domain.board.CardId from,
                               com.personalkanban.domain.board.CardId to) {
        guarded(() -> {
            if (service.board().hasLink(from, to)) {
                service.unlinkCards(from, to);
            } else {
                service.linkCards(from, to);
            }
            refresh();
        });
    }

    /** Top arrows: adds a NEW card as predecessor/successor of the anchor. */
    public void onProcessAddNewRelation(com.personalkanban.domain.board.CardId anchor,
                                        boolean asPredecessor) {
        service.board().findCard(anchor).ifPresent(anchorCard -> guarded(() -> {
            CardId created = service.addCard(anchorCard.columnId(),
                    i18n.text("process.new.card.title"), "", anchorCard.color());
            if (asPredecessor) {
                service.linkCards(created, anchor);
            } else {
                service.linkCards(anchor, created);
            }
            refresh();
            onOpenCardDetail(created);
        }));
    }

    /** Bottom arrows: links an EXISTING card as predecessor/successor. */
    public void onProcessLinkExisting(com.personalkanban.domain.board.CardId anchor,
                                      boolean asPredecessor) {
        List<com.personalkanban.domain.board.Card> others = service.board().allCards().stream()
                .filter(card -> !card.id().equals(anchor)).toList();
        dialogs.chooseCard(others, i18n.text(asPredecessor
                        ? "process.link.predecessor.header" : "process.link.successor.header"))
                .ifPresent(other -> guarded(() -> {
                    if (asPredecessor) {
                        service.linkCards(other.id(), anchor);
                    } else {
                        service.linkCards(anchor, other.id());
                    }
                    refresh();
                }));
    }

    /** The card with keyboard focus in the processes view, or null. */
    public CardId processFocusId() {
        return processFocusId;
    }

    /** Keyboard or mouse focus moved onto a card of the processes view. */
    public void onProcessFocus(CardId id) {
        processFocusId = id;
        refreshProcessInspectors();
    }

    /** Escape in the processes view: drop the card focus. */
    public void onClearProcessFocus() {
        processFocusId = null;
        refreshProcessInspectors();
    }

    /** Moves keyboard focus to a card of the processes view. */
    public void focusProcessCard(CardId id) {
        onProcessFocus(id);
        findProcessCardNode(id).ifPresent(Node::requestFocus);
    }

    /** Removes every link of a process-view card, in both directions. */
    public void onProcessUnlinkAll(CardId id) {
        var board = service.board();
        List<CardId> predecessors = new ArrayList<>(board.incomingPredecessorsOf(id));
        List<CardId> successors = new ArrayList<>(board.outgoingSuccessorsOf(id));
        int total = predecessors.size() + successors.size();
        if (total == 0) {
            dialogs.info(i18n.text("process.unlink.none"));
            return;
        }
        if (!dialogs.confirm(i18n.text("process.unlink.all.confirm",
                total, board.titleOf(id)))) {
            return;
        }
        guarded(() -> {
            for (CardId predecessor : predecessors) {
                service.unlinkCards(predecessor, id);
            }
            for (CardId successor : successors) {
                service.unlinkCards(id, successor);
            }
            refresh();
        });
    }

    /** Suggested order of a process: dialog with the topological order. */
    public void onShowSuggestedOrder(com.personalkanban.domain.board.ProcessId processId) {
        var board = service.board();
        var members = board.cardsOfProcess(processId);
        if (members.isEmpty()) {
            dialogs.info(i18n.text("process.order.empty"));
            return;
        }
        List<com.personalkanban.domain.board.CardId> ids =
                members.stream().map(com.personalkanban.domain.board.Card::id).toList();
        var result = service.suggestedOrder(ids);
        if (!result.cycleRemaining().isEmpty()) {
            dialogs.info(i18n.text("process.order.cycle",
                    result.cycleRemaining().size(),
                    result.cycleRemaining().stream()
                            .map(board::titleOf).collect(java.util.stream.Collectors.joining(", "))));
        }
        StringBuilder text = new StringBuilder();
        int position = 1;
        for (com.personalkanban.domain.board.CardId cardId : result.ordered()) {
            text.append(position++).append("). ")
                    .append(board.titleOf(cardId)).append('\n');
        }
        dialogs.info(i18n.text("process.order.title") + "\n\n" + text.toString().strip());
    }

    // ------------------------------------------------------------------
    // Intents: columns & cards (invoked by the view builders)
    // ------------------------------------------------------------------

    /** Adds a column at the end of the board (the Boards menu entry, N-3). */
    private void onAddColumn() {
        addColumnAt(-1);
    }

    /** Adds a column to the left of the given one (its own index). */
    public void onAddColumnLeft(com.personalkanban.domain.board.ColumnId columnId) {
        addColumnAt(columnIndexOf(columnId));
    }

    /** Adds a column to the right of the given one. */
    public void onAddColumnRight(com.personalkanban.domain.board.ColumnId columnId) {
        addColumnAt(columnIndexOf(columnId) + 1);
    }

    /** Position of a column in the active board (-1 when it is not there). */
    private int columnIndexOf(com.personalkanban.domain.board.ColumnId columnId) {
        return service.board().columns().stream().map(BoardColumn::id).toList().indexOf(columnId);
    }

    /**
     * Asks for the new column and inserts it at {@code index} in one undoable
     * step (negative = append). The same dialog as before, so title,
     * description, color and WIP limit are unchanged.
     */
    private void addColumnAt(int index) {
        Optional<Dialogs.ColumnForm> form = dialogs.columnDialog(null);
        form.ifPresent(f -> guarded(() -> {
            service.addColumnAt(f.title(), f.description(), f.color(), f.wipLimit(), index);
            refresh();
        }));
    }

    /**
     * Quick capture (user request T13 · B4): the column's ➕ opens a one-line
     * field with inline syntax. Enter creates the card right away; «More
     * options…» hands the parsed draft to the full dialog instead.
     */
    public void onAddCard(com.personalkanban.domain.board.ColumnId columnId) {
        new QuickCaptureDialog(i18n).showAndWait(LocalDate.now()).ifPresent(result -> {
            if (result.action() == QuickCaptureDialog.Action.CREATE) {
                QuickCapture.Draft draft = result.draft();
                if (!draft.canCreate()) {
                    return;
                }
                guarded(() -> {
                    service.addCard(columnId, draft.title(), "", BoardColor.DEFAULT,
                            draft.dueDate(), draft.labels());
                    refresh();
                });
            } else if (result.action() == QuickCaptureDialog.Action.MORE) {
                onAddCardFull(columnId, QuickCaptureDialog.toCardForm(result.draft()));
            }
        });
    }

    /** The classic full card dialog for a new card (Ctrl+➕ / column menu). */
    public void onAddCardFull(com.personalkanban.domain.board.ColumnId columnId) {
        onAddCardFull(columnId, null);
    }

    /** Full card dialog, optionally prefilled by quick capture. */
    private void onAddCardFull(com.personalkanban.domain.board.ColumnId columnId,
                               Dialogs.CardForm initial) {
        Optional<Dialogs.CardForm> form = dialogs.cardDialog(
                initial, globalLabelVocabulary(), service.processes(),
                service.board().allCards(), List.of(), List.of());
        form.ifPresent(f -> guarded(() -> {
            var created = service.addCard(columnId, f.title(), f.description(), f.color(),
                    f.dueDate(), f.labels(), f.notes(), null, f.processId());
            if (!f.checklistLines().isEmpty()) {
                service.setChecklistFromLines(created, f.checklistLines());
            }
            for (var predecessor : f.predecessors()) {
                service.linkCards(predecessor, created);
            }
            for (var successor : f.successors()) {
                service.linkCards(created, successor);
            }
            refresh();
        }));
    }

    public void onEditCard(com.personalkanban.domain.board.CardId cardId) {
        service.board().findCard(cardId).ifPresent(card -> {
            List<String> checklistLines = card.checklist().stream()
                    .map(item -> item.done() ? "[x] " + item.text() : item.text())
                    .toList();
            String oldNotes = card.notes();
            List<String> oldLines = List.copyOf(checklistLines);
            var oldProcessId = card.processId();
            var oldPredecessors = List.copyOf(service.board().incomingPredecessorsOf(cardId));
            var oldSuccessors = List.copyOf(service.board().outgoingSuccessorsOf(cardId));
            var otherCards = service.board().allCards().stream()
                    .filter(other -> !other.id().equals(cardId)).toList();
            var initial = new Dialogs.CardForm(card.title(), card.description(), card.color(),
                    card.dueDate(), List.copyOf(card.labels()), card.notes(),
                    checklistLines, card.processId());
            dialogs.cardDialog(initial, globalLabelVocabulary(), service.processes(),
                    otherCards, oldPredecessors, oldSuccessors)
                    .ifPresent(f -> guarded(() -> {
                        service.editCard(cardId, f.title(), f.description(), f.color(),
                                f.dueDate(), f.labels());
                        // Advanced fields apply only when actually changed, so
                        // undo stays meaningful (one transaction per change).
                        if (!f.notes().equals(oldNotes)) {
                            service.setCardNotes(cardId, f.notes());
                        }
                        if (!f.checklistLines().equals(oldLines)) {
                            service.setChecklistFromLines(cardId, f.checklistLines());
                        }
                        if (!java.util.Objects.equals(f.processId(), oldProcessId)) {
                            if (f.processId() == null) {
                                // "Ninguno": leaves the process AND
                                // drops every arrow that touches this
                                // card (TarjA→TarjX→TarjB becomes
                                // TarjA … TarjB, unrelated).
                                service.unassignCardFromProcess(cardId);
                            } else {
                                service.assignCardToProcess(cardId, f.processId());
                            }
                        }
                        diffLinks(cardId, oldPredecessors, f.predecessors(), true);
                        diffLinks(cardId, oldSuccessors, f.successors(), false);
                        refresh();
                    }));
        });
    }

    /** Applies predecessor/successor diffs as link/unlink transactions. */
    private void diffLinks(com.personalkanban.domain.board.CardId cardId,
                           List<com.personalkanban.domain.board.CardId> before,
                           List<com.personalkanban.domain.board.CardId> after,
                           boolean asPredecessor) {
        for (var other : after) {
            if (!before.contains(other)) {
                if (asPredecessor) {
                    service.linkCards(other, cardId);
                } else {
                    service.linkCards(cardId, other);
                }
            }
        }
        for (var other : before) {
            if (!after.contains(other)) {
                if (asPredecessor) {
                    service.unlinkCards(other, cardId);
                } else {
                    service.unlinkCards(cardId, other);
                }
            }
        }
    }

    /** Opens the non-modal markdown detail window for a card. */
    public void onOpenCardDetail(com.personalkanban.domain.board.CardId cardId) {
        service.board().findCard(cardId).ifPresent(card ->
                CardDetailWindow.open(card, this, i18n, themeManager));
    }

    /** Persists a description edited in the detail window (keeps other fields). */
    public void onDescriptionSaved(com.personalkanban.domain.board.CardId cardId, String markdown) {
        service.board().findCard(cardId).ifPresent(card -> guarded(() -> {
            service.editCard(cardId, card.title(), markdown, card.color(),
                    card.dueDate(), card.labels());
            refresh();
        }));
    }

    public void onRemoveCard(com.personalkanban.domain.board.CardId cardId) {
        if (dialogs.confirm(i18n.text("confirm.delete.card"))) {
            guarded(() -> {
                // Session 10: Delete must not throw the keyboard user back to
                // the top of the board — remember the card's position and
                // focus the card that takes its place afterwards.
                int[] position = cardId.equals(cardFocused) ? visiblePositionOf(cardId) : null;
                trashAttachments(cardId); // notes/checklist go with the row; files go to the trash
                service.removeCard(cardId);
                refresh();
                if (position != null) {
                    cardFocused = null;
                    focusAtVisiblePosition(position[0], position[1]);
                }
            });
        }
    }

    // ------------------------------------------------------------------
    // Session 10 (board cleanup): attachments of deleted cards + maintenance
    // ------------------------------------------------------------------

    /**
     * The card's attachment files follow a deletion — but into the trash,
     * never to `rm`: the row itself is undoable and the files live outside
     * the database, so a hard delete would make Ctrl+Z restore a card whose
     * attachments were silently lost.
     */
    private void trashAttachments(CardId cardId) {
        AttachmentStore.moveToTrash(com.personalkanban.Main.dataDirectory(), cardId.value());
    }

    /** Brings back trashed attachments of cards that exist again (after undo). */
    private void restoreTrashedAttachments() {
        java.nio.file.Path dataDirectory = com.personalkanban.Main.dataDirectory();
        for (String cardId : AttachmentStore.trashedCardIds(dataDirectory)) {
            if (service.board().findCard(new CardId(cardId)).isPresent()) {
                AttachmentStore.restoreFromTrash(dataDirectory, cardId);
            }
        }
    }

    /**
     * File → Cleanup: deletes the attachment folders (live and trashed) whose
     * card no longer exists in ANY board of this database. That is the safe
     * way to actually free the disk: folders of cards that were deleted
     * before this feature existed, and the trash left by later deletions.
     */
    private void onCleanupAttachments() {
        java.nio.file.Path dataDirectory = com.personalkanban.Main.dataDirectory();
        List<String> orphans = AttachmentStore.storedCardIds(dataDirectory).stream()
                .filter(id -> !cardExistsAnywhere(id))
                .toList();
        if (orphans.isEmpty()) {
            dialogs.info(i18n.text("cleanup.attachments.none"));
            return;
        }
        if (!dialogs.confirm(i18n.text("cleanup.attachments.confirm", orphans.size()))) {
            return;
        }
        int deleted = 0;
        for (String cardId : orphans) {
            if (AttachmentStore.deleteCardFolder(dataDirectory, cardId)) {
                deleted++;
            }
        }
        dialogs.info(i18n.text("cleanup.attachments.done", deleted));
    }

    /** True when the card exists in the active board or in any other one. */
    private boolean cardExistsAnywhere(String cardId) {
        CardId id = new CardId(cardId);
        if (service.board().findCard(id).isPresent()) {
            return true;
        }
        for (BoardDescriptor descriptor : service.boards()) {
            if (descriptor.id().equals(service.activeBoardId())) {
                continue;
            }
            try {
                boolean exists = service.columnsOf(descriptor.id()).stream()
                        .flatMap(column -> column.cards().stream())
                        .anyMatch(card -> card.id().equals(id));
                if (exists) {
                    return true;
                }
            } catch (RuntimeException unreadableBoard) {
                // A board we cannot read must never cost the user their files.
                return true;
            }
        }
        return false;
    }

    /** Zero-based [column, card] of a visible card, or null when not visible. */
    private int[] visiblePositionOf(CardId cardId) {
        List<List<CardId>> columns = visibleColumnsCards();
        for (int c = 0; c < columns.size(); c++) {
            int index = columns.get(c).indexOf(cardId);
            if (index >= 0) {
                return new int[]{c, index};
            }
        }
        return null;
    }

    /** Focuses the card that took a removed card's place (clamped to the column). */
    private void focusAtVisiblePosition(int columnIndex, int cardIndex) {
        List<List<CardId>> columns = visibleColumnsCards();
        if (columnIndex < 0 || columnIndex >= columns.size()) {
            return;
        }
        List<CardId> cards = columns.get(columnIndex);
        if (cards.isEmpty()) {
            return;
        }
        kanbanFocusCard(cards.get(Math.min(cardIndex, cards.size() - 1)));
    }

    /**
     * Move-or-copy one card to another board (user request): the transfer
     * dialog lists every other board and its columns; "mover" removes the
     * card from this board after copying it to the target.
     */
    public void onTransferCardToBoard(com.personalkanban.domain.board.CardId cardId) {
        var sourceCard = service.board().findCard(cardId);
        if (sourceCard.isEmpty()) {
            return;
        }
        var otherBoards = service.boards().stream()
                .filter(descriptor -> !descriptor.id().equals(service.activeBoardId()))
                .toList();
        if (otherBoards.isEmpty()) {
            dialogs.info(i18n.text("card.transfer.no.other"));
            return;
        }
        dialogs.cardTransferDialog(1, otherBoards, service.activeBoardId(), service::columnsOf)
                .ifPresent(form -> guarded(() -> {
                    if (form.copy()) {
                        service.copyCardToBoard(cardId, form.targetBoardId(),
                                form.targetColumn().id());
                    } else {
                        service.moveCardToBoard(cardId, form.targetBoardId(),
                                form.targetColumn().id());
                    }
                    refresh();
                }));
    }

    /**
     * Session 10 (F3): exports ONE card as txt / markdown / pdf with the
     * sections, notes and format the user picks in the export dialog.
     */
    public void onExportCard(com.personalkanban.domain.board.CardId cardId) {
        service.board().findCard(cardId).ifPresent(card ->
                dialogs.exportCardDialog(card).ifPresent(form -> {
                    String extension = form.format().fileExtension();
                    FileChooser chooser = new FileChooser();
                    chooser.setTitle(i18n.text("export.card.title"));
                    chooser.setInitialFileName(safeFileName(card.title()) + extension);
                    chooser.setInitialDirectory(initialDirectory());
                    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                            form.format().name().toUpperCase(Locale.ROOT) + " (*" + extension + ")",
                            "*" + extension));
                    java.io.File file = chooser.showSaveDialog(window());
                    if (file == null) {
                        return;
                    }
                    try {
                        String text = CardExporter.render(card, service.timeEntriesOf(cardId),
                                form, i18n);
                        if (form.format() == CardExporter.Format.PDF) {
                            com.personalkanban.ui.pdf.CardPdfWriter.write(text, file.toPath());
                        } else {
                            Files.writeString(file.toPath(), text,
                                    java.nio.charset.StandardCharsets.UTF_8);
                        }
                        context.rememberTransferDirectory(file.getParentFile().toPath());
                    } catch (java.io.IOException e) {
                        dialogs.error(Dialogs.describeFailure(
                                new RuntimeException(e.getMessage(), e)));
                    } catch (RuntimeException e) {
                        dialogs.error(Dialogs.describeFailure(e));
                    }
                }));
    }

    /** File-system-safe default file name for a card title. */
    private static String safeFileName(String title) {
        String base = title == null ? "" : title.strip()
                .replaceAll("[\\\\/:*?\"<>|]+", " ").strip().replaceAll("\\s+", " ");
        if (base.isEmpty()) {
            base = "card";
        }
        return base.length() > 60 ? base.substring(0, 60).strip() : base;
    }

    public void onClearColumn(com.personalkanban.domain.board.ColumnId columnId) {
        if (dialogs.confirm(i18n.text("confirm.clear.cards"))) {
            guarded(() -> {
                service.column(columnId).cards().forEach(card -> trashAttachments(card.id()));
                service.clearColumn(columnId);
                refresh();
            });
        }
    }

    public void onEditColumn(com.personalkanban.domain.board.ColumnId columnId) {
        var column = service.column(columnId);
        var initial = new Dialogs.ColumnForm(column.title(), column.description(),
                column.color(), column.wipLimit(), column.isDone(), column.backgroundColor());
        dialogs.columnDialog(initial).ifPresent(f -> guarded(() -> {
            service.renameColumn(columnId, f.title());
            service.editColumn(columnId, f.description(), f.color(), f.wipLimit(),
                    f.done(), f.backgroundColor());
            refresh();
        }));
    }

    /** Toggles the board's single "finalizado" column (service keeps the invariant). */
    public void onSetColumnDone(com.personalkanban.domain.board.ColumnId columnId) {
        var column = service.column(columnId);
        guarded(() -> {
            service.setColumnDone(columnId, !column.isDone());
            refresh();
        });
    }

    /** Column background color, separate from the card-tab color (null = default). */
    public void onSetColumnBackground(com.personalkanban.domain.board.ColumnId columnId) {
        dialogs.columnBackgroundDialog(service.column(columnId).backgroundColor())
                .ifPresent(color -> guarded(() -> {
                    service.setColumnBackground(columnId, color);
                    refresh();
                }));
    }

    /** Moves the column to a user-chosen 1-based board position. */
    public void onReorderColumn(com.personalkanban.domain.board.ColumnId columnId) {
        var columns = service.board().columns();
        int current = 0;
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).id().equals(columnId)) {
                current = i;
                break;
            }
        }
        dialogs.columnOrderDialog(current + 1, columns.size()).ifPresent(position ->
                guarded(() -> {
                    service.moveColumn(columnId, position - 1);
                    refresh();
                }));
    }

    public void onRemoveColumn(com.personalkanban.domain.board.ColumnId columnId) {
        if (dialogs.confirm(i18n.text("confirm.delete.column"))) {
            guarded(() -> {
                service.column(columnId).cards().forEach(card -> trashAttachments(card.id()));
                service.removeColumn(columnId);
                refresh();
            });
        }
    }

    public void onMoveCard(com.personalkanban.domain.board.CardId cardId,
                           com.personalkanban.domain.board.ColumnId targetColumn, int targetIndex) {
        guarded(() -> {
            service.moveCard(cardId, targetColumn, targetIndex);
            refresh();
        });
    }

    public void onMoveCardToSlot(com.personalkanban.domain.board.CardId cardId,
                                 com.personalkanban.domain.board.ColumnId targetColumn, int slotIndex) {
        guarded(() -> {
            service.moveCardToSlot(cardId, targetColumn, slotIndex);
            refresh();
        });
    }

    /** Stable priority sort of one column: (★+!) → (!) → (★) → rest. */
    public void onSortColumnByPriority(com.personalkanban.domain.board.ColumnId columnId) {
        guarded(() -> {
            service.sortColumnByPriority(columnId);
            refresh();
        });
    }

    /** Opens the detail window for reading/updating a card's notes. */
    public void onOpenCardNotes(com.personalkanban.domain.board.CardId cardId) {
        service.board().findCard(cardId).ifPresent(card ->
                CardDetailWindow.open(card, this, i18n, themeManager));
    }

    /**
     * Opens the attachments window of a card: reference files kept as plain
     * files under the program's data folder (never in the database).
     */
    public void onOpenCardAttachments(com.personalkanban.domain.board.CardId cardId) {
        service.board().findCard(cardId).ifPresent(card ->
                AttachmentsWindow.open(card.id().value(), card.title(), i18n, themeManager));
    }

    /** Number of files attached to a card (badge on the card view). */
    public int attachmentCountOf(com.personalkanban.domain.board.CardId cardId) {
        return AttachmentStore.count(com.personalkanban.Main.dataDirectory(), cardId.value());
    }

    public void onMoveColumn(com.personalkanban.domain.board.ColumnId columnId, int targetIndex) {
        guarded(() -> {
            service.moveColumn(columnId, targetIndex);
            refresh();
        });
    }

    /**
     * Makes the ACTIVE BOARD visible at all times: its name is shown in the
     * toolbar and in the window title (next to the database file name).
     * Called from rebuildAll — every path that changes the board (open, new,
     * rename, delete, import, language, database switch) goes through it.
     */
    private void updateBoardIdentity() {
        String name = service.boards().stream()
                .filter(descriptor -> descriptor.id().equals(service.activeBoardId()))
                .findFirst()
                .map(BoardDescriptor::name)
                .orElse("");
        if (boardNameLabel != null) {
            boardNameLabel.setText(name);
        }
        title.set(i18n.text("app.title") + " \u2014 " + name
                + " \u2014 " + context.databasePath().getFileName());
    }

    /** Runs a UI action, converting failures into an error dialog. */
    private void guarded(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            dialogs.error(Dialogs.describeFailure(e));
        }
    }

    /** Re-renders the relation inspectors of the processes view rows. */
    private void refreshProcessInspectors() {
        com.personalkanban.domain.board.Card focused = processFocusId == null
                ? null
                : service.board().findCard(processFocusId).orElse(null);
        if (focused == null) {
            processFocusId = null;
        }
        forEachInspector(box ->
                ProcessViewBuilder.renderInspector(box, service, i18n, this, focused));
    }

    /** Applies an update to every relation inspector of the processes view. */
    private void forEachInspector(Consumer<VBox> action) {
        if (!processView) {
            return;
        }
        Node content = boardScroller.getContent();
        if (content instanceof Parent parent) {
            collectInspectors(parent, action);
        }
    }

    /** Recursively feeds every process-row inspector to the action. */
    private void collectInspectors(Parent parent, Consumer<VBox> action) {
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof VBox box
                    && child.getStyleClass().contains("process-inspector")) {
                action.accept(box);
            } else if (child instanceof Parent nested) {
                collectInspectors(nested, action);
            }
        }
    }

    /** The node of a card in the processes view, if it is built. */
    private Optional<Node> findProcessCardNode(CardId id) {
        Node content = boardScroller.getContent();
        return content == null ? Optional.empty() : findNodeWithData(content, id);
    }

    private Optional<Node> findNodeWithData(Node node, Object data) {
        if (data.equals(node.getUserData())) {
            return Optional.of(node);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Optional<Node> found = findNodeWithData(child, data);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private void refresh() {
        // An undo may have just brought a deleted card back: restore the
        // attachment files that were trashed when it was deleted. Cheap when
        // there is nothing trashed (one empty directory listing).
        restoreTrashedAttachments();
        LabelFilter filter = currentLabelFilter();
        LabelFilter quickFilter = currentQuickFlagFilter();
        // The columns about to be replaced know where the user was looking.
        rememberColumnScroll();
        columnsRow.getChildren().setAll(
                ColumnViewBuilder.buildAll(service, i18n, dialogs, this, undoRedo, filter, quickFilter,
                        selectionMode, collapsedColumns, themeManager.isDark(), columnWidths));
        // The processes view always spans the full window width;
        // the kanban view keeps content-sized columns.
        boardScroller.setFitToWidth(processView);
        boardScroller.setContent(processView
                ? ProcessViewBuilder.buildAll(service, i18n, this)
                : columnsRow);
        rebuildBoardMenu(); // keep the active-board marker in sync
        rebuildProcessMenu();
        syncCardViewMenu();
        syncViewMenu();
        undoRedo.sync();
        syncFilterBarVisibility();
        updateDueNotice();
        updateSelectionBarState();
        // Keep the processes-view keyboard focus across the rebuild.
        if (processView && processFocusId != null) {
            if (service.board().findCard(processFocusId).isEmpty()) {
                processFocusId = null;
            } else {
                findProcessCardNode(processFocusId).ifPresent(Node::requestFocus);
            }
        }
        // The "current" card must still exist after the rebuild (the card may
        // have been deleted or moved), and the node just built needs the
        // highlight re-painted — a data change rebuilt it from scratch.
        if (cardFocused != null && service.board().findCard(cardFocused).isEmpty()) {
            cardFocused = null;
        }
        syncCurrentCardHighlight();
    }

    /** Switches between the kanban and the processes view. */
    public void onToggleView() {
        onSelectView(!processView);
    }

    /**
     * Shows the board view (false) or the processes view (true): the toolbar
     * toggle, the "Ver" menu and its accelerators all land here.
     */
    public void onSelectView(boolean processes) {
        if (processView == processes) {
            return;
        }
        processView = processes;
        refresh();
    }

    private Button toolButton(String glyph, String textKey) {
        Button button = new Button(glyph);
        button.getStyleClass().addAll("tool-button");
        button.setTooltip(new Tooltip(i18n.text(textKey)));
        button.setFocusTraversable(false);
        return button;
    }
}
