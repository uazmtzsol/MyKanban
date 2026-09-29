package com.personalkanban.ui;

import com.personalkanban.domain.board.BoardColor;
import javafx.scene.paint.Color;

/**
 * Bridges domain colors to the UI. Swatches are painted programmatically from
 * the hex value (setFill), so they render correctly even inside dialogs that
 * do not inherit the scene's stylesheets — the old CSS-class swatches showed
 * up black for exactly that reason. Column/card accents are applied as inline
 * border colors so custom (non-palette) colors render exactly too.
 */
final class ColorCss {

    private ColorCss() {
    }

    /** Style class for a color: {@code pk-color-<name>}, or {@code pk-color-custom}. */
    static String styleClass(BoardColor color) {
        return "pk-color-" + (color.isCustom() ? "custom" : color.name());
    }

    /**
     * Applies the exact accent color to a region's border — only for custom
     * colors: named presets keep their themed styling from light/dark.css
     * (which adapts the palette shades per theme), while a custom color has
     * no stylesheet rule and needs its hue spelled out inline. The matching
     * {@code pk-color-custom} CSS class supplies the border width.
     */
    static void applyAccent(javafx.scene.layout.Region region, BoardColor color) {
        if (color.isCustom()) {
            region.setStyle("-fx-border-color: " + color.hex() + ";");
        }
    }

    /** Converts a JavaFX color to the {@code #rrggbb} form the domain expects. */
    static String toHex(Color color) {
        return String.format("#%02x%02x%02x",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }
}
