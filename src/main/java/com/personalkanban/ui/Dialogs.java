package com.personalkanban.ui;

import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardColumn;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.LabelSuggester;
import com.personalkanban.domain.board.WipLimit;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Owns every modal interaction (Pure Fabrication): the column form, the card
 * form (with due date and labels), board name prompts, and confirmations.
 * Keeps controller code free of dialog plumbing.
 */
final class Dialogs {

    /** Result of the column dialog. */
    record ColumnForm(String title, String description, BoardColor color, WipLimit wipLimit) {
    }

    /** Result of the card dialog (session 5: advanced fields added). */
    record CardForm(String title, String description, BoardColor color,
                    LocalDate dueDate, List<String> labels,
                    String notes, List<String> checklistLines, com.personalkanban.domain.board.ProcessId processId,
                    List<com.personalkanban.domain.board.CardId> predecessors,
                    List<com.personalkanban.domain.board.CardId> successors) {

        /** Session-5 shape without relation fields. */
        CardForm(String title, String description, BoardColor color,
                 LocalDate dueDate, List<String> labels,
                 String notes, List<String> checklistLines, com.personalkanban.domain.board.ProcessId processId) {
            this(title, description, color, dueDate, labels, notes, checklistLines, processId,
                    List.of(), List.of());
        }

        /** Backward-compatible shape: minimal fields only. */
        CardForm(String title, String description, BoardColor color,
                 LocalDate dueDate, List<String> labels) {
            this(title, description, color, dueDate, labels, "", List.of(), null, List.of(), List.of());
        }
    }

    /** Result of the bulk labels dialog: which operation and which labels. */
    record BulkLabelsForm(boolean add, List<String> labels) {
    }

    private final I18n i18n;

    Dialogs(I18n i18n) {
        this.i18n = i18n;
    }

    // ------------------------------------------------------------------
    // Column dialog
    // ------------------------------------------------------------------

    Optional<ColumnForm> columnDialog(ColumnForm initial) {
        TextField titleField = new TextField(initial == null ? "" : initial.title());
        TextField descriptionField = new TextField(initial == null ? "" : initial.description());
        ColorPicker colorPicker = colorPicker(initial == null ? BoardColor.DEFAULT : initial.color());
        TextField wipField = new TextField(initial == null || initial.wipLimit().isUnlimited()
                ? "" : String.valueOf(initial.wipLimit().asOptional().get()));
        wipField.setPromptText(i18n.text("dialog.wip.prompt"));

        Dialog<ColumnForm> dialog = new Dialog<>();
        dialog.setTitle(i18n.text("dialog.column.title"));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        GridPane grid = formGrid();
        grid.add(new Label(i18n.text("dialog.title.label")), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label(i18n.text("dialog.description")), 0, 1);
        grid.add(descriptionField, 1, 1);
        grid.add(new Label(i18n.text("dialog.color")), 0, 2);
        grid.add(colorPicker, 1, 2);
        grid.add(new Label(i18n.text("dialog.wip")), 0, 3);
        grid.add(wipField, 1, 3);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            Integer wip = parseWip(wipField.getText());
            if (wip == null) {
                return null;
            }
            WipLimit wipLimit = wip == 0 ? WipLimit.unlimited() : WipLimit.of(wip);
            return new ColumnForm(titleField.getText(), descriptionField.getText(),
                    selectedColor(colorPicker), wipLimit);
        });

        return dialog.showAndWait();
    }

    // ------------------------------------------------------------------
    // Card dialog
    // ------------------------------------------------------------------

    Optional<CardForm> cardDialog(CardForm initial, List<String> labelVocabulary,
                                  List<com.personalkanban.domain.board.Process> processes,
                                  List<com.personalkanban.domain.board.Card> otherCards,
                                  List<com.personalkanban.domain.board.CardId> currentPredecessors,
                                  List<com.personalkanban.domain.board.CardId> currentSuccessors) {
        TextField titleField = new TextField(initial == null ? "" : initial.title());
        TextArea descriptionArea = new TextArea(initial == null ? "" : initial.description());
        descriptionArea.setPrefRowCount(3);
        ColorPicker colorPicker = colorPicker(initial == null ? BoardColor.DEFAULT : initial.color());
        DatePicker dueDatePicker = new DatePicker(initial == null ? null : initial.dueDate());
        dueDatePicker.setPromptText(i18n.text("card.due.none"));
        TextField labelsField = new TextField(initial == null || initial.labels().isEmpty()
                ? "" : String.join(", ", initial.labels()));
        labelsField.setPromptText(i18n.text("card.labels.prompt"));
        List<String> ownLabels = initial == null ? List.of() : initial.labels();
        LabelAutoComplete.attach(labelsField, new LabelSuggester(labelVocabulary), () -> ownLabels);

        // --- Advanced (collapsible) section — user request: the dialog shows
        //     the minimal useful fields by default; one checklist-shaped
        //     toggle reveals notes, checklist and process. ---
        TextArea notesArea = new TextArea(initial == null ? "" : initial.notes());
        notesArea.setPromptText(i18n.text("card.notes.prompt"));
        notesArea.setPrefRowCount(2);
        notesArea.setWrapText(true);
        TextArea checklistArea = new TextArea(initial == null || initial.checklistLines().isEmpty()
                ? "" : String.join("\n", initial.checklistLines()));
        checklistArea.setPromptText(i18n.text("checklist.dialog.prompt"));
        checklistArea.setPrefRowCount(3);
        checklistArea.setWrapText(true);
        javafx.scene.control.ComboBox<com.personalkanban.domain.board.Process> processCombo =
                new javafx.scene.control.ComboBox<>(
                        javafx.collections.FXCollections.observableArrayList(processes));
        processCombo.setPromptText(i18n.text("card.process.none"));
        processCombo.setMaxWidth(Double.MAX_VALUE);
        processCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(com.personalkanban.domain.board.Process process) {
                return process == null ? "" : process.name();
            }

            @Override
            public com.personalkanban.domain.board.Process fromString(String string) {
                return null;
            }
        });
        if (initial != null && initial.processId() != null) {
            processes.stream()
                    .filter(process -> process.id().equals(initial.processId()))
                    .findFirst()
                    .ifPresent(processCombo.getSelectionModel()::select);
        }

        javafx.scene.control.Label notesCaption =
                new javafx.scene.control.Label(i18n.text("card.tab.notes"));
        javafx.scene.control.Label checklistCaption =
                new javafx.scene.control.Label(i18n.text("card.tab.checklist"));
        checklistCaption.setTooltip(new javafx.scene.control.Tooltip(
                i18n.text("checklist.dialog.help")));
        javafx.scene.control.Label processCaption =
                new javafx.scene.control.Label(i18n.text("card.process.label"));
        javafx.scene.layout.VBox advancedBox = new javafx.scene.layout.VBox(
                8, notesCaption, notesArea, checklistCaption, checklistArea,
                processCaption, processCombo);

        // --- Precedence links (user request ②): which tasks come BEFORE and
        //     AFTER this one. Two multi-selection lists of the other cards;
        //     the controller diffs them into link/unlink transactions. ---
        java.util.LinkedHashMap<String, com.personalkanban.domain.board.CardId> candidates =
                new java.util.LinkedHashMap<>();
        for (com.personalkanban.domain.board.Card candidate : otherCards) {
            candidates.put(candidate.title(), candidate.id());
        }
        java.util.List<com.personalkanban.domain.board.CardId> oldPredecessors =
                currentPredecessors == null ? List.of() : currentPredecessors;
        javafx.scene.control.ListView<String> predecessorsList =
                new javafx.scene.control.ListView<>(
                        javafx.collections.FXCollections.observableArrayList(candidates.keySet()));
        javafx.scene.control.ListView<String> successorsList =
                new javafx.scene.control.ListView<>(
                        javafx.collections.FXCollections.observableArrayList(candidates.keySet()));
        predecessorsList.setPrefHeight(90);
        successorsList.setPrefHeight(90);
        predecessorsList.getSelectionModel().setSelectionMode(
                javafx.scene.control.SelectionMode.MULTIPLE);
        successorsList.getSelectionModel().setSelectionMode(
                javafx.scene.control.SelectionMode.MULTIPLE);
        predecessorsList.setCellFactory(view -> relationCell(oldPredecessors, candidates));
        successorsList.setCellFactory(view -> relationCell(
                currentSuccessors == null ? List.of() : currentSuccessors, candidates));
        oldPredecessors.forEach(id -> candidates.entrySet().stream()
                .filter(entry -> entry.getValue().equals(id))
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .ifPresent(title -> predecessorsList.getSelectionModel().select(title)));
        oldPredecessors.forEach(id -> candidates.entrySet().stream()
                .filter(entry -> entry.getValue().equals(id))
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .ifPresent(title -> predecessorsList.getSelectionModel().select(title)));
        javafx.scene.control.Label predecessorsCaption =
                new javafx.scene.control.Label(i18n.text("card.links.predecessors"));
        javafx.scene.control.Label successorsCaption =
                new javafx.scene.control.Label(i18n.text("card.links.successors"));
        javafx.scene.layout.VBox relationsBox = new javafx.scene.layout.VBox(
                4, predecessorsCaption, predecessorsList, successorsCaption, successorsList);

        advancedBox.getChildren().add(relationsBox);

        javafx.scene.control.ToggleButton advancedToggle =
                new javafx.scene.control.ToggleButton("\u2611 " + i18n.text("card.advanced.toggle"));
        advancedToggle.getStyleClass().addAll("tool-button", "card-advanced-toggle");
        advancedBox.visibleProperty().bind(advancedToggle.selectedProperty());
        advancedBox.managedProperty().bind(advancedToggle.selectedProperty());

        Dialog<CardForm> dialog = new Dialog<>();
        dialog.setTitle(i18n.text("dialog.card.title"));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        GridPane grid = formGrid();
        grid.add(new Label(i18n.text("dialog.title.label")), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label(i18n.text("dialog.description")), 0, 1);
        grid.add(descriptionArea, 1, 1);
        grid.add(new Label(i18n.text("dialog.color")), 0, 2);
        grid.add(colorPicker, 1, 2);
        grid.add(new Label(i18n.text("card.due")), 0, 3);
        grid.add(dueDatePicker, 1, 3);
        grid.add(new Label(i18n.text("card.labels")), 0, 4);
        grid.add(labelsField, 1, 4);
        grid.add(advancedToggle, 0, 5, 2, 1);
        grid.add(advancedBox, 0, 6, 2, 1);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            String title = titleField.getText();
            if (title == null || title.isBlank()) {
                return null;
            }
            return new CardForm(title, descriptionArea.getText(), selectedColor(colorPicker),
                    dueDatePicker.getValue(), parseLabels(labelsField.getText()),
                    notesArea.getText(), parseChecklistLines(checklistArea.getText()),
                    processCombo.getValue() == null ? null : processCombo.getValue().id(),
                    selectedIds(predecessorsList, candidates),
                    selectedIds(successorsList, candidates));
        });

        return dialog.showAndWait();
    }

    /** Cell that pre-checks the currently linked cards (✔ prefix). */
    private javafx.scene.control.ListCell<String> relationCell(
            List<com.personalkanban.domain.board.CardId> linked,
            java.util.LinkedHashMap<String, com.personalkanban.domain.board.CardId> candidates) {
        return new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                com.personalkanban.domain.board.CardId id = candidates.get(item);
                boolean isLinked = id != null && linked.stream()
                        .anyMatch(linkedId -> linkedId.equals(id));
                setText(isLinked ? "\u2714 " + item : item);
            }
        };
    }

    private List<com.personalkanban.domain.board.CardId> selectedIds(
            javafx.scene.control.ListView<String> list,
            java.util.LinkedHashMap<String, com.personalkanban.domain.board.CardId> candidates) {
        return list.getSelectionModel().getSelectedItems().stream()
                .map(candidates::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * Checklist text area → lines: "[x] task" = done, "[ ] task"/"task" =
     * pending. Blank lines are dropped; the markers survive as text so the
     * domain's line parser reads them.
     */
    static List<String> parseChecklistLines(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split("\\R"))
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    // ------------------------------------------------------------------
    // Bulk (multi-selection) dialogs
    // ------------------------------------------------------------------

    /** Labels for N selected cards: one field, add-or-remove selector. */
    Optional<BulkLabelsForm> bulkLabelsDialog(int selectedCount, List<String> labelVocabulary) {
        TextField labelsField = new TextField();
        labelsField.setPromptText(i18n.text("bulk.labels.prompt"));
        LabelAutoComplete.attach(labelsField, new LabelSuggester(labelVocabulary), List::of);

        ComboBox<String> mode = new ComboBox<>();
        mode.getItems().addAll(i18n.text("bulk.add"), i18n.text("bulk.remove"));
        mode.getSelectionModel().selectFirst();

        Dialog<BulkLabelsForm> dialog = new Dialog<>();
        dialog.setTitle(i18n.text("bulk.labels.title"));
        dialog.setHeaderText(i18n.text("bulk.labels.header", selectedCount));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        GridPane grid = formGrid();
        grid.add(mode, 0, 0);
        grid.add(labelsField, 1, 0);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new BulkLabelsForm(mode.getSelectionModel().getSelectedIndex() == 0,
                        parseLabels(labelsField.getText()))
                : null);
        return dialog.showAndWait();
    }

    /** One color applied to every selected card. */
    Optional<BoardColor> bulkColorDialog(int selectedCount) {
        ColorPicker picker = colorPicker(BoardColor.DEFAULT);
        picker.getCustomColors().setAll(BoardColor.palette().stream()
                .map(color -> Color.web(color.hex()))
                .toList());

        Dialog<BoardColor> dialog = new Dialog<>();
        dialog.setTitle(i18n.text("bulk.color.title"));
        dialog.setHeaderText(i18n.text("bulk.color.header", selectedCount));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        GridPane grid = formGrid();
        grid.add(new Label(i18n.text("dialog.color")), 0, 0);
        grid.add(picker, 1, 0);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button ->
                button == ButtonType.OK ? selectedColor(picker) : null);
        return dialog.showAndWait();
    }

    /** Target column chooser for a bulk move; defaults to the first other column. */
    Optional<ColumnId> bulkMoveDialog(List<BoardColumn> columns, ColumnId sourceColumnId,
                                      int selectedCount) {
        ComboBox<BoardColumn> combo = new ComboBox<>(
                FXCollections.observableArrayList(columns));
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(BoardColumn column) {
                return column == null ? "" : column.title();
            }

            @Override
            public BoardColumn fromString(String string) {
                return null;
            }
        });
        columns.stream()
                .filter(column -> !column.id().equals(sourceColumnId))
                .findFirst()
                .ifPresent(column -> combo.getSelectionModel().select(column));

        Dialog<ColumnId> dialog = new Dialog<>();
        dialog.setTitle(i18n.text("bulk.move.title"));
        dialog.setHeaderText(i18n.text("bulk.move.header", selectedCount));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        GridPane grid = formGrid();
        grid.add(new Label(i18n.text("bulk.move.target")), 0, 0);
        grid.add(combo, 1, 0);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button ->
                button == ButtonType.OK && combo.getValue() != null
                        ? combo.getValue().id()
                        : null);
        return dialog.showAndWait();
    }

    // ------------------------------------------------------------------
    // Color selection: named palette + free custom color in one control
    // ------------------------------------------------------------------

    /**
     * A ColorPicker preloaded with the 8 named palette swatches. Users pick a
     * named color from the grid or any custom color from the spectrum; the
     * tooltip shows the resolved name/hex.
     */
    private ColorPicker colorPicker(BoardColor current) {
        ColorPicker picker = new ColorPicker(Color.web(current.hex()));
        picker.getCustomColors().setAll(BoardColor.palette().stream()
                .map(color -> Color.web(color.hex()))
                .toList());
        picker.valueProperty().addListener((obs, old, value) ->
                picker.setTooltip(new Tooltip(BoardColor.fromHex(ColorCss.toHex(value)).displayName())));
        picker.setTooltip(new Tooltip(current.displayName()));
        return picker;
    }

    private BoardColor selectedColor(ColorPicker picker) {
        Color value = picker.getValue();
        return value == null ? BoardColor.DEFAULT : BoardColor.fromHex(ColorCss.toHex(value));
    }

    // ------------------------------------------------------------------
    // Prompts, confirmations, messages
    // ------------------------------------------------------------------

    Optional<String> promptText(String header, String initial) {
        return promptText(header, i18n.text("dialog.title.label"), initial);
    }

    /** Free-text prompt with a custom content label. */
    Optional<String> promptText(String header, String contentText, String initial) {
        TextInputDialog dialog = new TextInputDialog(initial == null ? "" : initial);
        dialog.setHeaderText(header);
        dialog.setContentText(contentText);
        return dialog.showAndWait();
    }

    boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(null);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    void info(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    void error(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.CLOSE);
        alert.setHeaderText(i18n.text("error.title"));
        alert.showAndWait();
    }

    /**
     * A single-line failure summary for error dialogs. Plain messages often
     * hide the real cause (e.g. a generic save failure wrapping a SQLite
     * constraint violation), so the deepest root cause is appended — that is
     * usually the actionable part.
     */
    static String describeFailure(RuntimeException failure) {
        String top = failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName()
                : failure.getMessage();
        Throwable cause = failure.getCause();
        while (cause != null && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause != null && cause.getMessage() != null
                && !cause.getMessage().isBlank()
                && !cause.getMessage().equals(top)) {
            return top + "\n\n" + cause.getMessage();
        }
        return top;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        return grid;
    }

    /**
     * Splits labels separated by commas OR whitespace, in any mix and
     * amount: {@code "et1 et2 et3"}, {@code "et1,et2,et3"} and
     * {@code "et1,   et2  et3"} all yield [et1, et2, et3]. Used by the card
     * dialog, the bulk dialogs and the board filter.
     */
    static List<String> parseLabels(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split("[,\\s\uFF0C]+"))
                .map(String::strip)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    // ------------------------------------------------------------------
    // New-board column seeding
    // ------------------------------------------------------------------

    /** How the columns of a new board are chosen. */
    enum NewBoardColumns { STANDARD, CUSTOM, EMPTY }

    /**
     * Asks how to seed the columns of a new board. The standard Kanban
     * template (three columns) is preselected because it is the most common
     * choice; the labels are localized and the answer is mapped back by
     * label, not by enum name.
     */
    Optional<NewBoardColumns> newBoardColumnsDialog() {
        String standard = i18n.text("board.columns.choice.standard");
        String custom = i18n.text("board.columns.choice.custom");
        String empty = i18n.text("board.columns.choice.empty");
        ChoiceDialog<String> dialog = new ChoiceDialog<>(standard,
                List.of(standard, custom, empty));
        dialog.setTitle(i18n.text("board.new"));
        dialog.setHeaderText(i18n.text("board.columns.choice.header"));
        dialog.setContentText(i18n.text("board.columns.choice.label"));
        return dialog.showAndWait().map(answer -> {
            if (answer.equals(custom)) {
                return NewBoardColumns.CUSTOM;
            }
            if (answer.equals(empty)) {
                return NewBoardColumns.EMPTY;
            }
            return NewBoardColumns.STANDARD;
        });
    }

    /** Upper bound of columns a new board can be seeded with. */
    static final int MAX_NEW_BOARD_COLUMNS = 12;

    /**
     * Parses the "new board columns" answer, accepting both forms the user
     * was offered: a plain number ({@code "4"}) yields that many
     * default-named columns ({@code defaultName} localizes "Columna 1"...),
     * otherwise the text is comma-separated column titles ({@code "Por
     * hacer, Haciendo, Hecho"}). Comma-only splitting on purpose: column
     * titles may contain spaces.
     *
     * @return the column titles; {@code List.of()} for blank input; {@code
     *         null} when the input is a number outside 1..{link
     *         #MAX_NEW_BOARD_COLUMNS} (caller shows a validation message).
     */
    static List<String> parseNewBoardColumns(String raw,
                                             java.util.function.IntFunction<String> defaultName) {
        String text = raw == null ? "" : raw.strip();
        if (text.isEmpty()) {
            return List.of();
        }
        if (text.matches("\\d{1,3}")) {
            int count = Integer.parseInt(text);
            if (count < 1 || count > MAX_NEW_BOARD_COLUMNS) {
                return null;
            }
            return java.util.stream.IntStream.rangeClosed(1, count)
                    .mapToObj(defaultName)
                    .toList();
        }
        return Arrays.stream(text.split(","))
                .map(String::strip)
                .filter(title -> !title.isEmpty())
                .toList();
    }

    /** Parses WIP input; null means invalid; 0 means unlimited. */
    Integer parseWip(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(text.strip());
            if (value < 0 || value > 999) {
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
