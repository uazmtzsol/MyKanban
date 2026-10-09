package com.personalkanban.ui;

import com.personalkanban.application.BoardService;
import com.personalkanban.application.StylePrefs;
import com.personalkanban.application.ThemeColors;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Preferences dialog (appearance: priority and label colors). Two areas live
 * here: the highlight colors of the quick flags (Importante / Urgente /
 * Importante y urgente), and the colors of individual labels or label
 * combinations — each stored separately for the light and the dark theme.
 * Cancel leaves everything untouched.
 *
 * <p>Session 11 (S11-1): the background-image tab was removed at the user's
 * request — the feature is gone entirely, not hidden.</p>
 */
final class PreferencesDialog {

    /** One row of the label-rules list: storage key + readable name. */
    private record LabelRule(String key, String display) {
    }

    /** The five scheme slots, in dialog order, with their i18n key suffix. */
    private static final ThemeColors.Slot[] SCHEME_SLOTS = {
            ThemeColors.Slot.BG, ThemeColors.Slot.COLUMN, ThemeColors.Slot.CARD,
            ThemeColors.Slot.TEXT, ThemeColors.Slot.SELECTED};

    /** Scheme pickers: slot -> {lightPicker, darkPicker}. */
    private final Map<ThemeColors.Slot, ColorPicker[]> schemePickers = new LinkedHashMap<>();

    /** Live preview nodes, restyled on every picker change. */
    private final List<javafx.scene.Node> previewNodes = new ArrayList<>();

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

    /** Shows the dialog; OK commits the appearance changes, Cancel discards them. */
    void show() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(i18n.text("prefs.title"));
        dialog.setHeaderText(i18n.text("prefs.header"));
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(appearancePane());
        Dialogs.makeResizable(dialog, 720, 560);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                applyAppearanceChanges();
            }
            return null;
        });
        dialog.showAndWait();
    }

    // ------------------------------------------------------------------
    // Appearance tab
    // ------------------------------------------------------------------

    private VBox appearancePane() {
        VBox pane = new VBox(14);
        pane.getChildren().addAll(schemeSection(), prioritySection(), labelSection());
        ScrollPane scroll = new ScrollPane(pane);
        scroll.setFitToWidth(true);
        scroll.setPrefWidth(680);
        scroll.setPrefHeight(460);
        return new VBox(scroll);
    }

    // ------------------------------------------------------------------
    // Color scheme section (session 11, S11-2)
    // ------------------------------------------------------------------

    /**
     * The user color scheme: one row per slot (board/column/card/text/
     * selected card), light and dark pickers, a "restore defaults" button
     * and a live mini-board preview that repaints on every change.
     */
    private VBox schemeSection() {
        Label caption = new Label(i18n.text("prefs.scheme.section"));
        caption.getStyleClass().add("prefs-state-name");

        ThemeColors colors = service.themeColors();
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new Label(i18n.text("prefs.theme.light")), 1, 0);
        grid.add(new Label(i18n.text("prefs.theme.dark")), 2, 0);
        int row = 1;
        for (ThemeColors.Slot slot : SCHEME_SLOTS) {
            grid.add(new Label(i18n.text("prefs.scheme." + slot.key())), 0, row);
            ColorPicker light = colorPicker(colors.get(slot, false));
            ColorPicker dark = colorPicker(colors.get(slot, true));
            schemePickers.put(slot, new ColorPicker[]{light, dark});
            light.valueProperty().addListener((obs, old, value) -> updatePreview());
            dark.valueProperty().addListener((obs, old, value) -> updatePreview());
            grid.add(light, 1, row);
            grid.add(dark, 2, row);
            row++;
        }

        Button reset = new Button(i18n.text("prefs.scheme.reset"));
        reset.setOnAction(e -> {
            // Back to the developer defaults, kept local until OK.
            for (ThemeColors.Slot slot : SCHEME_SLOTS) {
                schemePickers.get(slot)[0].setValue(
                        parseColor(ThemeColors.defaultOf(slot, false)));
                schemePickers.get(slot)[1].setValue(
                        parseColor(ThemeColors.defaultOf(slot, true)));
            }
            updatePreview();
        });

        VBox preview = schemePreview();
        HBox controls = new HBox(8, reset);
        return new VBox(8, caption, grid, controls,
                new Label(i18n.text("prefs.scheme.preview")), preview);
    }

    /**
     * Mini board preview: one column on the board background, two cards —
     * one plain, one "currently selected" — all painted live from the
     * pickers, in the theme the user is currently working with, so every
     * change is seen immediately.
     */
    private VBox schemePreview() {
        ThemeColors colors = schemeColorsFromPickers();

        Label title = new Label(i18n.text("prefs.scheme.preview.card"));
        title.setStyle(textStyle(colors, ThemeColors.Slot.TEXT, dark));
        Label selectedTitle = new Label(i18n.text("prefs.scheme.preview.selected"));
        selectedTitle.setStyle(textStyle(colors, ThemeColors.Slot.TEXT, dark));

        VBox card = new VBox(title);
        card.setStyle(surfaceStyle(colors, ThemeColors.Slot.CARD, false, dark));
        card.setPadding(new javafx.geometry.Insets(8));

        VBox selected = new VBox(selectedTitle);
        selected.setStyle(surfaceStyle(colors, ThemeColors.Slot.CARD, true, dark));
        selected.setPadding(new javafx.geometry.Insets(8));

        VBox column = new VBox(6, card, selected);
        column.setStyle(surfaceStyle(colors, ThemeColors.Slot.COLUMN, false, dark));
        column.setPadding(new javafx.geometry.Insets(8));
        column.setMaxWidth(320);

        VBox board = new VBox(column);
        board.setStyle(surfaceStyle(colors, ThemeColors.Slot.BG, false, dark));
        board.setPadding(new javafx.geometry.Insets(10));

        previewNodes.clear();
        previewNodes.addAll(List.of(board, column, card, selected, title, selectedTitle));
        return board;
    }

    /** Re-reads the pickers and repaints the live preview. */
    private void updatePreview() {
        if (previewNodes.size() < 5) {
            return; // preview not built yet
        }
        ThemeColors colors = schemeColorsFromPickers();
        javafx.scene.layout.VBox board = (javafx.scene.layout.VBox) previewNodes.get(0);
        javafx.scene.layout.VBox column = (javafx.scene.layout.VBox) previewNodes.get(1);
        javafx.scene.layout.VBox card = (javafx.scene.layout.VBox) previewNodes.get(2);
        javafx.scene.layout.VBox selected = (javafx.scene.layout.VBox) previewNodes.get(3);
        Label title = (Label) previewNodes.get(4);
        Label selectedTitle = (Label) previewNodes.get(5);
        board.setStyle(surfaceStyle(colors, ThemeColors.Slot.BG, false, dark));
        column.setStyle(surfaceStyle(colors, ThemeColors.Slot.COLUMN, false, dark));
        card.setStyle(surfaceStyle(colors, ThemeColors.Slot.CARD, false, dark));
        selected.setStyle(surfaceStyle(colors, ThemeColors.Slot.CARD, true, dark));
        title.setStyle(textStyle(colors, ThemeColors.Slot.TEXT, dark));
        selectedTitle.setStyle(textStyle(colors, ThemeColors.Slot.TEXT, dark));
    }

    /** The scheme as the pickers currently show it (preview + OK source). */
    private ThemeColors schemeColorsFromPickers() {
        if (schemePickers.isEmpty()) {
            return service.themeColors();
        }
        ThemeColors colors = service.themeColors();
        for (ThemeColors.Slot slot : SCHEME_SLOTS) {
            ColorPicker[] pickers = schemePickers.get(slot);
            colors = colors.with(slot, false, hexOrNull(pickers[0]));
            colors = colors.with(slot, true, hexOrNull(pickers[1]));
        }
        return colors;
    }

    /** Inline surface style of a preview node (board/column/card + selection). */
    private static String surfaceStyle(ThemeColors colors, ThemeColors.Slot slot,
                                       boolean selectedCard, boolean dark) {
        StringBuilder style = new StringBuilder("-fx-background-color: ")
                .append(colors.get(slot, dark)).append(";");
        if (selectedCard) {
            String accent = colors.get(ThemeColors.Slot.SELECTED, dark);
            style.append(" -fx-border-color: ").append(accent).append(";")
                    .append(" -fx-border-width: 2;")
                    .append(" -fx-background-radius: 10;")
                    .append(" -fx-border-radius: 10;");
        }
        return style.toString();
    }

    /** Inline text style for a preview label. */
    private static String textStyle(ThemeColors colors, ThemeColors.Slot slot, boolean dark) {
        return "-fx-text-fill: " + colors.get(slot, dark) + ";";
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
        // Color scheme: persist each slot; a value equal to the developer
        // default is cleared, so the storage stays minimal.
        ThemeColors defaults = ThemeColors.defaults();
        for (ThemeColors.Slot slot : SCHEME_SLOTS) {
            for (boolean dark : new boolean[]{false, true}) {
                String hex = hexOrNull(schemePickers.get(slot)[dark ? 1 : 0]);
                service.setThemeColor(slot, dark,
                        hex.equalsIgnoreCase(defaults.get(slot, dark)) ? null : hex);
            }
        }
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
}
