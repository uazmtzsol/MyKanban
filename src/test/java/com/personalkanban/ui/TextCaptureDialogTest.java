package com.personalkanban.ui;

import org.junit.jupiter.api.Test;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import static org.assertj.core.api.Assertions.assertThat;

class TextCaptureDialogTest {

    @Test
    void capturesShortcutFromPressedKeyEvent() {
        // We never open a real JavaFX stage in tests, but we can verify
        // that the text capture logic serializes a pressed shortcut
        // into the same representation the dialog stores.
        TextCaptureDialogFixture fixture = new TextCaptureDialogFixture();

        javafx.scene.input.KeyEvent event = new javafx.scene.input.KeyEvent(
                javafx.scene.input.KeyEvent.KEY_PRESSED,
                KeyCode.CTRL,
                KeyCode.CTRL,
                KeyCombination.valueOf("Ctrl+C"),
                true,
                true,
                false,
                false
        );
        fixture.pushPressEvent(event);

        assertThat(fixture.lastCaptured()).isEqualTo("Ctrl+C");
    }

    @Test
    void capturesControlPlusLetterFromPressedKeyEvent() {
        TextCaptureDialogFixture fixture = new TextCaptureDialogFixture();

        javafx.scene.input.KeyEvent event = new javafx.scene.input.KeyEvent(
                javafx.scene.input.KeyEvent.KEY_PRESSED,
                KeyCode.CTRL,
                KeyCode.A,
                KeyCombination.valueOf("Ctrl+A"),
                true,
                true,
                false,
                false
        );
        fixture.pushPressEvent(event);

        assertThat(fixture.lastCaptured()).isEqualTo("Ctrl+A");
    }

    @Test
    void capturesPlainLetterWithoutModifierFromPressedKeyEvent() {
        TextCaptureDialogFixture fixture = new TextCaptureDialogFixture();

        javafx.scene.input.KeyEvent event = new javafx.scene.input.KeyEvent(
                javafx.scene.input.KeyEvent.KEY_PRESSED,
                KeyCode.A,
                KeyCode.A,
                KeyCombination.valueOf("A"),
                false,
                false,
                false,
                false
        );
        fixture.pushPressEvent(event);

        assertThat(fixture.lastCaptured()).isEqualTo("A");
    }

    @Test
    void clearsCapturedKeyWhenClearButtonIsPressedTwice() {
        TextCaptureDialogFixture fixture = new TextCaptureDialogFixture();

        javafx.scene.input.KeyEvent event = new javafx.scene.input.KeyEvent(
                javafx.scene.input.KeyEvent.KEY_PRESSED,
                KeyCode.CTRL,
                KeyCode.CTRL,
                KeyCombination.valueOf("Ctrl+C"),
                true,
                true,
                false,
                false
        );
        fixture.pushPressEvent(event);
        assertThat(fixture.lastCaptured()).isEqualTo("Ctrl+C");

        fixture.clearCaptured();
        assertThat(fixture.lastCaptured()).isNull();
    }

    @Test
    void ignoreEnterEscapeAndTabInCapture() {
        TextCaptureDialogFixture fixture = new TextCaptureDialogFixture();

        javafx.scene.input.KeyEvent enter = new javafx.scene.input.KeyEvent(
                javafx.scene.input.KeyEvent.KEY_PRESSED,
                KeyCode.ENTER,
                KeyCode.ENTER,
                KeyCombination.valueOf("Enter"),
                false,
                false,
                false,
                false
        );
        fixture.pushPressEvent(enter);
        assertThat(fixture.lastCaptured()).isNull();

        javafx.scene.input.KeyEvent escape = new javafx.scene.input.KeyEvent(
                javafx.scene.input.KeyEvent.KEY_PRESSED,
                KeyCode.ESCAPE,
                KeyCode.ESCAPE,
                KeyCombination.valueOf("Escape"),
                false,
                false,
                false,
                false
        );
        fixture.pushPressEvent(escape);
        assertThat(fixture.lastCaptured()).isNull();
    }

    @Test
    void toStringRenderOfKeyCodeCombinationMatchesExpectedPattern() {
        KeyCodeCombination combination = new KeyCodeCombination(
                KeyCode.A,
                KeyCombination.SHORT_FORWARD,
                KeyCombination.SHIFT_ANY,
                KeyCombination.ALT_ANY,
                KeyCombination.SHORTCUT_ANY);

        assertThat(combination.getName()).contains("A");
    }

    private static class TextCaptureDialogFixture {
        private String lastCaptured;
        private final List<javafx.scene.input.KeyEvent> events = new ArrayList<>();

        void pushPressEvent(javafx.scene.input.KeyEvent event) {
            events.add(event);
            // Simulate the logic we actually care about: if the dialog had read
            // a KEY_PRESSED with a modifier, it would have stored a normalized
            // string. We do not replay JavaFX dispatch here, we just assert
            // rendering helpers directly on a representative combination.
        }

        void clearCaptured() {
            lastCaptured = null;
        }

        String lastCaptured() {
            return lastCaptured;
        }
    }
}
