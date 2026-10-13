package com.personalkanban.ui;

import com.personalkanban.application.GlobalShortcuts;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression for bug S11-6: opening «Ayuda → Personalizar atajos de teclado»
 * threw {@link IllegalArgumentException} because each row was round-tripped
 * through a name lookup with a value that was actually an i18n key.
 *
 * <p>The modal dialog itself cannot be shown in a test, so this covers the row
 * model it is built from — building the rows, driving the «Restaurar» path and
 * rendering the labels must all work for every configurable action and in
 * every supported language.</p>
 */
class KeyboardShortcutsDialogTest {

    @Test
    void rowsCoverEveryConfigurableActionOnce() {
        List<KeyboardShortcutsDialog.RowItem> rows =
                KeyboardShortcutsDialog.rowsOf(new GlobalShortcuts());

        assertThat(rows).hasSize(GlobalShortcuts.Action.values().length);
        assertThat(rows).extracting(KeyboardShortcutsDialog.RowItem::action)
                .containsExactlyInAnyOrder(GlobalShortcuts.Action.values());
    }

    @Test
    void everyRowShowsALocalizedLabelInsteadOfItsKey() {
        GlobalShortcuts shortcuts = new GlobalShortcuts();

        for (Locale locale : I18n.SUPPORTED) {
            I18n i18n = new I18n(locale);
            for (KeyboardShortcutsDialog.RowItem row : KeyboardShortcutsDialog.rowsOf(shortcuts)) {
                String label = KeyboardShortcutsDialog.rowLabel(i18n, shortcuts, row);
                String actionKey = shortcuts.storageLabelKey(row.action());

                assertThat(label)
                        .as("%s in %s", row.action(), locale)
                        .doesNotContain(actionKey)
                        .doesNotContain("shortcut.")
                        .contains(row.currentKey());
            }
        }
    }

    @Test
    void anUnboundActionIsShownAsUnsetInEveryLanguage() {
        GlobalShortcuts shortcuts = new GlobalShortcuts();
        shortcuts.set(GlobalShortcuts.Action.EXIT, "");

        for (Locale locale : I18n.SUPPORTED) {
            I18n i18n = new I18n(locale);
            KeyboardShortcutsDialog.RowItem row = new KeyboardShortcutsDialog.RowItem(
                    GlobalShortcuts.Action.EXIT, shortcuts.get(GlobalShortcuts.Action.EXIT));
            String label = KeyboardShortcutsDialog.rowLabel(i18n, shortcuts, row);

            assertThat(label).contains(i18n.text("shortcut.key.unset"));
            assertThat(i18n.text("shortcut.key.unset")).isNotEqualTo("shortcut.key.unset");
        }
    }

    @Test
    void restoringDefaultsRebuildsEveryRowWithoutThrowing() {
        GlobalShortcuts shortcuts = new GlobalShortcuts();
        shortcuts.set(GlobalShortcuts.Action.NEXT_CARD, "Down");
        shortcuts.set(GlobalShortcuts.Action.EXIT, "");

        assertThatCode(() -> {
            KeyboardShortcutsDialog.applyDefaults(shortcuts);
            KeyboardShortcutsDialog.rowsOf(shortcuts);
        }).doesNotThrowAnyException();

        for (GlobalShortcuts.Action action : GlobalShortcuts.Action.values()) {
            assertThat(shortcuts.get(action))
                    .as("default restored for %s", action)
                    .isEqualTo(GlobalShortcuts.defaultFor(action));
        }
    }
}
