package com.personalkanban.domain.board;

import java.util.Arrays;
import java.util.Locale;

/**
 * The fixed color palette for columns and cards (OCP: a new color means a new
 * enum constant, never a magic string scattered through the code).
 * Names match CSS classes used by the UI, e.g. {@code pk-color-blue}.
 */
public enum BoardColor {

    BLUE("blue", "#2196f3"),
    GREEN("green", "#4caf50"),
    RED("red", "#f44336"),
    ORANGE("orange", "#ff9800"),
    PURPLE("purple", "#9c27b0"),
    TEAL("teal", "#009688"),
    PINK("pink", "#e91e63"),
    GRAY("gray", "#607d8b");

    public static final BoardColor DEFAULT = BLUE;

    private final String cssSuffix;
    private final String hex;

    BoardColor(String cssSuffix, String hex) {
        this.cssSuffix = cssSuffix;
        this.hex = hex;
    }

    /** Suffix used to build CSS classes such as {@code pk-color-blue}. */
    public String cssSuffix() {
        return cssSuffix;
    }

    /** Plain hex value, handy for small inline swatches. */
    public String hex() {
        return hex;
    }

    public static BoardColor fromName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Board color must not be null");
        }
        String normalized = name.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(color -> color.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown board color: " + name));
    }
}
