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
    void contentIsChosenPerCardAndDefaultsToTheDescription() {
        CardViewSettings settings = new CardViewSettings();
        assertThat(settings.effectiveContent("c1"))
                .isEqualTo(CardViewSettings.Content.DESCRIPTION);
        assertThat(settings.hasContentOverride("c1")).isFalse();

        settings.setContentOverride("c1", CardViewSettings.Content.CHECKLIST);

        assertThat(settings.effectiveContent("c1")).isEqualTo(CardViewSettings.Content.CHECKLIST);
        assertThat(settings.hasContentOverride("c1")).isTrue();
        assertThat(settings.effectiveContent("c2"))
                .as("the choice is per card")
                .isEqualTo(CardViewSettings.Content.DESCRIPTION);
    }

    @Test
    void askingForTheDescriptionAgainDropsTheOverride() {
        CardViewSettings settings = new CardViewSettings();
        settings.setContentOverride("c1", CardViewSettings.Content.NOTES);

        settings.setContentOverride("c1", CardViewSettings.Content.DESCRIPTION);
        assertThat(settings.hasContentOverride("c1")).isFalse();

        settings.setContentOverride("c1", CardViewSettings.Content.NOTES);
        settings.setContentOverride("c1", null);
        assertThat(settings.hasContentOverride("c1")).isFalse();
        assertThat(settings.contentOverrideCount()).isZero();
    }

    @Test
    void clearingEveryOverrideAlsoClearsTheContentChoices() {
        CardViewSettings settings = new CardViewSettings();
        settings.setContentOverride("c1", CardViewSettings.Content.NOTES);

        settings.clearOverrides();

        assertThat(settings.contentOverrideCount()).isZero();
        assertThat(settings.effectiveContent("c1"))
                .isEqualTo(CardViewSettings.Content.DESCRIPTION);
    }

    @Test
    void jsonRoundTripPreservesTheContentChoices() {
        CardViewSettings settings = new CardViewSettings();
        settings.setContentOverride("c9", CardViewSettings.Content.CHECKLIST);

        CardViewSettings restored = CardViewSettings.fromJson(settings.toJson());

        assertThat(restored.effectiveContent("c9")).isEqualTo(CardViewSettings.Content.CHECKLIST);
        assertThat(restored.effectiveContent("c1")).isEqualTo(CardViewSettings.Content.DESCRIPTION);
    }

    /**
     * Settings written before N-14 have no {@code content} section at all:
     * every card has to keep showing the description.
     */
    @Test
    void settingsWithoutAContentSectionKeepTheDescription() {
        CardViewSettings restored =
                CardViewSettings.fromJson("{\"default\":\"TITLE_ONLY\",\"overrides\":{}}");

        assertThat(restored.boardDefault()).isEqualTo(CardViewSettings.Mode.TITLE_ONLY);
        assertThat(restored.contentOverrideCount()).isZero();
        assertThat(restored.effectiveContent("c1"))
                .isEqualTo(CardViewSettings.Content.DESCRIPTION);
    }

    @Test
    void anUnknownContentNameDegradesToDefaults() {
        CardViewSettings restored = CardViewSettings.fromJson("{\"content\":{\"c1\":\"SIDEWAYS\"}}");

        assertThat(restored.contentOverrideCount()).isZero();
        assertThat(restored.effectiveContent("c1"))
                .isEqualTo(CardViewSettings.Content.DESCRIPTION);
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
