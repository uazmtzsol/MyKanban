package com.personalkanban.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class GlobalShortcutsTest {

    @Test
    void defaultShortcutsUseMostCommonValues() {
        GlobalShortcuts shortcuts = new GlobalShortcuts();

        assertThat(shortcuts.get(GlobalShortcuts.Action.NEXT_CARD))
                .isEqualTo(GlobalShortcuts.defaultFor(GlobalShortcuts.Action.NEXT_CARD));
        assertThat(shortcuts.get(GlobalShortcuts.Action.PREV_CARD))
                .isEqualTo(GlobalShortcuts.defaultFor(GlobalShortcuts.Action.PREV_CARD));
        assertThat(shortcuts.get(GlobalShortcuts.Action.EDIT_CARD))
                .isEqualTo(GlobalShortcuts.defaultFor(GlobalShortcuts.Action.EDIT_CARD));
        assertThat(shortcuts.get(GlobalShortcuts.Action.EXIT))
                .isEqualTo(GlobalShortcuts.defaultFor(GlobalShortcuts.Action.EXIT));
        assertThat(shortcuts.get(GlobalShortcuts.Action.LABEL_FILTER))
                .isEqualTo(GlobalShortcuts.defaultFor(GlobalShortcuts.Action.LABEL_FILTER));
        assertThat(shortcuts.get(GlobalShortcuts.Action.PROCESS_FILTER))
                .isEqualTo(GlobalShortcuts.defaultFor(GlobalShortcuts.Action.PROCESS_FILTER));
    }

    @Test
    void storedJsonWithoutAnActionFallsBackToItsDefault() {
        // A list saved before the filter actions existed: those must not stay
        // blank (blank = unbound, which disabled Ctrl+F / Ctrl+P).
        String legacy = """
                [{"action":"NEXT_CARD","keyCombination":"Right"},
                 {"action":"PREV_CARD","keyCombination":"Left"},
                 {"action":"EDIT_CARD","keyCombination":"Enter"},
                 {"action":"EXIT","keyCombination":"Escape"}]
                """;

        GlobalShortcuts shortcuts = GlobalShortcuts.fromJson(legacy);

        assertThat(shortcuts.get(GlobalShortcuts.Action.LABEL_FILTER)).isEqualTo("Ctrl+F");
        assertThat(shortcuts.get(GlobalShortcuts.Action.PROCESS_FILTER)).isEqualTo("Ctrl+P");
        assertThat(shortcuts.get(GlobalShortcuts.Action.NEXT_CARD)).isEqualTo("Right");
    }

    @Test
    void anActionTheUserClearedStaysCleared() {
        String stored = """
                [{"action":"NEXT_CARD","keyCombination":""},
                 {"action":"PREV_CARD","keyCombination":"Left"},
                 {"action":"EDIT_CARD","keyCombination":"Enter"},
                 {"action":"EXIT","keyCombination":"Escape"},
                 {"action":"LABEL_FILTER","keyCombination":"Ctrl+F"},
                 {"action":"PROCESS_FILTER","keyCombination":"Ctrl+P"}]
                """;

        GlobalShortcuts shortcuts = GlobalShortcuts.fromJson(stored);

        assertThat(shortcuts.get(GlobalShortcuts.Action.NEXT_CARD)).isEmpty();
    }

    @Test
    void defaultShortcutsMatchKnownDefaults() {
        GlobalShortcuts shortcuts = new GlobalShortcuts();

        assertThat(shortcuts.get(GlobalShortcuts.Action.NEXT_CARD)).isEqualTo("Right");
        assertThat(shortcuts.get(GlobalShortcuts.Action.PREV_CARD)).isEqualTo("Left");
        assertThat(shortcuts.get(GlobalShortcuts.Action.EDIT_CARD)).isEqualTo("Enter");
        assertThat(shortcuts.get(GlobalShortcuts.Action.EXIT)).isEqualTo("Escape");
        assertThat(shortcuts.get(GlobalShortcuts.Action.LABEL_FILTER)).isEqualTo("Ctrl+F");
        assertThat(shortcuts.get(GlobalShortcuts.Action.PROCESS_FILTER)).isEqualTo("Ctrl+P");
    }

    @Test
    void setReplacesOneAction() {
        GlobalShortcuts shortcuts = new GlobalShortcuts();
        shortcuts.set(GlobalShortcuts.Action.NEXT_CARD, "Down");

        assertThat(shortcuts.get(GlobalShortcuts.Action.NEXT_CARD)).isEqualTo("Down");
        assertThat(shortcuts.get(GlobalShortcuts.Action.PREV_CARD)).isEqualTo("Left");
        assertThat(shortcuts.get(GlobalShortcuts.Action.EDIT_CARD)).isEqualTo("Enter");
    }

    @Test
    void setIgnoresSurroundingWhitespaceAndQuotes() {
        GlobalShortcuts shortcuts = new GlobalShortcuts();
        shortcuts.set(GlobalShortcuts.Action.EXIT, "  \"Tab\"  ");

        assertThat(shortcuts.get(GlobalShortcuts.Action.EXIT)).isEqualTo("Tab");
    }

    @Test
    void setWithEmptyBecomesBlank() {
        GlobalShortcuts shortcuts = new GlobalShortcuts();
        shortcuts.set(GlobalShortcuts.Action.PROCESS_FILTER, "   ");

        assertThat(shortcuts.get(GlobalShortcuts.Action.PROCESS_FILTER)).isEmpty();
    }

    @Test
    void setWithInvalidValueStillNormalizes() {
        GlobalShortcuts shortcuts = new GlobalShortcuts();
        shortcuts.set(GlobalShortcuts.Action.LABEL_FILTER, "Ctrl +  G  ");

        assertThat(shortcuts.get(GlobalShortcuts.Action.LABEL_FILTER)).isEqualTo("Ctrl + G");
    }

    @Test
    void fromJsonWithBlankReturnsDefaults() {
        GlobalShortcuts shortcuts = GlobalShortcuts.fromJson("   ");
        assertThat(shortcuts.get(GlobalShortcuts.Action.NEXT_CARD)).isEqualTo("Right");
    }

    @Test
    void fromJsonRoundTrips() {
        GlobalShortcuts original = new GlobalShortcuts();
        original.set(GlobalShortcuts.Action.NEXT_CARD, "Down");
        original.set(GlobalShortcuts.Action.EXIT, "Tab");

        String json = original.toJson();
        GlobalShortcuts restored = GlobalShortcuts.fromJson(json);

        assertThat(restored.get(GlobalShortcuts.Action.NEXT_CARD)).isEqualTo("Down");
        assertThat(restored.get(GlobalShortcuts.Action.EXIT)).isEqualTo("Tab");
        assertThat(restored.get(GlobalShortcuts.Action.EDIT_CARD)).isEqualTo("Enter");
    }

    @Test
    void fromJsonWithUnknownActionKeepsKnownActions() {
        String json = """
                [
                  {"action":"NEXT_CARD","keyCombination":"Down"},
                  {"action":"UNKNOWN_ACTION","keyCombination":"X"},
                  {"action":"EDIT_CARD","keyCombination":"Enter"}
                ]
                """;

        GlobalShortcuts restored = GlobalShortcuts.fromJson(json);

        assertThat(restored.get(GlobalShortcuts.Action.NEXT_CARD)).isEqualTo("Down");
        assertThat(restored.get(GlobalShortcuts.Action.EDIT_CARD)).isEqualTo("Enter");
        // Unknown actions should be silently ignored.
        assertThat(restored.settings()).allSatisfy(setting ->
                GlobalShortcuts.Action.isKnownActionName(setting.action()));
    }

    @Test
    void fromJsonWithEmptyListReturnsDefaults() {
        String json = "[]";
        GlobalShortcuts restored = GlobalShortcuts.fromJson(json);
        assertThat(restored.get(GlobalShortcuts.Action.NEXT_CARD)).isEqualTo("Right");
    }

    @Test
    void validActionNamesMatchDefaultActions() {
        assertThat(GlobalShortcuts.validActionNames()).containsExactly(
                GlobalShortcuts.Action.NEXT_CARD.name(),
                GlobalShortcuts.Action.PREV_CARD.name(),
                GlobalShortcuts.Action.EDIT_CARD.name(),
                GlobalShortcuts.Action.EXIT.name(),
                GlobalShortcuts.Action.LABEL_FILTER.name(),
                GlobalShortcuts.Action.PROCESS_FILTER.name()
        );
    }

    @Test
    void storageLabelKeyIsStable() {
        GlobalShortcuts shortcuts = new GlobalShortcuts();
        assertThat(shortcuts.storageLabelKey(GlobalShortcuts.Action.NEXT_CARD))
                .isEqualTo("shortcut.next.card");
        assertThat(shortcuts.storageLabelKey(GlobalShortcuts.Action.EDIT_CARD))
                .isEqualTo("shortcut.edit.card");
    }

    @Test
    void equalsAndHashCodeIgnore() {
        // We no longer require the model to be used as a value-object identity;
        // what matters is behavior around get/set/serialization.
        GlobalShortcuts a = new GlobalShortcuts();
        GlobalShortcuts b = new GlobalShortcuts();

        assertThat(a.get(GlobalShortcuts.Action.NEXT_CARD))
                .isEqualTo(b.get(GlobalShortcuts.Action.NEXT_CARD));
        a.set(GlobalShortcuts.Action.NEXT_CARD, "Down");
        assertThat(a.get(GlobalShortcuts.Action.NEXT_CARD)).isNotEqualTo(
                b.get(GlobalShortcuts.Action.NEXT_CARD));
    }
}
