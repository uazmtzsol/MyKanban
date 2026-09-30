package com.personalkanban.ui;

import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.Optional;

/**
 * Preferences dialog (session 4.5 request: customization options such as a
 * background image). Two decisions live here: which image file, and how
 * strongly it is dimmed to keep the board readable. "Quitar imagen" clears
 * the preference; Cancel leaves everything untouched.
 */
final class PreferencesDialog {

    /** Outcome of the dialog: null path = no background; dim in [0..0.8]. */
    record BackgroundChoice(String path, double dim) {
    }

    private final I18n i18n;

    PreferencesDialog(I18n i18n) {
        this.i18n = i18n;
    }

    /**
     * Shows the dialog. {@code initialDirectory} seeds the image chooser;
     * {@code current} holds the persisted "path|dim" setting, or null.
     */
    Optional<BackgroundChoice> show(File initialDirectory, String current) {
        String initialPath = null;
        double initialDim = 0.45;
        if (current != null && current.contains("|")) {
            String[] parts = current.split("\\|", 2);
            initialPath = parts[0].isBlank() ? null : parts[0];
            try {
                initialDim = Math.clamp(Double.parseDouble(parts[1]), 0.0, 0.8);
            } catch (NumberFormatException ignored) {
                // keep default dim
            }
        }

        Label fileLabel = new Label(initialPath == null
                ? i18n.text("prefs.background.none")
                : initialPath);
        fileLabel.getStyleClass().add("prefs-file-label");
        final double fallbackDim = initialDim;

        Button choose = new Button(i18n.text("prefs.background.choose"));
        String[] chosen = {initialPath};
        choose.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(i18n.text("prefs.background.choose"));
            chooser.setInitialDirectory(initialDirectoryOrNull(initialDirectory));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "PNG / JPG", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
            File file = chooser.showOpenDialog(choose.getScene() == null
                    ? null : choose.getScene().getWindow());
            if (file != null) {
                chosen[0] = file.getAbsolutePath();
                fileLabel.setText(chosen[0]);
            }
        });

        Slider dim = new Slider(0, 0.8, initialDim);
        dim.setShowTickLabels(true);
        dim.setBlockIncrement(0.05);
        Label dimLabel = new Label(i18n.text("prefs.background.dim"));

        CheckBox enabled = new CheckBox(i18n.text("prefs.background.enable"));
        enabled.setSelected(initialPath != null);

        Dialog<BackgroundChoice> dialog = new Dialog<>();
        dialog.setTitle(i18n.text("prefs.title"));
        dialog.setHeaderText(i18n.text("prefs.header"));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.add(enabled, 0, 0, 2, 1);
        grid.add(choose, 0, 1);
        grid.add(fileLabel, 1, 1);
        grid.add(dimLabel, 0, 2);
        grid.add(dim, 1, 2);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            if (!enabled.isSelected() || chosen[0] == null) {
                return new BackgroundChoice(null, fallbackDim); // clear preference
            }
            return new BackgroundChoice(chosen[0], dim.getValue());
        });
        return dialog.showAndWait();
    }

    private File initialDirectoryOrNull(File seed) {
        return seed != null && seed.isDirectory() ? seed : null;
    }
}
