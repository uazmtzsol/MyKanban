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
     *
     * @deprecated replaced by {@link #applySurface}, which also paints the
     *             tinted background; kept until all call sites migrate.
     */
    @Deprecated
    static void applyAccent(javafx.scene.layout.Region region, BoardColor color) {
        if (color.isCustom()) {
            region.setStyle("-fx-border-color: " + color.hex() + ";");
        }
    }

    /**
     * Hex of the chosen color mixed into the surface color behind the region
     * — a soft, always-legible tint instead of the raw (often saturated or
     * too dark/light) picked color. The default color yields no tint: boards
     * stay calm until the user deliberately picks a color.
     */
    static String backgroundTint(BoardColor color, boolean dark) {
        if (color.equals(BoardColor.DEFAULT)) {
            return "";
        }
        javafx.scene.paint.Color base = javafx.scene.paint.Color.web(color.hex());
        javafx.scene.paint.Color surface =
                javafx.scene.paint.Color.web(dark ? "#1f2229" : "#ffffff");
        double alpha = dark ? 0.22 : 0.12;
        javafx.scene.paint.Color mixed = javafx.scene.paint.Color.color(
                base.getRed() * alpha + surface.getRed() * (1 - alpha),
                base.getGreen() * alpha + surface.getGreen() * (1 - alpha),
                base.getBlue() * alpha + surface.getBlue() * (1 - alpha));
        return toHex(mixed);
    }

    /**
     * Paints a column/card "surface": tinted background (any non-default
     * color) plus the exact border for custom colors. Inline styles win over
     * the stylesheet, so this composes everything one node needs in a single
     * {@code setStyle} call.
     */
    static void applySurface(javafx.scene.layout.Region region, BoardColor color, boolean dark) {
        String style = surfaceStyle(color, dark);
        if (!style.isEmpty()) {
            region.setStyle(style);
        }
    }

    /**
     * The inline style produced by {@link #applySurface}, as a string so
     * callers can append more declarations (priority/label/column colors)
     * before a single {@code setStyle} call.
     */
    static String surfaceStyle(BoardColor color, boolean dark) {
        StringBuilder style = new StringBuilder();
        String tint = backgroundTint(color, dark);
        if (!tint.isEmpty()) {
            style.append("-fx-background-color: ").append(tint).append(";");
        }
        if (color.isCustom()) {
            style.append("-fx-border-color: ").append(color.hex()).append(";");
        }
        return style.toString();
    }

    /** Converts a JavaFX color to the {@code #rrggbb} form the domain expects. */
    static String toHex(Color color) {
        return String.format("#%02x%02x%02x",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }
}
