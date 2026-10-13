package com.personalkanban.ui;

import com.personalkanban.application.StylePrefs;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * How a label chip is painted (user request T15 · B7, pure — no JavaFX):
 * the color the user chose for that label when a style rule exists, else the
 * deterministic hash palette class the chips have always used.
 */
final class LabelChipStyle {

    /** Style class (hash palette) or inline declarations (chosen color). */
    record Chip(String styleClass, String inlineStyle) {
    }

    /** Palette slots of the hash fallback (the {@code chip-color-N} CSS rules). */
    private static final int HASH_SLOTS = 8;

    private LabelChipStyle() {
    }

    /** Deterministic fallback: label hash → one of the palette slots. */
    static String hashClass(String label) {
        int index = Math.abs(label.toLowerCase(Locale.ROOT).hashCode()) % HASH_SLOTS;
        return "chip-color-" + index;
    }

    /**
     * @param lookup resolves the saved colors of one style-rule key, e.g.
     *               {@code key -> service.labelStyle(key, dark)}
     */
    static Chip of(String label, Function<String, Optional<StylePrefs.StyleColors>> lookup) {
        Optional<StylePrefs.StyleColors> rule = lookup.apply(StylePrefs.labelKey(label));
        if (rule.isPresent()) {
            return new Chip(null, inline(rule.get()));
        }
        return new Chip(hashClass(label), null);
    }

    /** Inline CSS of a saved rule (either field may be absent). */
    static String inline(StylePrefs.StyleColors colors) {
        StringBuilder style = new StringBuilder();
        if (colors.background() != null && !colors.background().isBlank()) {
            style.append("-fx-background-color: ").append(colors.background()).append(';');
        }
        if (colors.text() != null && !colors.text().isBlank()) {
            style.append("-fx-text-fill: ").append(colors.text()).append(';');
        }
        return style.toString();
    }

    /**
     * Readable text color for a chip background: dark ink on light colors,
     * white on dark ones (relative luminance, the WCAG threshold).
     */
    static String contrastingText(String backgroundHex) {
        int rgb;
        try {
            rgb = Integer.parseInt(backgroundHex.replace("#", ""), 16);
        } catch (RuntimeException notHex) {
            return "#ffffff";
        }
        double red = ((rgb >> 16) & 0xFF) / 255.0;
        double green = ((rgb >> 8) & 0xFF) / 255.0;
        double blue = (rgb & 0xFF) / 255.0;
        double luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue;
        return luminance > 0.6 ? "#1f2937" : "#ffffff";
    }
}
