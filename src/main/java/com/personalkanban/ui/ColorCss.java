package com.personalkanban.ui;

import com.personalkanban.domain.board.BoardColor;

import java.util.ArrayList;
import java.util.List;

/** Maps palette colors to the {@code pk-color-*} CSS classes (Single source of truth). */
final class ColorCss {

    static final String PREFIX = "pk-color-";

    private ColorCss() {
    }

    /** Style class for a color, e.g. {@code pk-color-blue}. */
    static String styleClass(BoardColor color) {
        return PREFIX + color.cssSuffix();
    }

    /** All color classes; used to strip previous colors from a node. */
    static List<String> allClasses() {
        List<String> classes = new ArrayList<>();
        for (BoardColor color : BoardColor.values()) {
            classes.add(styleClass(color));
        }
        return classes;
    }
}
