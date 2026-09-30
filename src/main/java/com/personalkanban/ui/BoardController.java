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
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
    private Menu boardMenu;
    private Menu databaseMenu;
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
        HBox filterBar = buildFilterBar();
        MenuBar menuBar = buildMenuBar();

        columnsRow = new HBox(12);
        columnsRow.setPadding(new Insets(14));

        ScrollPane scroller = new ScrollPane(columnsRow);
        scroller.setFitToHeight(true);
        VBox.setVgrow(scroller, Priority.ALWAYS);

        buildSelectionBar();
        root.getChildren().setAll(menuBar, toolbar, selectionBar, filterBar, scroller);
        updateBoardIdentity();
        refresh();
    }

    private HBox buildToolbar() {
        Label brand = new Label("Personal Kanban");
        brand.getStyleClass().add("brand");

        Button addColumn = toolButton("\u2795", "toolbar.add.column");
        addColumn.setOnAction(e -> onAddColumn());

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

        HBox toolbar = new HBox(8, brand, boardNameLabel, addColumn,
                undoButton, redoButton, darkMode, exportPdf, cardViewMenu, spacer);
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
            refresh();
        });

        javafx.scene.control.ToggleButton importantFilter = quickFlagFilterButton(
                "\u2605", com.personalkanban.domain.board.Card.LABEL_IMPORTANT, "filter.flag.important");
        javafx.scene.control.ToggleButton urgentFilter = quickFlagFilterButton(
                "!", com.personalkanban.domain.board.Card.LABEL_URGENT, "filter.flag.urgent");

        HBox bar = new HBox(8, filterLabel, labelFilterField, labelFilterMode, clearFilter,
                importantFilter, urgentFilter);
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

            Button moveButton = toolButton("\u27A1", "bulk.move");
            moveButton.setOnAction(e -> onBulkMove());

            Button removeButton = toolButton("\uD83D\uDDD1", "bulk.remove.action");
            removeButton.setOnAction(e -> onBulkRemove());

            Label count = new Label();
            count.getStyleClass().add("selection-count");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            selectionBar.getChildren().setAll(
                    exit, labelButton, colorButton, moveButton, removeButton,
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
        return new MenuBar(databaseMenu, boardMenu, languageMenu);
    }

    private void rebuildDatabaseMenu() {
        if (databaseMenu == null) {
            return;
        }
        MenuItem currentFile = new MenuItem(i18n.text("db.current") + " "
                + context.databasePath());
        currentFile.setDisable(true);
        databaseMenu.getItems().setAll(
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

        databaseMenu.getItems().addAll(new SeparatorMenuItem(), currentFile);
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
            for (String title : columnTitles) {
                service.addColumn(title, "", BoardColor.DEFAULT, WipLimit.unlimited());
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
        Optional<Dialogs.CardForm> form = dialogs.cardDialog(null, service.labelVocabulary());
        form.ifPresent(f -> guarded(() -> {
            service.addCard(columnId, f.title(), f.description(), f.color(), f.dueDate(), f.labels());
            refresh();
        }));
    }

    public void onEditCard(com.personalkanban.domain.board.CardId cardId) {
        service.board().findCard(cardId).ifPresent(card ->
                dialogs.cardDialog(new Dialogs.CardForm(card.title(), card.description(), card.color(),
                        card.dueDate(), List.copyOf(card.labels())), service.labelVocabulary())
                        .ifPresent(f -> guarded(() -> {
                            service.editCard(cardId, f.title(), f.description(), f.color(),
                                    f.dueDate(), f.labels());
                            refresh();
                        })));
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
                column.color(), column.wipLimit());
        dialogs.columnDialog(initial).ifPresent(f -> guarded(() -> {
            service.renameColumn(columnId, f.title());
            service.editColumn(columnId, f.description(), f.color(), f.wipLimit());
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

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private void refresh() {
        LabelFilter filter = currentLabelFilter();
        LabelFilter quickFilter = currentQuickFlagFilter();
        columnsRow.getChildren().setAll(
                ColumnViewBuilder.buildAll(service, i18n, dialogs, this, undoRedo, filter, quickFilter,
                        selectionMode, collapsedColumns, themeManager.isDark(), columnWidths));
        rebuildBoardMenu(); // keep the active-board marker in sync
        syncCardViewMenu();
        undoRedo.sync();
        updateSelectionBarState();
    }

    private Button toolButton(String glyph, String textKey) {
        Button button = new Button(glyph);
        button.getStyleClass().addAll("tool-button");
        button.setTooltip(new Tooltip(i18n.text(textKey)));
        button.setFocusTraversable(false);
        return button;
    }
}
