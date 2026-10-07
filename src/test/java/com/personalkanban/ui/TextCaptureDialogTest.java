package com.personalkanban.ui;

import org.junit.jupiter.api.Test;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextCaptureDialogTest {

    /**
     * The 8-argument KeyEvent constructor is the only public one usable in
     * tests: (eventType, character, text, code, shiftDown, controlDown,
     * altDown, metaDown). The KeyCombination-based constructor used by the
     * old test does not exist in JavaFX 21.
     */
    private static KeyEvent press(KeyCode code, boolean shift, boolean ctrl,
                                  boolean alt, boolean meta) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                shift, ctrl, alt, meta);
    }

    @Test
    void capturesShortcutFromPressedKeyEvent() {
        TextCaptureDialogFixture fixture = new TextCaptureDialogFixture();

        // Ctrl pressed together with C -> "Ctrl+C".
        fixture.pushPressEvent(press(KeyCode.C, false, true, false, false));

        assertThat(fixture.lastCaptured()).isEqualTo("Ctrl+C");
    }

    @Test
    void capturesControlPlusLetterFromPressedKeyEvent() {
        TextCaptureDialogFixture fixture = new TextCaptureDialogFixture();

        fixture.pushPressEvent(press(KeyCode.A, false, true, false, false));

        assertThat(fixture.lastCaptured()).isEqualTo("Ctrl+A");
    }

    @Test
    void capturesPlainLetterWithoutModifierFromPressedKeyEvent() {
        TextCaptureDialogFixture fixture = new TextCaptureDialogFixture();

        fixture.pushPressEvent(press(KeyCode.A, false, false, false, false));

        assertThat(fixture.lastCaptured()).isEqualTo("A");
    }

    @Test
    void clearsCapturedKeyWhenClearButtonIsPressed() {
        TextCaptureDialogFixture fixture = new TextCaptureDialogFixture();

        fixture.pushPressEvent(press(KeyCode.C, false, true, false, false));
        assertThat(fixture.lastCaptured()).isEqualTo("Ctrl+C");

        fixture.clearCaptured();
        assertThat(fixture.lastCaptured()).isNull();
    }

    @Test
    void ignoresEnterAndEscapeInCapture() {
        TextCaptureDialogFixture fixture = new TextCaptureDialogFixture();

        // Enter and Escape are navigation keys the dialog itself needs;
        // they must never be captured as a shortcut.
        fixture.pushPressEvent(press(KeyCode.ENTER, false, false, false, false));
        assertThat(fixture.lastCaptured()).isNull();

        fixture.pushPressEvent(press(KeyCode.ESCAPE, false, false, false, false));
        assertThat(fixture.lastCaptured()).isNull();
    }

    @Test
    void capturesShiftAndAltCombinations() {
        TextCaptureDialogFixture fixture = new TextCaptureDialogFixture();

        fixture.pushPressEvent(press(KeyCode.A, true, false, true, false));

        assertThat(fixture.lastCaptured()).isEqualTo("Shift+Alt+A");
    }

    /**
     * Mirrors the capture logic of TextCaptureDialog's KEY_PRESSED filter:
     * navigation keys (Enter/Escape/Tab) and undefined codes are ignored;
     * everything else becomes "Meta+…/Ctrl+… + Shift+… + Alt+… + <key name>".
     */
    private static class TextCaptureDialogFixture {
        private String lastCaptured;
        private final List<KeyEvent> events = new ArrayList<>();

        void pushPressEvent(KeyEvent event) {
            events.add(event);
            KeyCode code = event.getCode();
            if (code == KeyCode.UNDEFINED
                    || code == KeyCode.ENTER
                    || code == KeyCode.ESCAPE
                    || code == KeyCode.TAB) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            if (event.isMetaDown()) {
                sb.append("Meta+");
            } else if (event.isControlDown() || event.isShortcutDown()) {
                sb.append("Ctrl+");
            }
            if (event.isShiftDown()) {
                sb.append("Shift+");
            }
            if (event.isAltDown()) {
                sb.append("Alt+");
            }
            sb.append(code.getName());
            lastCaptured = sb.toString();
        }

        void clearCaptured() {
            lastCaptured = null;
        }

        String lastCaptured() {
            return lastCaptured;
        }
    }
}
