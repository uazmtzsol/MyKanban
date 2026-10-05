package com.personalkanban.ui;

import com.personalkanban.AppContext;
import com.personalkanban.application.BoardService;
import com.personalkanban.application.CardViewSettings;
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

    private final StringProperty title = new SimpleStringProperty();
    private final VBox root;

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
    private Menu helpMenu;
    private Label boardNameLabel;
    private HBox toolbarReference;

    // Label filter state; survives language-driven rebuilds of the controls.
    private String activeFilterLabels = "";
    private String activeFilterMode; // null (= show each, "AND" or "OR")
    private TextField labelFilterField;
    private ComboBox<String> labelFilterMode;
    // Quick flag filters (★ Importante / ! Urgente); both active = AND (must carry both).
    private final java.util.Set<String> quickFlagFilters = new java.util.LinkedHashSet<>();

    // Multi-selection mode: the selected cards all live in selectionColumn.
    private boolean selectionMode;
    private ColumnId selectionColumn;
    private final java.util.Set<CardId> selectedCards = new java.util.LinkedHashSet<>();
    private HBox selectionBar;

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

    // Background customization (session 4): "path|dim" or null = none.
    private String backgroundSpec;

    // Which database menus were built (the File menu must re-run its builder).

    public BoardController(AppContext context) {
        this.context = context;
        this.service = context.boardService();
        this.i18n = new I18n(context.savedLocale());
        this.themeManager = context.themeManager();
        this.undoRedo = new UndoRedoController(service, i18n, this::refresh);
        this.dialogs = new Dialogs(i18n);
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

    /** Clears every per-card override (all cards follow the board default). */
    public void onResetCardViewModes() {
        cardViewSettings.clearOverrides();
        service.setCardViewSettings(service.activeBoardId(), cardViewSettings);
        refresh();
    }

    public void bindScene(Scene scene) {
        undoRedo.bindScene(scene);
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
        scene.getAccelerators().put(javafx.scene.input.KeyCombination.valueOf("Shortcut+F"),
                this::onFocusFilter);
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
        scene.getAccelerators().put(javafx.scene.input.KeyCombination.valueOf("Shortcut+P"),
                this::onFocusProcessFilter);
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
        backgroundSpec = service.background();
        applyBoardBackground();

        HBox toolbar = buildToolbar();
        HBox filterBar = buildFilterBar();
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
        root.getChildren().setAll(menuBar, toolbar, selectionBar, filterBar, boardScroller);
        updateBoardIdentity();
        refresh();
    }

    private HBox buildToolbar() {
        Label brand = new Label("Personal Kanban");
        brand.getStyleClass().add("brand");

        Button addColumn = toolButton("\u2795", "toolbar.add.column");
        addColumn.setOnAction(e -> onAddColumn());

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

        // "Guardar" (session 5, user request): folds the WAL into the file
        // right now; everything was already committed, this is reassurance.
        Button save = toolButton("\uD83D\uDCBE", "toolbar.save");
        save.setOnAction(e -> onSaveNow());

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

        HBox toolbar = new HBox(8, brand, boardNameLabel, addColumn, viewToggle,
                undoButton, redoButton, save, darkMode, exportPdf, cardViewMenu, spacer);
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
        labelFilterField.textProperty().addListener((obs, old, value) -> {
            activeFilterLabels = value == null ? "" : value;
            refresh();
        });
        // Autocomplete on the filter too: same vocabulary as the card dialog.
        LabelAutoComplete.attach(labelFilterField, new LabelSuggester(java.util.List.of()),
                List::of).setVocabularySupplier(this::filterVocabulary);

        labelFilterMode = new ComboBox<>();
        labelFilterMode.getItems().addAll("AND", "OR");
        labelFilterMode.setPromptText(i18n.text("filter.mode.empty"));
        labelFilterMode.setValue(activeFilterMode);
        labelFilterMode.setPrefWidth(90);
        labelFilterMode.setTooltip(new Tooltip(i18n.text("filter.mode.tooltip")));
        labelFilterMode.setOnAction(e -> {
            activeFilterMode = labelFilterMode.getValue();
            refresh();
        });

        Button clearFilter = toolButton("\u2715", "filter.clear");
        clearFilter.setOnAction(e -> {
            labelFilterField.clear();
            labelFilterMode.setValue(null);
            activeFilterLabels = "";
            activeFilterMode = null;
            activeProcessFilter = null;
            processFilterNone = false;
            if (processFilterCombo != null) {
                processFilterCombo.setValue(i18n.text("filter.process.all"));
            }
            refresh();
        });

        javafx.scene.control.ToggleButton importantFilter = quickFlagFilterButton(
                "\u2605", com.personalkanban.domain.board.Card.LABEL_IMPORTANT, "filter.flag.important");
        javafx.scene.control.ToggleButton urgentFilter = quickFlagFilterButton(
                "!", com.personalkanban.domain.board.Card.LABEL_URGENT, "filter.flag.urgent");

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

        HBox bar = new HBox(8, filterLabel, labelFilterField, labelFilterMode, clearFilter,
                importantFilter, urgentFilter, processFilter);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().addAll("toolbar", "filter-bar");
        bar.setPadding(new Insets(6, 10, 6, 10));
        return bar;
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
        updateSelectionBarState();
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
        List<CardId> ids = List.copyOf(selectedCards);
        dialogs.bulkLabelsDialog(ids.size(), service.labelVocabulary()).ifPresent(form -> {
            if (form.labels().isEmpty()) {
                return; // nothing typed: keep the selection active
            }
            guarded(() -> {
                if (form.add()) {
                    service.addLabelsToCards(ids, form.labels());
                } else {
                    service.removeLabelsFromCards(ids, form.labels());
                }
                exitSelectionMode();
            });
        });
    }

    private void onBulkColor() {
        List<CardId> ids = List.copyOf(selectedCards);
        dialogs.bulkColorDialog(ids.size()).ifPresent(color -> guarded(() -> {
            service.recolorCards(ids, color);
            exitSelectionMode();
        }));
    }

    private void onBulkProcess() {
        List<CardId> ids = List.copyOf(selectedCards);
        // "Ninguno" (null) takes the cards out of their process
        // without touching their arrow relations — bulk actions
        // must never destroy links silently.
        dialogs.bulkProcessDialog(ids.size(), service.processes()).ifPresent(form ->
                guarded(() -> {
                    service.assignProcessToCards(ids, form.processId());
                    exitSelectionMode();
                }));
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
            service.removeCards(ids);
            exitSelectionMode();
        });
    }

    /** Vocabulary for the filter's autocomplete: same as card dialogs. */
    private List<String> filterVocabulary() {
        return service.labelVocabulary();
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
    private LabelFilter currentLabelFilter() {
        var labels = Dialogs.parseLabels(activeFilterLabels);
        if (labels.isEmpty()) {
            return LabelFilter.none();
        }
        LabelFilter.Mode mode = "OR".equalsIgnoreCase(activeFilterMode)
                ? LabelFilter.Mode.ANY
                : LabelFilter.Mode.ALL;
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
        processMenu = new Menu(i18n.text("menu.processes"));
        rebuildProcessMenu();
        helpMenu = new Menu(i18n.text("menu.help"));
        rebuildHelpMenu();
        return new MenuBar(databaseMenu, boardMenu, processMenu, languageMenu, helpMenu);
    }

    private void rebuildDatabaseMenu() {
        if (databaseMenu == null) {
            return;
        }
        MenuItem currentFile = new MenuItem(i18n.text("db.current") + " "
                + context.databasePath());
        currentFile.setDisable(true);
        databaseMenu.getItems().setAll(
                itemOf("file.save", this::onSaveNow),
                itemOf("prefs.title", this::onShowPreferences),
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
                itemOf("board.export", this::onExportBoard),
                itemOf("board.export.pdf", this::onExportBoardPdf),
                itemOf("board.import", this::onImportBoard));
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

    /** Rebuilds the help menu contents (shortcuts reference lives here). */
    private void rebuildHelpMenu() {
        if (helpMenu == null) {
            return;
        }
        helpMenu.getItems().setAll(itemOf("help.shortcuts", this::onShowShortcutsHelp));
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
        rebuildAll();
        Scene scene = root.getScene();
        if (scene != null) {
            scene.getStylesheets().setAll(themeManager.stylesheet());
        }
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
            Optional<Dialogs.NewBoardColumns> choice = dialogs.newBoardColumnsDialog();
            if (choice.isEmpty()) {
                return; // cancelled: create nothing
            }
            List<String> columnTitles;
            if (choice.get() == Dialogs.NewBoardColumns.STANDARD) {
                columnTitles = List.of(
                        i18n.text("board.kanban.todo"),
                        i18n.text("board.kanban.doing"),
                        i18n.text("board.kanban.done"));
            } else if (choice.get() == Dialogs.NewBoardColumns.CUSTOM) {
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
            if (choice.get() == Dialogs.NewBoardColumns.STANDARD
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
        Scene scene = root.getScene();
        if (scene != null) {
            scene.getStylesheets().setAll(themeManager.stylesheet());
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
        ShortcutsHelpWindow.show(i18n, themeManager.stylesheet());
    }

    /** Closes the app cleanly (File → Exit / Ctrl+Q / window X). */
    private void onExit() {
        shutdown();
        javafx.application.Platform.exit();
    }

    /**
     * Persistence hygiene (user request): every mutation already commits
     * instantly; this performs the final WAL checkpoint and closes the
     * database so the -wal/-shm files are folded away and removed. Also
     * wired to the window close (X) via {@code Main}.
     */
    public void shutdown() {
        try {
            context.checkpointQuietly();
        } catch (RuntimeException ignored) {
            // Best-effort: closing below is the real guarantee.
        }
        context.close();
    }

    /** Ctrl+S / menu "Guardar": folds the WAL into the file right now. */
    public void onSaveNow() {
        boolean folded = context.saveCheckpoint();
        if (folded) {
            dialogs.info(i18n.text("file.save.confirm"));
        } else {
            dialogs.info(i18n.text("file.save.busy"));
        }
    }

    /** Preferences: background image, priority and label colors. */
    private void onShowPreferences() {
        java.io.File seed = initialDirectory();
        var choice = new PreferencesDialog(i18n, service, themeManager.isDark())
                .show(seed, backgroundSpec);
        choice.ifPresent(selected -> {
            String stored = selected.path() == null
                    ? null
                    : selected.path() + "|" + String.format(java.util.Locale.ROOT, "%.2f", selected.dim());
            backgroundSpec = stored;
            service.setBackground(stored);
            applyBoardBackground();
        });
    }

    /**
     * Paints the customized background (session 4): image over the whole
     * board, pre-dimmed once at load time so cards/columns stay readable.
     * A missing or unreadable file silently falls back to no background
     * (cosmetic preference; it must never block the app).
     */
    private void applyBoardBackground() {
        if (backgroundSpec == null || backgroundSpec.isBlank()) {
            paintPlainBackground();
            return;
        }
        String[] parts = backgroundSpec.split("\\|", 2);
        String path = parts[0];
        double dim = 0.45;
        if (parts.length == 2) {
            try {
                dim = Math.clamp(Double.parseDouble(parts[1]), 0.0, 0.8);
            } catch (NumberFormatException ignored) {
                // keep default dim
            }
        }
        java.io.File file = new java.io.File(path);
        if (!file.isFile()) {
            paintPlainBackground();
            return;
        }
        try {
            javafx.scene.image.Image image = new javafx.scene.image.Image(
                    dimmedImageUri(file, dim), true);
            root.setBackground(new javafx.scene.layout.Background(
                    new javafx.scene.layout.BackgroundImage(
                            image,
                            javafx.scene.layout.BackgroundRepeat.NO_REPEAT,
                            javafx.scene.layout.BackgroundRepeat.NO_REPEAT,
                            javafx.scene.layout.BackgroundPosition.CENTER,
                            new javafx.scene.layout.BackgroundSize(
                                    javafx.scene.layout.BackgroundSize.AUTO,
                                    javafx.scene.layout.BackgroundSize.AUTO,
                                    false, false, true, true)))); // cover
        } catch (RuntimeException e) {
            // A bad image file is a cosmetic problem only.
            paintPlainBackground();
        }
    }

    /**
     * Solid fallback behind the columns. The stylesheet's
     * .board-root color used to win over the programmatic
     * background (hiding the image), so both the plain state and
     * the "image missing" state are painted here instead.
     */
    private void paintPlainBackground() {
        root.setBackground(new javafx.scene.layout.Background(
                new javafx.scene.layout.BackgroundFill(
                        javafx.scene.paint.Color.web(
                                themeManager.isDark() ? "#16181d" : "#f5f6f8"),
                        javafx.scene.layout.CornerRadii.EMPTY,
                        javafx.geometry.Insets.EMPTY)));
    }

    /**
     * Returns a data URI of the image with a black overlay of the given
     * opacity composited once (BufferedImage, same trick as the PDF
     * exporter), so the UI never pays per-frame dimming.
     */
    private static String dimmedImageUri(java.io.File file, double dim) {
        try {
            java.awt.image.BufferedImage source = javax.imageio.ImageIO.read(file);
            if (source == null) {
                return file.toURI().toString(); // undimmable format: use as is
            }
            java.awt.image.BufferedImage dimmed = new java.awt.image.BufferedImage(
                    source.getWidth(), source.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D graphics = dimmed.createGraphics();
            graphics.drawImage(source, 0, 0, null);
            graphics.setColor(new java.awt.Color(0, 0, 0, (int) Math.round(dim * 255)));
            graphics.fillRect(0, 0, dimmed.getWidth(), dimmed.getHeight());
            graphics.dispose();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(dimmed, "png", out);
            return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (java.io.IOException e) {
            return file.toURI().toString(); // fall back to the raw image
        }
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
        if (activeProcessFilter != null) {
            return activeProcessFilter.equals(card.processId());
        }
        return !processFilterNone || card.processId() == null;
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

    private void onAddColumn() {
        Optional<Dialogs.ColumnForm> form = dialogs.columnDialog(null);
        form.ifPresent(f -> guarded(() -> {
            service.addColumn(f.title(), f.description(), f.color(), f.wipLimit());
            refresh();
        }));
    }

    public void onAddCard(com.personalkanban.domain.board.ColumnId columnId) {
        Optional<Dialogs.CardForm> form = dialogs.cardDialog(
                null, service.labelVocabulary(), service.processes(),
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
            dialogs.cardDialog(initial, service.labelVocabulary(), service.processes(),
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
                service.removeCard(cardId);
                refresh();
            });
        }
    }

    public void onClearColumn(com.personalkanban.domain.board.ColumnId columnId) {
        if (dialogs.confirm(i18n.text("confirm.clear.cards"))) {
            guarded(() -> {
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
        LabelFilter filter = currentLabelFilter();
        LabelFilter quickFilter = currentQuickFlagFilter();
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
        undoRedo.sync();
        updateSelectionBarState();
        // Keep the processes-view keyboard focus across the rebuild.
        if (processView && processFocusId != null) {
            if (service.board().findCard(processFocusId).isEmpty()) {
                processFocusId = null;
            } else {
                findProcessCardNode(processFocusId).ifPresent(Node::requestFocus);
            }
        }
    }

    /** Switches between the kanban and the processes view. */
    public void onToggleView() {
        processView = !processView;
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
