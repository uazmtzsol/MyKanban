package com.personalkanban.application;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * The user-customizable color scheme of the board (session 11, S11-2): five
 * slots — board background, column surface, card surface, primary text and
 * the highlight of the currently selected card — each stored separately for
 * the light and the dark theme.
 *
 * <p>Every slot has a developer default that matches the stylesheet values,
 * so an untouched scheme renders exactly like the built-in themes. The user
 * overrides individual slots in the preferences dialog (with a live preview
 * and a "restore defaults" button); anything not overridden falls back to
 * the default.</p>
 *
 * <p>Pure model (no JavaFX, no storage): the service reads/writes the
 * individual slot values through the settings store and rebuilds this
 * snapshot; the UI turns it into an overriding stylesheet.</p>
 */
public final class ThemeColors {

    /** The scheme slots, in the order the preferences dialog lists them. */
    public enum Slot {
        /** Board background (the .root surface behind the columns). */
        BG("bg"),
        /** Column surface. */
        COLUMN("column"),
        /** Card surface. */
        CARD("card"),
        /** Primary text (titles, labels, buttons). */
        TEXT("text"),
        /** Border/glow of the currently selected card (click or keyboard). */
        SELECTED("selected");

        private final String key;

        Slot(String key) {
            this.key = key;
        }

        /** Storage key fragment of this slot ("bg", "column", ...). */
        public String key() {
            return key;
        }
    }

    /** Settings prefix of every scheme value ({@code ui.scheme.<slot>.<theme>}). */
    public static final String STORAGE_PREFIX = "ui.scheme.";

    // Developer defaults, mirroring light.css / dark.css (S11-2 also lightened
    // the dark theme's secondary grays so text is readable on dark surfaces).
    private static final Map<Slot, String> DEFAULTS_LIGHT = new EnumMap<>(Slot.class);
    private static final Map<Slot, String> DEFAULTS_DARK = new EnumMap<>(Slot.class);

    static {
        DEFAULTS_LIGHT.put(Slot.BG, "#f5f6f8");
        DEFAULTS_LIGHT.put(Slot.COLUMN, "#ffffff");
        DEFAULTS_LIGHT.put(Slot.CARD, "#fafbfc");
        DEFAULTS_LIGHT.put(Slot.TEXT, "#1f2937");
        DEFAULTS_LIGHT.put(Slot.SELECTED, "#2563eb");
        DEFAULTS_DARK.put(Slot.BG, "#16181d");
        DEFAULTS_DARK.put(Slot.COLUMN, "#1f2229");
        DEFAULTS_DARK.put(Slot.CARD, "#262a33");
        DEFAULTS_DARK.put(Slot.TEXT, "#e5e7eb");
        DEFAULTS_DARK.put(Slot.SELECTED, "#60a5fa");
    }

    /** [slot][0=light, 1=dark]; never null (defaults fill every cell). */
    private final String[][] values;

    /** A fully resolved scheme; null cells are replaced by the defaults. */
    private ThemeColors(String[][] values) {
        this.values = new String[Slot.values().length][2];
        for (Slot slot : Slot.values()) {
            for (int theme = 0; theme < 2; theme++) {
                String candidate = values[slot.ordinal()][theme];
                this.values[slot.ordinal()][theme] = candidate == null || candidate.isBlank()
                        ? defaultOf(slot, theme == 1)
                        : candidate;
            }
        }
    }

    /** The developer-default scheme (no user overrides). */
    public static ThemeColors defaults() {
        return new ThemeColors(new String[Slot.values().length][2]);
    }

    /**
     * Builds the effective scheme from raw stored values (missing or blank
     * entries fall back to the developer defaults).
     */
    public static ThemeColors of(Map<String, String> storedByKey) {
        String[][] values = new String[Slot.values().length][2];
        if (storedByKey != null) {
            for (Slot slot : Slot.values()) {
                values[slot.ordinal()][0] = storedByKey.get(storageKey(slot, false));
                values[slot.ordinal()][1] = storedByKey.get(storageKey(slot, true));
            }
        }
        return new ThemeColors(values);
    }

    /** Effective color of one slot for one theme (never null). */
    public String get(Slot slot, boolean dark) {
        return values[slot.ordinal()][dark ? 1 : 0];
    }

    /** A copy of this scheme with one slot changed. */
    public ThemeColors with(Slot slot, boolean dark, String hex) {
        String[][] copy = new String[Slot.values().length][2];
        for (Slot candidate : Slot.values()) {
            copy[candidate.ordinal()][0] = get(candidate, false);
            copy[candidate.ordinal()][1] = get(candidate, true);
        }
        copy[slot.ordinal()][dark ? 1 : 0] = hex;
        return new ThemeColors(copy);
    }

    /** Developer default of one slot and theme (no storage involved). */
    public static String defaultOf(Slot slot, boolean dark) {
        return dark ? DEFAULTS_DARK.get(slot) : DEFAULTS_LIGHT.get(slot);
    }

    /** Settings key of one slot and one theme ({@code ui.scheme.<slot>.<theme>}). */
    public static String storageKey(Slot slot, boolean dark) {
        return STORAGE_PREFIX + slot.key() + "." + (dark ? "dark" : "light");
    }

    /** True when this scheme equals the developer defaults slot by slot. */
    public boolean isDefault() {
        for (Slot slot : Slot.values()) {
            for (boolean dark : new boolean[]{false, true}) {
                if (!Objects.equals(get(slot, dark), defaultOf(slot, dark))) {
                    return false;
                }
            }
        }
        return true;
    }
}
