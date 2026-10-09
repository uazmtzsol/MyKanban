package com.personalkanban.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wiring guards for the session-10 keyboard map.
 *
 * <p>The behaviour that matters here cannot be asserted without booting a
 * database-backed controller on a JavaFX toolkit, so — like
 * {@link CardKeyboardFocusTest} — the source itself is checked: the handler
 * must exist as a scene-level event FILTER (an accelerator is swallowed by
 * the focused control, which is why the keyboard used to look dead while the
 * toolbar buttons worked), the buttons it replaces must be gone, and every
 * key of the user's spec must be handled.</p>
 */
class KeyboardNavigationTest {

    private static Path uiRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 4 && candidate != null; depth++) {
            Path probe = candidate.resolve("src/main/java/com/personalkanban/ui");
            if (Files.isDirectory(probe)) {
                return probe;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate the ui sources");
    }

    private static String controllerSource() throws IOException {
        return Files.readString(uiRoot().resolve("BoardController.java"), StandardCharsets.UTF_8);
    }

    @Test
    void keyboardMapIsAEventFilterOnTheSceneNotAnAccelerator() throws IOException {
        String source = controllerSource();
        assertThat(source)
                .as("a filter fires before the focused control can swallow the key")
                .contains("scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, this::onBoardKeyPressed)");
        assertThat(source)
                .as("the four navigation accelerators must not double-handle the keys")
                .doesNotContain("getAccelerators().put(keyComboFor(GlobalShortcuts.Action.NEXT_CARD)");
        assertThat(source)
                .doesNotContain("getAccelerators().put(keyComboFor(GlobalShortcuts.Action.PREV_CARD)");
        assertThat(source)
                .doesNotContain("getAccelerators().put(keyComboFor(GlobalShortcuts.Action.EDIT_CARD)");
    }

    @Test
    void navigationButtonsAreGoneFromTheToolbar() throws IOException {
        String source = controllerSource();
        assertThat(source)
                .as("session 10: navigation is keyboard-only now")
                .doesNotContain("focusNext")
                .doesNotContain("focusPrev")
                .doesNotContain("editFocused")
                .doesNotContain("toolButton(\"\\u2192\", \"shortcut.next.card\")");
    }

    @Test
    void everyKeyOfTheSpecIsHandled() throws IOException {
        String source = controllerSource();
        assertThat(source).contains("KeyCode.DOWN");       // next card in column
        assertThat(source).contains("KeyCode.UP");         // previous card in column
        assertThat(source).contains("focusNeighbouringColumn"); // Alt+arrows
        assertThat(source).contains("switchBoard");              // Ctrl+arrows
        assertThat(source).contains("DIGIT1, NUMPAD1");          // positional select
        assertThat(source).contains("KeyCode.DELETE");            // delete with confirm
        assertThat(source).contains("KeyCode.I");                 // Alt+I importante
        assertThat(source).contains("KeyCode.U");                 // Alt+U urgente
        assertThat(source).contains("onEditCard(target)");        // Enter edits
        assertThat(source).contains("digitPosition");
    }

    @Test
    void typingInTheFilterFieldNeverTriggersNavigation() throws IOException {
        String source = controllerSource();
        assertThat(source)
                .as("arrows/digits must keep their normal meaning inside a field")
                .contains("inTextEntryContext");
        assertThat(source)
                .as("the field guard must run before the fixed bindings")
                .contains("if (!inTextEntryContext()) {");
    }

    @Test
    void theProcessesViewKeepsItsOwnKeys() throws IOException {
        String source = controllerSource();
        assertThat(source)
                .as("ProcessViewBuilder owns arrows/Delete there; the map must step aside")
                .contains("if (processView) {");
    }

    @Test
    void persistedShortcutsAreLoadedOnStartupAndOnDatabaseSwitch() throws IOException {
        String source = controllerSource();
        assertThat(source)
                .as("ui.shortcuts used to be saved but never read back (session 10 fix)")
                .contains("service.globalShortcuts()");
    }

    @Test
    void theNavigatorStaysFreeOfJavaFxSoItCanBeTested() throws IOException {
        String source = Files.readString(uiRoot().resolve("CardNavigator.java"), StandardCharsets.UTF_8);
        assertThat(source).doesNotContain("javafx");
        assertThat(List.of(source)).isNotEmpty();
    }
}
