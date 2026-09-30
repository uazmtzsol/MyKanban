package com.personalkanban.domain.board;

import java.util.List;
import java.util.Locale;

/**
 * A board color: either one of the 8 named palette presets or a custom color
 * chosen with a color picker. Identity is the hex value, so presets and
 * custom colors compare equal by appearance (records give that for free).
 * Carrying hex makes persistence and JSON export self-describing, and lets
 * the UI paint swatches programmatically (immune to missing stylesheets).
 */
public record BoardColor(String name, String hex) {

    public static final BoardColor BLUE = new BoardColor("blue", "#2196f3");
    public static final BoardColor GREEN = new BoardColor("green", "#4caf50");
    public static final BoardColor RED = new BoardColor("red", "#f44336");
    public static final BoardColor ORANGE = new BoardColor("orange", "#ff9800");
    public static final BoardColor PURPLE = new BoardColor("purple", "#9c27b0");
    public static final BoardColor TEAL = new BoardColor("teal", "#009688");
    public static final BoardColor PINK = new BoardColor("pink", "#e91e63");
    public static final BoardColor GRAY = new BoardColor("gray", "#607d8b");

    public static final BoardColor DEFAULT = BLUE;

    private static final List<BoardColor> PALETTE =
            List.of(BLUE, GREEN, RED, ORANGE, PURPLE, TEAL, PINK, GRAY);

    public BoardColor {
        if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Color must be #rrggbb but was: " + hex);
        }
        hex = hex.toLowerCase(Locale.ROOT);
        name = (name == null || name.isBlank()) ? null : name.strip().toLowerCase(Locale.ROOT);
    }

    /** The 8 named presets, in stable display order. */
    public static List<BoardColor> palette() {
        return PALETTE;
    }

    /** Parses any stored value: a hex code, or a legacy palette name. */
    public static BoardColor fromStored(String stored) {
        if (stored == null || stored.isBlank()) {
            throw new IllegalArgumentException("Stored color must not be blank");
        }
        String value = stored.strip();
        if (value.startsWith("#")) {
            return fromHex(value);
        }
        return fromName(value);
    }

    /** Preset lookup by hex; anything else becomes a custom color. */
    public static BoardColor fromHex(String hex) {
        String normalized = hex.strip().toLowerCase(Locale.ROOT);
        return PALETTE.stream()
                .filter(color -> color.hex().equals(normalized))
                .findFirst()
                .orElseGet(() -> new BoardColor(null, normalized));
    }

    /** Legacy palette-name lookup (old databases stored enum names). */
    public static BoardColor fromName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Color name must not be null");
        }
        String normalized = name.strip().toLowerCase(Locale.ROOT);
        return PALETTE.stream()
                .filter(color -> normalized.equals(color.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown board color name: " + name));
    }

    /** True when the color is not one of the named presets. */
    public boolean isCustom() {
        return name == null;
    }

    /**
     * The value to store in SQLite/JSON: the palette name for presets, the
     * hex code for custom colors. Never null, unlike {@link #name()}, which
     * is only populated for palette presets — storing {@code name()} for a
     * custom color once tripped the NOT NULL constraint ("Could not save
     * board ..."). {@link #fromStored} reads both forms back.
     */
    public String stored() {
        return isCustom() ? hex : name;
    }

    /** Human-readable label: preset name or the hex itself for custom colors. */
    public String displayName() {
        return isCustom() ? hex : name;
    }
}
