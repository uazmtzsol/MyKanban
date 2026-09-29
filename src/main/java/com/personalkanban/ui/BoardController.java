package com.personalkanban.ui;

import com.personalkanban.AppContext;
import com.personalkanban.application.BoardService;
import com.personalkanban.ui.theme.ThemeManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Locale;
import java.util.Optional;

/**
 * Root controller (GRASP Controller): builds the board UI, owns all user
 * intents, and delegates every mutation to {@link BoardService}. The scene
 * graph is built in code — no FXML, no reflection, fully debuggable.
 */
public final class BoardController {

    private final AppContext context;
    private final BoardService service;
    private final I18n i18n;
    private final ThemeManager themeManager;
    private final UndoRedoController undoRedo;
    private final Dialogs dialogs;

    private final StringProperty title = new SimpleStringProperty();
    private final VBox root;
    private final HBox columnsRow;

    public BoardController(AppContext context) {
        this.context = context;
        this.service = context.boardService();
        this.i18n = new I18n(context.savedLocale());
        this.themeManager = context.themeManager();
        this.undoRedo = new UndoRedoController(service, i18n, this::refresh);
        this.dialogs = new Dialogs(i18n);
        this.columnsRow = new HBox(12);
        this.columnsRow.setPadding(new Insets(14));
        this.root = buildRoot();
        refresh();
    }

    public VBox root() {
        return root;
    }

    StringProperty titleProperty() {
        return title;
    }

    public void bindScene(Scene scene) {
        undoRedo.bindScene(scene);
    }

    void applyThemeToScene(Scene scene) {
        scene.getStylesheets().setAll(themeManager.stylesheet());
    }

    // ------------------------------------------------------------------
    // UI construction
    // ------------------------------------------------------------------

    private VBox buildRoot() {
        Label brand = new Label("Personal Kanban");
        brand.getStyleClass().add("brand");

        Button addColumn = toolButton("pk-add-column", "\u2795", "toolbar.add.column");
        addColumn.setOnAction(e -> onAddColumn());

        Button clearBoard = toolButton("pk-clear-board", "\uD83D\uDDD1", "toolbar.clear.board");
        clearBoard.setOnAction(e -> onClearBoard());

        Button undoButton = toolButton("pk-undo", "\u21B6", "toolbar.undo");
        undoButton.disableProperty().bind(undoRedo.canUndoProperty().not());
        undoButton.setOnAction(e -> undoRedo.undo());

        Button redoButton = toolButton("pk-redo", "\u21B7", "toolbar.redo");
        redoButton.disableProperty().bind(undoRedo.canRedoProperty().not());
        redoButton.setOnAction(e -> undoRedo.redo());

        Button darkMode = toolButton("pk-dark-mode", "\uD83C\uDF19", "toolbar.dark.mode");
        darkMode.setOnAction(e -> onToggleDarkMode());

        Menu languageMenu = new Menu(i18n.text("menu.language"));
        rebuildLanguageMenu(languageMenu);

        MenuBar menuBar = new MenuBar(languageMenu);
        menuBar.setUseSystemMenuBar(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(8, brand, addColumn, clearBoard, undoButton, redoButton, darkMode, spacer, menuBar);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("toolbar");
        toolbar.setPadding(new Insets(10));

        ScrollPane scroller = new ScrollPane(columnsRow);
        scroller.setFitToHeight(true);
        VBox.setVgrow(scroller, Priority.ALWAYS);

        VBox root = new VBox(menuBar, toolbar, scroller);
        root.getStyleClass().add("board-root");
        return root;
    }

    private Button toolButton(String styleClass, String glyph, String textKey) {
        Button button = new Button(glyph);
        button.getStyleClass().addAll("tool-button", styleClass);
        button.setTooltip(new Tooltip(i18n.text(textKey)));
        button.setFocusTraversable(false);
        return button;
    }

    // ------------------------------------------------------------------
    // Intents
    // ------------------------------------------------------------------

    private void onAddColumn() {
        Optional<Dialogs.ColumnForm> form = dialogs.columnDialog(null);
        form.ifPresent(f -> {
            service.addColumn(f.title(), f.description(), f.color(), f.wipLimit());
            refresh();
        });
    }

    private void onClearBoard() {
        if (dialogs.confirm(i18n.text("confirm.clear.board"))) {
            service.clearBoard();
            refresh();
        }
    }

    void onToggleDarkMode() {
        themeManager.toggle();
        context.saveTheme(themeManager.theme());
        Scene scene = root.getScene();
        if (scene != null) {
            scene.getStylesheets().setAll(themeManager.stylesheet());
        }
    }

    private void rebuildLanguageMenu(Menu menu) {
        menu.getItems().clear();
        for (Locale locale : I18n.SUPPORTED) {
            String label = locale.getDisplayLanguage(locale) + " (" + locale.getLanguage() + ")";
            MenuItem item = new MenuItem(label);
            item.setOnAction(e -> onSwitchLanguage(locale));
            menu.getItems().add(item);
        }
    }

    private void onSwitchLanguage(Locale locale) {
        context.saveLocale(locale);
        i18n.setLocale(locale);
        refresh();
    }

    // ------------------------------------------------------------------
    // Card & column intents (invoked by the view builders)
    // ------------------------------------------------------------------

    void onAddCard(com.personalkanban.domain.board.ColumnId columnId) {
        Optional<Dialogs.CardForm> form = dialogs.cardDialog(null);
        form.ifPresent(f -> {
            if (guarded(() -> service.addCard(columnId, f.title(), f.description(), f.color()))) {
                refresh();
            }
        });
    }

    void onEditCard(com.personalkanban.domain.board.CardId cardId) {
        service.board().findCard(cardId).ifPresent(card ->
                dialogs.cardDialog(new Dialogs.CardForm(card.title(), card.description(), card.color()))
                        .ifPresent(f -> {
                            if (guarded(() -> service.editCard(cardId, f.title(), f.description(), f.color()))) {
                                refresh();
                            }
                        }));
    }

    void onRemoveCard(com.personalkanban.domain.board.CardId cardId) {
        if (dialogs.confirm(i18n.text("confirm.delete.card"))) {
            if (guarded(() -> service.removeCard(cardId))) {
                refresh();
            }
        }
    }

    void onClearColumn(com.personalkanban.domain.board.ColumnId columnId) {
        if (dialogs.confirm(i18n.text("confirm.clear.cards"))) {
            if (guarded(() -> service.clearColumn(columnId))) {
                refresh();
            }
        }
    }

    void onEditColumn(com.personalkanban.domain.board.ColumnId columnId) {
        var column = service.column(columnId);
        var initial = new Dialogs.ColumnForm(column.title(), column.description(),
                column.color(), column.wipLimit());
        dialogs.columnDialog(initial).ifPresent(f -> {
            if (guarded(() -> {
                service.renameColumn(columnId, f.title());
                service.editColumn(columnId, f.description(), f.color(), f.wipLimit());
            })) {
                refresh();
            }
        });
    }

    void onRemoveColumn(com.personalkanban.domain.board.ColumnId columnId) {
        if (dialogs.confirm(i18n.text("confirm.delete.column"))) {
            if (guarded(() -> service.removeColumn(columnId))) {
                refresh();
            }
        }
    }

    void onMoveCard(com.personalkanban.domain.board.CardId cardId,
                    com.personalkanban.domain.board.ColumnId targetColumn, int targetIndex) {
        if (guarded(() -> service.moveCard(cardId, targetColumn, targetIndex))) {
            refresh();
        }
    }

    void onMoveColumn(com.personalkanban.domain.board.ColumnId columnId, int targetIndex) {
        if (guarded(() -> service.moveColumn(columnId, targetIndex))) {
            refresh();
        }
    }

    /** Runs a mutation, converts domain/persistence failures into an error dialog. */
    private boolean guarded(Runnable action) {
        try {
            action.run();
            return true;
        } catch (RuntimeException e) {
            new Alert(javafx.scene.control.Alert.AlertType.ERROR,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                    javafx.scene.control.ButtonType.CLOSE).showAndWait();
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private void refresh() {
        columnsRow.getChildren().setAll(
                ColumnViewBuilder.buildAll(service, i18n, dialogs, this, undoRedo));
    }
}
