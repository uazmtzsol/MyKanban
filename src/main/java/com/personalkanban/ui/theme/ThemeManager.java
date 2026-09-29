package com.personalkanban.ui.theme;

import java.util.Objects;

/**
 * Holds the current theme and resolves it to a stylesheet path
 * (Pure Fabrication; Observer-friendly: the controller re-applies on change).
 */
public final class ThemeManager {

    public enum Theme { LIGHT, DARK }

    private static final String LIGHT_CSS = "/css/light.css";
    private static final String DARK_CSS = "/css/dark.css";

    private Theme theme;

    public ThemeManager(Theme initial) {
        this.theme = Objects.requireNonNull(initial);
    }

    public Theme theme() {
        return theme;
    }

    public boolean isDark() {
        return theme == Theme.DARK;
    }

    public void setTheme(Theme theme) {
        this.theme = Objects.requireNonNull(theme);
    }

    public void toggle() {
        this.theme = isDark() ? Theme.LIGHT : Theme.DARK;
    }

    /** Classpath path of the stylesheet for the current theme. */
    public String stylesheet() {
        return isDark() ? DARK_CSS : LIGHT_CSS;
    }
}
