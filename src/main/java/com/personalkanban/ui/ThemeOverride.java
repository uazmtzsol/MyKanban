package com.personalkanban.ui;

import com.personalkanban.application.ThemeColors;

/**
 * Turns a {@link ThemeColors} scheme into the small overriding stylesheet
 * that is appended after the built-in theme (session 11, S11-2). JavaFX
 * applies stylesheets in order and the later declaration of the same
 * specificity wins, so this file only restates the five customizable slots:
 * root background/text, column surface, card surface and the highlight of
 * the currently selected card.
 *
 * <p>Pure string building (no JavaFX classes touched), so it is unit
 * testable. Inline per-card styles (custom card colors, flag/label color
 * rules) keep winning over this sheet by design — the scheme paints the
 * *default* surfaces, user-chosen card colors stay on top.</p>
 */
final class ThemeOverride {

    private ThemeOverride() {
    }

    /** The overriding CSS for one theme; appended after light/dark.css. */
    static String css(ThemeColors colors, boolean dark) {
        StringBuilder css = new StringBuilder();
        css.append("/* Personal Kanban user color scheme (")
                .append(dark ? "dark" : "light").append(") — generated, do not edit. */\n");
        css.append(".root {\n")
                .append("    -fx-background-color: ").append(colors.get(ThemeColors.Slot.BG, dark)).append(";\n")
                .append("    -fx-text-fill: ").append(colors.get(ThemeColors.Slot.TEXT, dark)).append(";\n")
                .append("}\n");
        css.append(".column {\n")
                .append("    -fx-background-color: ").append(colors.get(ThemeColors.Slot.COLUMN, dark)).append(";\n")
                .append("}\n");
        css.append(".card {\n")
                .append("    -fx-background-color: ").append(colors.get(ThemeColors.Slot.CARD, dark)).append(";\n")
                .append("}\n");
        // The selected card (click or keyboard) is restated with the same
        // glow the themes use, so the highlight keeps its shape and only
        // the hue follows the scheme.
        String selected = colors.get(ThemeColors.Slot.SELECTED, dark);
        css.append(".card:focused {\n")
                .append("    -fx-border-color: ").append(selected).append(";\n")
                .append("    -fx-border-width: 2;\n")
                .append("    -fx-effect: dropshadow(gaussian, ").append(selected)
                .append(", 16, 0.60, 0, 0);\n")
                .append("}\n");
        css.append(".card-selected {\n")
                .append("    -fx-border-color: ").append(selected).append(";\n")
                .append("    -fx-border-width: 2;\n")
                .append("}\n");
        return css.toString();
    }

    /** File name of the generated override inside the data directory. */
    static final String FILE_NAME = "scheme-overrides.css";

    /**
     * Writes the override for the given theme into the data directory and
     * returns its URL (to be appended to the scene stylesheets), or null when
     * the file cannot be written — a cosmetic preference never blocks the UI,
     * the built-in theme simply keeps rendering.
     */
    static String write(java.nio.file.Path dataDirectory, ThemeColors colors, boolean dark) {
        try {
            java.nio.file.Files.createDirectories(dataDirectory);
            java.nio.file.Path file = dataDirectory.resolve(FILE_NAME);
            java.nio.file.Files.writeString(file, css(colors, dark),
                    java.nio.charset.StandardCharsets.UTF_8);
            return file.toUri().toString();
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }
}
