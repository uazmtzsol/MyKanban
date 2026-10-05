package com.personalkanban.ui;

import com.personalkanban.application.BoardService;
import com.personalkanban.application.StylePrefs;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Preferences dialog (session 4.5 request: customization options such as a
 * background image; appearance request: priority and label colors). Three
 * areas live here: which image file and how strongly it is dimmed, the
 * highlight colors of the quick flags (Importante / Urgente / Importante y
 * urgente), and the colors of individual labels or label combinations — each
 * stored separately for the light and the dark theme. "Quitar imagen" clears
 * the preference; Cancel leaves everything untouched.
 */
final class PreferencesDialog {

    /** Outcome of the dialog: null path = no background; dim in [0..0.8]. */
    record BackgroundChoice(String path, double dim) {
    }

    /** One row of the label-rules list: storage key + readable name. */
    private record LabelRule(String key, String display) {
    }

    /** Edited colors of one label rule; a null field keeps the default. */
    private record LabelRuleEdit(String lightBg, String lightText,
                                 String darkBg, String darkText) {
    }

    private static final List<String> PRIORITY_STATES = List.of(
            StylePrefs.IMPORTANT, StylePrefs.URGENT, StylePrefs.URGENT_IMPORTANT);

    private final I18n i18n;
    private final BoardService service;
    private final boolean dark;

    /** Pickers of the priority section, per state: {lightBg, lightText, darkBg, darkText}. */
    private final Map<String, ColorPicker[]> priorityPickers = new LinkedHashMap<>();

    /** Rules to save (key -> edited colors) and keys to clear, applied on OK. */
    private final Map<String, LabelRuleEdit> pendingRules = new LinkedHashMap<>();
    private final List<String> pendingClears = new ArrayList<>();

    private ListView<LabelRule> ruleList;

    PreferencesDialog(I18n i18n, BoardService service, boolean dark) {
        this.i18n = i18n;
        this.service = service;
        this.dark = dark;
    }

    /**
     * Shows the dialog. {@code initialDirectory} seeds the image chooser;
     * {@code current} holds the persisted "path|dim" setting, or null.
     */
    Optional<BackgroundChoice> show(File initialDirectory, String current) {
        String initialPath = null;
        double initialDim = 0.45;
        if (current != null && current.contains("|")) {
            String[] parts = current.split("\\\\|", 2);
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

        GridPane backgroundGrid = new GridPane();
        backgroundGrid.setHgap(10);
        backgroundGrid.setVgap(12);
        backgroundGrid.add(enabled, 0, 0, 2, 1);
        backgroundGrid.add(choose, 0, 1);
        backgroundGrid.add(fileLabel, 1, 1);
        backgroundGrid.add(dimLabel, 0, 2);
        backgroundGrid.add(dim, 1, 2);

        TabPane tabs = new TabPane();
        Tab backgroundTab = new Tab(i18n.text("prefs.tab.background"), backgroundGrid);
        Tab appearanceTab = new Tab(i18n.text("prefs.tab.appearance"), appearancePane());
        backgroundTab.setClosable(false);
        appearanceTab.setClosable(false);
        tabs.getTabs().setAll(backgroundTab, appearanceTab);

        Dialog<BackgroundChoice> dialog = new Dialog<>();
        dialog.setTitle(i18n.text("prefs.title"));
        dialog.setHeaderText(i18n.text("prefs.header"));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(tabs);
        Dialogs.makeResizable(dialog, 720, 560);

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            applyAppearanceChanges();
            if (!enabled.isSelected() || chosen[0] == null) {
                return new BackgroundChoice(null, fallbackDim); // clear preference
            }
            return new BackgroundChoice(chosen[0], dim.getValue());
        });
        return dialog.showAndWait();
    }

    // ------------------------------------------------------------------
    // Appearance tab
    // ------------------------------------------------------------------

    private VBox appearancePane() {
        VBox pane = new VBox(14);
        pane.getChildren().addAll(prioritySection(), labelSection());
        ScrollPane scroll = new ScrollPane(pane);
        scroll.setFitToWidth(true);
        scroll.setPrefWidth(680);
        scroll.setPrefHeight(460);
        return new VBox(scroll);
    }

    /** Quick-flag highlight colors: one row per state, both themes. */
    private GridPane prioritySection() {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.getStyleClass().add("prefs-section");

        grid.add(new Label(i18n.text("prefs.priority.section")), 0, 0, 6, 1);
        grid.add(new Label(i18n.text("prefs.theme.light") + " — "
                + i18n.text("prefs.color.background")), 1, 1);
        grid.add(new Label(i18n.text("prefs.theme.light") + " — "
                + i18n.text("prefs.color.text")), 2, 1);
        grid.add(new Label(i18n.text("prefs.theme.dark") + " — "
                + i18n.text("prefs.color.background")), 3, 1);
        grid.add(new Label(i18n.text("prefs.theme.dark") + " — "
                + i18n.text("prefs.color.text")), 4, 1);

        int row = 2;
        for (String state : PRIORITY_STATES) {
            Label name = new Label(stateName(state));
            name.getStyleClass().add("prefs-state-name");
            ColorPicker lightBg = colorPicker(service.priorityStyle(state, false).background());
            ColorPicker lightText = colorPicker(service.priorityStyle(state, false).text());
            ColorPicker darkBg = colorPicker(service.priorityStyle(state, true).background());
            ColorPicker darkText = colorPicker(service.priorityStyle(state, true).text());
            priorityPickers.put(state, new ColorPicker[]{lightBg, lightText, darkBg, darkText});
            Button reset = new Button(i18n.text("prefs.priority.reset"));
            reset.setOnAction(e -> {
                // Back to the developer defaults (kept local until OK).
                lightBg.setValue(parseColor(service.defaultPriority(state, false).background()));
                lightText.setValue(parseColor(service.defaultPriority(state, false).text()));
                darkBg.setValue(parseColor(service.defaultPriority(state, true).background()));
                darkText.setValue(parseColor(service.defaultPriority(state, true).text()));
            });
            grid.add(name, 0, row);
            grid.add(lightBg, 1, row);
            grid.add(lightText, 2, row);
            grid.add(darkBg, 3, row);
            grid.add(darkText, 4, row);
            grid.add(reset, 5, row);
            row++;
        }
        return grid;
    }

    /** Label/combination rules: list + add/edit/remove. */
    private VBox labelSection() {
        Label caption = new Label(i18n.text("prefs.label.section"));
        caption.getStyleClass().add("prefs-state-name");

        ruleList = new ListView<>();
        ruleList.setPrefHeight(140);
        refreshRuleList();

        Button add = new Button(i18n.text("prefs.label.add"));
        add.setOnAction(e -> addRule());
        Button edit = new Button(i18n.text("prefs.label.edit"));
        edit.setOnAction(e -> editRule());
        Button remove = new Button(i18n.text("prefs.label.delete"));
        remove.setOnAction(e -> removeRule());

        javafx.scene.layout.HBox buttons = new javafx.scene.layout.HBox(8, add, edit, remove);
        return new VBox(8, caption, ruleList, buttons);
    }

    private void refreshRuleList() {
        Map<String, LabelRule> rules = new LinkedHashMap<>();
        for (String key : service.labelStyleRuleKeys()) {
            if (!pendingClears.contains(key)) {
                rules.put(key, new LabelRule(key, StylePrefs.describeRule(key)));
            }
        }
        pendingRules.forEach((key, edit) ->
                rules.put(key, new LabelRule(key, StylePrefs.describeRule(key))));
        ruleList.setItems(FXCollections.observableArrayList(
                new ArrayList<>(rules.values())));
    }

    private void addRule() {
        Optional<String> answer = dialogs().promptText(
                i18n.text("prefs.label.name.title"),
                i18n.text("prefs.label.name.prompt"));
        if (answer.isEmpty()) {
            return;
        }
        List<String> labels = Dialogs.parseLabels(answer.get());
        if (labels.isEmpty()) {
            dialogs().info(i18n.text("prefs.label.invalid"));
            return;
        }
        String key = labels.size() == 1
                ? StylePrefs.labelKey(labels.getFirst())
                : StylePrefs.combinationKey(labels);
        // An existing rule (saved or pending) is edited in place.
        LabelRuleEdit current = pendingRules.get(key);
        if (current == null) {
            current = new LabelRuleEdit(null, null, null, null);
        }
        editRuleColors(key, current).ifPresent(edit -> {
            pendingRules.put(key, edit);
            pendingClears.remove(key);
            refreshRuleList();
        });
    }

    private void editRule() {
        LabelRule selected = ruleList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        LabelRuleEdit current = pendingRules.get(selected.key());
        if (current == null) {
            current = new LabelRuleEdit(
                    service.labelStyle(selected.key(), false)
                            .map(StylePrefs.StyleColors::background).orElse(null),
                    service.labelStyle(selected.key(), false)
                            .map(StylePrefs.StyleColors::text).orElse(null),
                    service.labelStyle(selected.key(), true)
                            .map(StylePrefs.StyleColors::background).orElse(null),
                    service.labelStyle(selected.key(), true)
                            .map(StylePrefs.StyleColors::text).orElse(null));
        }
        editRuleColors(selected.key(), current).ifPresent(edit -> {
            pendingRules.put(selected.key(), edit);
            pendingClears.remove(selected.key());
            refreshRuleList();
        });
    }

    private void removeRule() {
        LabelRule selected = ruleList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        pendingRules.remove(selected.key());
        if (service.labelStyleRuleKeys().contains(selected.key())) {
            pendingClears.add(selected.key());
        }
        refreshRuleList();
    }

    /**
     * Editor for one rule: background/text for each theme. A null field
     * means "unset" (falls back to the next rule / the system colors).
     */
    private Optional<LabelRuleEdit> editRuleColors(String key, LabelRuleEdit current) {
        ColorPicker lightBg = colorPicker(current == null ? null : current.lightBg());
        ColorPicker lightText = colorPicker(current == null ? null : current.lightText());
        ColorPicker darkBg = colorPicker(current == null ? null : current.darkBg());
        ColorPicker darkText = colorPicker(current == null ? null : current.darkText());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        Label caption = new Label(StylePrefs.describeRule(key));
        caption.getStyleClass().add("prefs-state-name");
        grid.add(caption, 0, 0, 4, 1);
        grid.add(new Label(i18n.text("prefs.theme.light") + " — "
                + i18n.text("prefs.color.background")), 0, 1);
        grid.add(lightBg, 1, 1);
        grid.add(new Label(i18n.text("prefs.theme.light") + " — "
                + i18n.text("prefs.color.text")), 0, 2);
        grid.add(lightText, 1, 2);
        grid.add(new Label(i18n.text("prefs.theme.dark") + " — "
                + i18n.text("prefs.color.background")), 0, 3);
        grid.add(darkBg, 1, 3);
        grid.add(new Label(i18n.text("prefs.theme.dark") + " — "
                + i18n.text("prefs.color.text")), 0, 4);
        grid.add(darkText, 1, 4);

        Dialog<LabelRuleEdit> dialog = new Dialog<>();
        dialog.setTitle(i18n.text("prefs.label.edit.title"));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(grid);
        Dialogs.makeResizable(dialog, 420, 320);
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new LabelRuleEdit(hexOrNull(lightBg), hexOrNull(lightText),
                        hexOrNull(darkBg), hexOrNull(darkText))
                : null);
        return dialog.showAndWait();
    }

    /** Commits every pending appearance change (OK only). */
    private void applyAppearanceChanges() {
        for (String state : PRIORITY_STATES) {
            ColorPicker[] pickers = priorityPickers.get(state);
            service.setPriorityStyle(state, false,
                    hexOrNull(pickers[0]), hexOrNull(pickers[1]));
            service.setPriorityStyle(state, true,
                    hexOrNull(pickers[2]), hexOrNull(pickers[3]));
        }
        for (Map.Entry<String, LabelRuleEdit> entry : pendingRules.entrySet()) {
            LabelRuleEdit edit = entry.getValue();
            service.setLabelStyle(entry.getKey(), false, edit.lightBg(), edit.lightText());
            service.setLabelStyle(entry.getKey(), true, edit.darkBg(), edit.darkText());
        }
        for (String key : pendingClears) {
            service.clearLabelStyle(key);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** A color picker seeded from a hex value (null = a neutral start). */
    private static ColorPicker colorPicker(String hex) {
        ColorPicker picker = new ColorPicker(parseColor(hex));
        picker.setMaxWidth(Double.MAX_VALUE);
        return picker;
    }

    private static javafx.scene.paint.Color parseColor(String hex) {
        if (hex == null || hex.isBlank()) {
            return javafx.scene.paint.Color.web("#64748b");
        }
        try {
            return javafx.scene.paint.Color.web(hex);
        } catch (IllegalArgumentException e) {
            return javafx.scene.paint.Color.web("#64748b");
        }
    }

    private static String hexOrNull(ColorPicker picker) {
        return picker.getValue() == null ? null : ColorCss.toHex(picker.getValue());
    }

    private String stateName(String state) {
        if (StylePrefs.URGENT.equals(state)) {
            return i18n.text("prefs.priority.urgent");
        }
        if (StylePrefs.URGENT_IMPORTANT.equals(state)) {
            return i18n.text("prefs.priority.urgentImportant");
        }
        return i18n.text("prefs.priority.important");
    }

    /** The card dialogs' prompt/info helpers, reused for rule names. */
    private Dialogs dialogs() {
        return new Dialogs(i18n);
    }

    private File initialDirectoryOrNull(File seed) {
        return seed != null && seed.isDirectory() ? seed : null;
    }
}
