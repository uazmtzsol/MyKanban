package com.personalkanban.ui;

import com.personalkanban.AppContext;
import com.personalkanban.application.BoardService;
import com.personalkanban.domain.board.BoardColumn;
import com.personalkanban.domain.board.BoardDescriptor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.LabelFilter;
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

    // Label filter state; survives language-driven rebuilds of the controls.
    private String activeFilterLabels = "";
    private String activeFilterMode; // null (= show all), "AND" or "OR"
    private TextField labelFilterField;
    private ComboBox<String> labelFilterMode;

    public BoardController(AppContext context) {
        this.context = context;
        this.service = context.boardService();
        this.i18n = new I18n(context.savedLocale());
        this.themeManager = context.themeManager();
        this.undoRedo = new UndoRedoController(service, i18n, this::refresh);
        this.dialogs = new Dialogs(i18n);
        this.root = new VBox();
        this.root.getStyleClass().add("board-root");
        rebuildAll();
    }

    public VBox root() {
        return root;
    }

    public StringProperty titleProperty() {
        return title;
    }

    public void bindScene(Scene scene) {
        undoRedo.bindScene(scene);
    }

    // ------------------------------------------------------------------
    // Full UI construction (also used to apply a new language/theme state)
    // ------------------------------------------------------------------

    private void rebuildAll() {
        HBox toolbar = buildToolbar();
        HBox filterBar = buildFilterBar();
        MenuBar menuBar = buildMenuBar();

        columnsRow = new HBox(12);
        columnsRow.setPadding(new Insets(14));

        ScrollPane scroller = new ScrollPane(columnsRow);
        scroller.setFitToHeight(true);
        VBox.setVgrow(scroller, Priority.ALWAYS);

        root.getChildren().setAll(menuBar, toolbar, filterBar, scroller);
        title.set(i18n.text("app.title") + " \u2014 " + context.databasePath().getFileName());
        refresh();
    }

    private HBox buildToolbar() {
        Label brand = new Label("Personal Kanban");
        brand.getStyleClass().add("brand");

        Button addColumn = toolButton("\u2795", "toolbar.add.column");
        addColumn.setOnAction(e -> onAddColumn());

        Button clearBoard = toolButton("\uD83D\uDDD1", "toolbar.clear.board");
        clearBoard.setOnAction(e -> onClearBoard());

        Button undoButton = toolButton("\u21B6", "toolbar.undo");
        undoButton.disableProperty().bind(undoRedo.canUndoProperty().not());
        undoButton.setOnAction(e -> undoRedo.undo());

        Button redoButton = toolButton("\u21B7", "toolbar.redo");
        redoButton.disableProperty().bind(undoRedo.canRedoProperty().not());
        redoButton.setOnAction(e -> undoRedo.redo());

        Button darkMode = toolButton("\uD83C\uDF19", "toolbar.dark.mode");
        darkMode.setOnAction(e -> onToggleDarkMode());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(8, brand, addColumn, clearBoard, undoButton, redoButton, darkMode, spacer);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("toolbar");
        toolbar.setPadding(new Insets(10));
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

        HBox bar = new HBox(8, filterLabel, labelFilterField, labelFilterMode, clearFilter);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().addAll("toolbar", "filter-bar");
        bar.setPadding(new Insets(6, 10, 6, 10));
        return bar;
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
                new SeparatorMenuItem(),
                currentFile);
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
            dialogs.error(i18n.text("db.open.failed") + "\n" + e.getMessage());
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

    private void onNewBoard() {
        Optional<String> name = dialogs.promptText(i18n.text("board.new"), "");
        name.flatMap(this::validatedBoardName).ifPresent(n -> {
            BoardId id = service.createBoard(n);
            service.openBoard(id);
            rebuildAll();
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
            service.deleteBoard(activeId);
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

    private void onExportBoard() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.text("board.export"));
        chooser.setInitialFileName("board.json");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Personal Kanban board", "*.json"));
        java.io.File file = chooser.showSaveDialog(root.getScene() == null ? null : root.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            service.exportBoard(file.toPath());
        } catch (RuntimeException e) {
            dialogs.error(e.getMessage());
        }
    }

    private void onImportBoard() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.text("board.import"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Personal Kanban board", "*.json"));
        java.io.File file = chooser.showOpenDialog(root.getScene() == null ? null : root.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            BoardId imported = service.importBoard(file.toPath());
            service.openBoard(imported);
            rebuildAll();
        } catch (RuntimeException e) {
            dialogs.error(e.getMessage());
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

    private void onClearBoard() {
        if (dialogs.confirm(i18n.text("confirm.clear.board"))) {
            guarded(() -> {
                service.clearBoard();
                refresh();
            });
        }
    }

    public void onAddCard(com.personalkanban.domain.board.ColumnId columnId) {
        Optional<Dialogs.CardForm> form = dialogs.cardDialog(null);
        form.ifPresent(f -> guarded(() -> {
            service.addCard(columnId, f.title(), f.description(), f.color(), f.dueDate(), f.labels());
            refresh();
        }));
    }

    public void onEditCard(com.personalkanban.domain.board.CardId cardId) {
        service.board().findCard(cardId).ifPresent(card ->
                dialogs.cardDialog(new Dialogs.CardForm(card.title(), card.description(), card.color(),
                                card.dueDate(), List.copyOf(card.labels())))
                        .ifPresent(f -> guarded(() -> {
                            service.editCard(cardId, f.title(), f.description(), f.color(),
                                    f.dueDate(), f.labels());
                            refresh();
                        })));
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

    public void onMoveColumn(com.personalkanban.domain.board.ColumnId columnId, int targetIndex) {
        guarded(() -> {
            service.moveColumn(columnId, targetIndex);
            refresh();
        });
    }

    /** Runs a UI action, converting failures into an error dialog. */
    private void guarded(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            dialogs.error(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private void refresh() {
        LabelFilter filter = currentLabelFilter();
        columnsRow.getChildren().setAll(
                ColumnViewBuilder.buildAll(service, i18n, dialogs, this, undoRedo, filter));
        rebuildBoardMenu(); // keep the active-board marker in sync
    }

    private Button toolButton(String glyph, String textKey) {
        Button button = new Button(glyph);
        button.getStyleClass().addAll("tool-button");
        button.setTooltip(new Tooltip(i18n.text(textKey)));
        button.setFocusTraversable(false);
        return button;
    }
}
