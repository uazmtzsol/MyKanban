package com.personalkanban.ui;

import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.WipLimit;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;

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

    /** Result of the card dialog. */
    record CardForm(String title, String description, BoardColor color,
                    LocalDate dueDate, List<String> labels) {
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

    Optional<CardForm> cardDialog(CardForm initial) {
        TextField titleField = new TextField(initial == null ? "" : initial.title());
        TextArea descriptionArea = new TextArea(initial == null ? "" : initial.description());
        descriptionArea.setPrefRowCount(3);
        ColorPicker colorPicker = colorPicker(initial == null ? BoardColor.DEFAULT : initial.color());
        DatePicker dueDatePicker = new DatePicker(initial == null ? null : initial.dueDate());
        dueDatePicker.setPromptText(i18n.text("card.due.none"));
        TextField labelsField = new TextField(initial == null || initial.labels().isEmpty()
                ? "" : String.join(", ", initial.labels()));
        labelsField.setPromptText(i18n.text("card.labels.prompt"));

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
                    dueDatePicker.getValue(), parseLabels(labelsField.getText()));
        });

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
        TextInputDialog dialog = new TextInputDialog(initial == null ? "" : initial);
        dialog.setHeaderText(header);
        dialog.setContentText(i18n.text("dialog.title.label"));
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

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        return grid;
    }

    /** Splits comma-separated labels into the normalized list the domain expects. */
    static List<String> parseLabels(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split(","))
                .map(String::strip)
                .filter(part -> !part.isEmpty())
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
