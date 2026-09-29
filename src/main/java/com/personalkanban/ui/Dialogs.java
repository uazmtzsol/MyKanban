package com.personalkanban.ui;

import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.WipLimit;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ListCell;
import javafx.scene.control.Alert;
import javafx.scene.layout.GridPane;
import javafx.scene.shape.Rectangle;

import java.util.Optional;

/**
 * Owns every modal interaction (Pure Fabrication): the column form, the card
 * form, and confirmations. Keeps controller code free of dialog plumbing.
 */
final class Dialogs {

    /** Result of the column dialog. */
    record ColumnForm(String title, String description, BoardColor color, WipLimit wipLimit) {
    }

    record CardForm(String title, String description, BoardColor color) {
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
        ComboBox<BoardColor> colorBox = colorPicker(initial == null ? BoardColor.DEFAULT : initial.color());
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
        grid.add(colorBox, 1, 2);
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
                    colorBox.getValue(), wipLimit);
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
        ComboBox<BoardColor> colorBox = colorPicker(initial == null ? BoardColor.DEFAULT : initial.color());

        Dialog<CardForm> dialog = new Dialog<>();
        dialog.setTitle(i18n.text("dialog.card.title"));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        GridPane grid = formGrid();
        grid.add(new Label(i18n.text("dialog.title.label")), 0, 0);
        grid.add(titleField, 1, 0);
        addDescriptionAndColorRows(grid, descriptionArea, colorBox);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            String title = titleField.getText();
            if (title == null || title.isBlank()) {
                return null;
            }
            return new CardForm(title, descriptionArea.getText(), colorBox.getValue());
        });

        return dialog.showAndWait();
    }

    private void addDescriptionAndColorRows(GridPane grid, TextArea descriptionArea, ComboBox<BoardColor> colorBox) {
        grid.add(new Label(i18n.text("dialog.description")), 0, 1);
        grid.add(descriptionArea, 1, 1);
        grid.add(new Label(i18n.text("dialog.color")), 0, 2);
        grid.add(colorBox, 1, 2);
    }

    // ------------------------------------------------------------------
    // Confirmation
    // ------------------------------------------------------------------

    boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(null);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
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

    private ComboBox<BoardColor> colorPicker(BoardColor current) {
        ComboBox<BoardColor> comboBox = new ComboBox<>();
        comboBox.getItems().setAll(BoardColor.values());
        comboBox.setValue(current);
        comboBox.setCellFactory(listView -> new ColorListCell());
        comboBox.setButtonCell(new ColorListCell());
        return comboBox;
    }

    /** Shows a swatch + name for each palette entry. */
    private static final class ColorListCell extends ListCell<BoardColor> {
        @Override
        protected void updateItem(BoardColor item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Rectangle swatch = new Rectangle(14, 14);
            swatch.getStyleClass().add(ColorCss.styleClass(item) + "-swatch");
            swatch.getStyleClass().add("color-swatch");
            setText(item.name());
            setGraphic(swatch);
        }
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
