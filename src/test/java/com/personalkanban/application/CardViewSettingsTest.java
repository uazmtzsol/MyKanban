package com.personalkanban.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Card view preferences: effective mode, overrides, JSON round-trip. */
class CardViewSettingsTest {

    @Test
    void defaultModeIsTitlePreview() {
        assertThat(new CardViewSettings().boardDefault())
                .isEqualTo(CardViewSettings.Mode.TITLE_PREVIEW);
        assertThat(new CardViewSettings().effectiveMode("any-card"))
                .isEqualTo(CardViewSettings.Mode.TITLE_PREVIEW);
    }

    @Test
    void boardDefaultChangesEffectiveModeOfEveryCard() {
        CardViewSettings settings = new CardViewSettings();
        settings.setBoardDefault(CardViewSettings.Mode.TITLE_ONLY);
        assertThat(settings.effectiveMode("c1")).isEqualTo(CardViewSettings.Mode.TITLE_ONLY);
        assertThat(settings.effectiveMode("c2")).isEqualTo(CardViewSettings.Mode.TITLE_ONLY);
    }

    @Test
    void overrideWinsOverBoardDefaultAndCanBeCleared() {
        CardViewSettings settings = new CardViewSettings();
        settings.setOverride("special", CardViewSettings.Mode.FULL);

        assertThat(settings.effectiveMode("special")).isEqualTo(CardViewSettings.Mode.FULL);
        assertThat(settings.effectiveMode("other")).isEqualTo(CardViewSettings.Mode.TITLE_PREVIEW);
        assertThat(settings.hasOverride("special")).isTrue();

        settings.setOverride("special", null); // back to the board default
        assertThat(settings.hasOverride("special")).isFalse();
        assertThat(settings.effectiveMode("special")).isEqualTo(CardViewSettings.Mode.TITLE_PREVIEW);
    }

    @Test
    void clearOverridesReturnsEverythingToBoardDefault() {
        CardViewSettings settings = new CardViewSettings();
        settings.setBoardDefault(CardViewSettings.Mode.FULL);
        settings.setOverride("a", CardViewSettings.Mode.TITLE_ONLY);
        settings.setOverride("b", CardViewSettings.Mode.TITLE_PREVIEW);

        settings.clearOverrides();

        assertThat(settings.overrideCount()).isZero();
        assertThat(settings.effectiveMode("a")).isEqualTo(CardViewSettings.Mode.FULL);
        assertThat(settings.effectiveMode("b")).isEqualTo(CardViewSettings.Mode.FULL);
    }

    @Test
    void jsonRoundTripPreservesDefaultsAndOverrides() {
        CardViewSettings settings = new CardViewSettings();
        settings.setBoardDefault(CardViewSettings.Mode.TITLE_ONLY);
        settings.setOverride("c9", CardViewSettings.Mode.FULL);

        CardViewSettings restored = CardViewSettings.fromJson(settings.toJson());

        assertThat(restored.boardDefault()).isEqualTo(CardViewSettings.Mode.TITLE_ONLY);
        assertThat(restored.effectiveMode("c9")).isEqualTo(CardViewSettings.Mode.FULL);
        assertThat(restored.effectiveMode("c1")).isEqualTo(CardViewSettings.Mode.TITLE_ONLY);
    }

    @Test
    void corruptOrEmptyJsonDegradesToDefaults() {
        assertThat(CardViewSettings.fromJson(null).boardDefault())
                .isEqualTo(CardViewSettings.Mode.TITLE_PREVIEW);
        assertThat(CardViewSettings.fromJson("not json at all").overrideCount()).isZero();
        assertThat(CardViewSettings.fromJson("").overrideCount()).isZero();
    }
}
