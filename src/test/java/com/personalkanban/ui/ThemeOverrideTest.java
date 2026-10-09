package com.personalkanban.ui;

import com.personalkanban.application.ThemeColors;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Session 11 (S11-2): the generated override stylesheet restates exactly the
 * five customizable slots for the requested theme, and a write failure (e.g.
 * a read-only data folder) degrades to null instead of blocking the UI.
 */
class ThemeOverrideTest {

    @Test
    void cssRestatesEverySlotOfTheRequestedTheme() {
        ThemeColors colors = ThemeColors.defaults()
                .with(ThemeColors.Slot.BG, true, "#101010")
                .with(ThemeColors.Slot.COLUMN, true, "#202020")
                .with(ThemeColors.Slot.CARD, true, "#303030")
                .with(ThemeColors.Slot.TEXT, true, "#f0f0f0")
                .with(ThemeColors.Slot.SELECTED, true, "#00ff00");

        String css = ThemeOverride.css(colors, true);

        assertThat(css).contains("-fx-background-color: #101010;");
        assertThat(css).contains("-fx-background-color: #202020;");
        assertThat(css).contains("-fx-background-color: #303030;");
        assertThat(css).contains("-fx-text-fill: #f0f0f0;");
        assertThat(css).contains(".card-selected");
        assertThat(css).contains("-fx-border-color: #00ff00;");
        // Selection keeps the theme's glow shape with the chosen hue.
        assertThat(css).contains(".card:focused").contains("dropshadow(gaussian, #00ff00");
    }

    @Test
    void cssUsesTheLightValuesForTheLightTheme() {
        String css = ThemeOverride.css(ThemeColors.defaults(), false);

        assertThat(css).contains(ThemeColors.defaultOf(ThemeColors.Slot.BG, false));
        assertThat(css).contains(ThemeColors.defaultOf(ThemeColors.Slot.TEXT, false));
        assertThat(css).doesNotContain(ThemeColors.defaultOf(ThemeColors.Slot.BG, true));
    }

    @Test
    void writeStoresTheCssAndReportsItsUrl() throws Exception {
        var dir = java.nio.file.Files.createTempDirectory("pk-scheme-test");
        try {
            String url = ThemeOverride.write(dir, ThemeColors.defaults(), true);

            assertThat(url).startsWith("file:").endsWith(ThemeOverride.FILE_NAME);
            String written = java.nio.file.Files.readString(dir.resolve(ThemeOverride.FILE_NAME));
            assertThat(written).isEqualTo(ThemeOverride.css(ThemeColors.defaults(), true));
        } finally {
            try (var walk = java.nio.file.Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    @Test
    void writeReturnsNullWhenTheDirectoryCannotBeWritten() {
        // A regular file used as "directory" makes createDirectories fail.
        try {
            var file = java.nio.file.Files.createTempFile("pk-scheme-not-a-dir", ".tmp");
            try {
                assertThat(ThemeOverride.write(file, ThemeColors.defaults(), false)).isNull();
            } finally {
                java.nio.file.Files.deleteIfExists(file);
            }
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void storedSchemeBuildsTheOverridingCssFromSettings() {
        // Simulates what the service produces from stored values.
        Map<String, String> stored = Map.of(
                ThemeColors.storageKey(ThemeColors.Slot.BG, true), "#0a0a0a");
        String css = ThemeOverride.css(ThemeColors.of(stored), true);

        assertThat(css).contains("-fx-background-color: #0a0a0a;");
    }
}
