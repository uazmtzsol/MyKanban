package com.personalkanban.application;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Session 11 (S11-2): the user color scheme resolves every slot with a
 * developer default, honors stored overrides per theme and reports when it
 * differs from the defaults.
 */
class ThemeColorsTest {

    @Test
    void defaultsProvideEverySlotForBothThemes() {
        ThemeColors defaults = ThemeColors.defaults();
        for (ThemeColors.Slot slot : ThemeColors.Slot.values()) {
            for (boolean dark : new boolean[]{false, true}) {
                assertThat(defaults.get(slot, dark))
                        .isEqualTo(ThemeColors.defaultOf(slot, dark));
            }
        }
        assertThat(defaults.isDefault()).isTrue();
    }

    @Test
    void storedOverridesWinOverDefaultsPerTheme() {
        Map<String, String> stored = Map.of(
                ThemeColors.storageKey(ThemeColors.Slot.BG, true), "#123456",
                ThemeColors.storageKey(ThemeColors.Slot.TEXT, false), "#abcdef");

        ThemeColors colors = ThemeColors.of(stored);

        assertThat(colors.get(ThemeColors.Slot.BG, true)).isEqualTo("#123456");
        // Untouched cells keep the developer default.
        assertThat(colors.get(ThemeColors.Slot.BG, false))
                .isEqualTo(ThemeColors.defaultOf(ThemeColors.Slot.BG, false));
        assertThat(colors.get(ThemeColors.Slot.TEXT, false)).isEqualTo("#abcdef");
        assertThat(colors.isDefault()).isFalse();
    }

    @Test
    void blankValuesFallBackToTheDefaults() {
        ThemeColors colors = ThemeColors.of(Map.of(
                ThemeColors.storageKey(ThemeColors.Slot.COLUMN, true), "  "));

        assertThat(colors.get(ThemeColors.Slot.COLUMN, true))
                .isEqualTo(ThemeColors.defaultOf(ThemeColors.Slot.COLUMN, true));
    }

    @Test
    void withChangesOnlyTheGivenSlotAndTheme() {
        ThemeColors original = ThemeColors.defaults();
        ThemeColors modified = original.with(ThemeColors.Slot.SELECTED, true, "#ff0000");

        assertThat(modified.get(ThemeColors.Slot.SELECTED, true)).isEqualTo("#ff0000");
        assertThat(modified.get(ThemeColors.Slot.SELECTED, false))
                .isEqualTo(original.get(ThemeColors.Slot.SELECTED, false));
        for (ThemeColors.Slot slot : ThemeColors.Slot.values()) {
            assertThat(modified.get(slot, false)).isEqualTo(original.get(slot, false));
        }
        // The original snapshot stays untouched (immutable value).
        assertThat(original.get(ThemeColors.Slot.SELECTED, true))
                .isEqualTo(ThemeColors.defaultOf(ThemeColors.Slot.SELECTED, true));
    }
}
