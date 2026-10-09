package com.personalkanban.ui;

import com.personalkanban.domain.board.LabelSuggester;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keyboard selection of label suggestions inside a REAL Dialog — the
 * environment where the user reported needing the mouse.
 *
 * <p>While the suggestion popup is showing, JavaFX's PopupWindow event
 * redirector (owner-window capture phase) forwards every key as a copy into
 * the popup's scene and, when that copy is consumed there, suppresses the
 * original before it reaches the TextField — or the Dialog's Enter scene
 * accelerator. The autocomplete must therefore apply the highlighted
 * suggestion from a filter on the POPUP scene: ArrowDown/ArrowUp move the
 * highlight, Enter/Tab apply it, and the Dialog must stay open. With the
 * popup hidden, Enter falls through to the Dialog's OK button as usual.</p>
 */
class LabelAutoCompleteKeyboardTest {

    @BeforeAll
    static void startJavaFxToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // Toolkit already initialized by another test in this JVM.
        }
        // Closing the last window (e.g. the Dialog at the end of a test)
        // would otherwise trigger JavaFX's implicit exit: the FX Application
        // Thread stops and every later Platform.runLater hangs forever.
        Platform.setImplicitExit(false);
    }

    /** Runs an action on the FX thread and waits for it to finish. */
    private static void fx(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });
        assertThat(done.await(10, TimeUnit.SECONDS))
                .as("FX action must finish").isTrue();
        if (error.get() != null) {
            throw new AssertionError("FX action failed", error.get());
        }
    }

    /**
     * The 8-argument KeyEvent constructor is the only public one usable in
     * tests: (eventType, character, text, code, shift, control, alt, meta).
     */
    private static KeyEvent press(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                false, false, false, false);
    }

    /** Shown Dialog with a label-autocomplete TextField; returns via refs. */
    private static void showDialog(AtomicReference<Dialog<Boolean>> dialogRef,
                                   AtomicReference<TextField> fieldRef,
                                   AtomicReference<LabelAutoComplete> acRef)
            throws Exception {
        fx(() -> {
            Dialog<Boolean> dialog = new Dialog<>();
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
            TextField field = new TextField();
            dialog.getDialogPane().setContent(field);
            LabelAutoComplete ac = LabelAutoComplete.attach(field,
                    new LabelSuggester(List.of("alpha", "alpine", "beta")), List::of);
            dialog.show();
            dialogRef.set(dialog);
            fieldRef.set(field);
            acRef.set(ac);
        });
        for (int i = 0; i < 100 && !dialogRef.get().isShowing(); i++) {
            Thread.sleep(50);
        }
        assertThat(dialogRef.get().isShowing()).isTrue();
    }

    /** Types "alp" and waits out the 120 ms debounce until the popup shows. */
    private static void typeAndShowPopup(AtomicReference<TextField> fieldRef,
                                         LabelAutoComplete ac) throws Exception {
        fx(() -> {
            TextField field = fieldRef.get();
            field.setText("alp");
            field.positionCaret(field.getText().length());
        });
        Thread.sleep(500); // debounce + FX pulses
        fx(() -> { });
        assertThat(ac.isPopupVisible())
                .as("suggestion popup must be visible after typing a match").isTrue();
    }

    @Test
    void programmaticKeyEventReachesOwnFilter() throws Exception {
        AtomicReference<Boolean> seen = new AtomicReference<>();
        fx(() -> {
            TextField bare = new TextField();
            bare.addEventFilter(KeyEvent.KEY_PRESSED, e -> seen.set(true));
            bare.fireEvent(press(KeyCode.DOWN));
        });
        assertThat(seen.get()).as("bare field: filter must see fired key").isTrue();
    }

    @Test
    void programmaticKeyEventReachesFilterOfFieldInsideShownDialog() throws Exception {
        AtomicReference<Dialog<Boolean>> dialogRef = new AtomicReference<>();
        AtomicReference<TextField> fieldRef = new AtomicReference<>();
        AtomicReference<Boolean> seen = new AtomicReference<>();

        fx(() -> {
            Dialog<Boolean> dialog = new Dialog<>();
            dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
            TextField field = new TextField();
            dialog.getDialogPane().setContent(field);
            field.addEventFilter(KeyEvent.KEY_PRESSED, e -> seen.set(true));
            dialog.show();
            dialogRef.set(dialog);
            fieldRef.set(field);
        });
        for (int i = 0; i < 100 && !dialogRef.get().isShowing(); i++) {
            Thread.sleep(50);
        }
        fx(() -> fieldRef.get().fireEvent(press(KeyCode.DOWN)));
        fx(() -> { });
        assertThat(seen.get()).as("dialog field: filter must see fired key").isTrue();
        fx(() -> dialogRef.get().hide());
    }

    @Test
    void arrowDownThenEnterAppliesSuggestionInsteadOfClosingTheDialog()
            throws Exception {
        AtomicReference<Dialog<Boolean>> dialogRef = new AtomicReference<>();
        AtomicReference<TextField> fieldRef = new AtomicReference<>();
        AtomicReference<LabelAutoComplete> acRef = new AtomicReference<>();
        showDialog(dialogRef, fieldRef, acRef);
        LabelAutoComplete ac = acRef.get();
        typeAndShowPopup(fieldRef, ac);

        // ArrowDown moves the highlight to the second suggestion, Enter
        // applies it — and must NOT be stolen by the OK button's Enter
        // scene accelerator (the popup-side filter consumes both copies).
        fx(() -> fieldRef.get().fireEvent(press(KeyCode.DOWN)));
        fx(() -> fieldRef.get().fireEvent(press(KeyCode.ENTER)));
        fx(() -> { });

        TextField field = fieldRef.get();
        assertThat(dialogRef.get().isShowing())
                .as("dialog must stay open (Enter consumed by the popup side)").isTrue();
        assertThat(ac.isPopupVisible())
                .as("popup must hide after applying").isFalse();
        assertThat(field.getText())
                .as("Enter must complete the highlighted suggestion")
                .startsWith("alp").isNotEqualTo("alp");

        // Control: with the popup hidden there is no redirect anymore, so
        // Enter activates the default OK button — proving the accelerator
        // exists and the earlier suppression was caused by the popup.
        fx(() -> fieldRef.get().fireEvent(press(KeyCode.ENTER)));
        fx(() -> { });
        for (int i = 0; i < 100 && dialogRef.get().isShowing(); i++) {
            Thread.sleep(50);
        }
        assertThat(dialogRef.get().isShowing())
                .as("Enter with popup hidden must activate the default OK button")
                .isFalse();
    }

    @Test
    void tabAppliesHighlightedSuggestion() throws Exception {
        AtomicReference<Dialog<Boolean>> dialogRef = new AtomicReference<>();
        AtomicReference<TextField> fieldRef = new AtomicReference<>();
        AtomicReference<LabelAutoComplete> acRef = new AtomicReference<>();
        showDialog(dialogRef, fieldRef, acRef);
        LabelAutoComplete ac = acRef.get();
        typeAndShowPopup(fieldRef, ac);

        fx(() -> fieldRef.get().fireEvent(press(KeyCode.TAB)));
        fx(() -> { });

        TextField field = fieldRef.get();
        assertThat(dialogRef.get().isShowing())
                .as("dialog must stay open (Tab consumed by the popup side)").isTrue();
        assertThat(ac.isPopupVisible())
                .as("popup must hide after applying").isFalse();
        assertThat(field.getText())
                .as("Tab must complete the first/highlighted suggestion")
                .startsWith("alp").isNotEqualTo("alp");

        fx(() -> {
            if (dialogRef.get().isShowing()) {
                dialogRef.get().hide();
            }
        });
    }
}
