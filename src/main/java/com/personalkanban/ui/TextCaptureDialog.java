package com.personalkanban.ui;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.util.Optional;

final class TextCaptureDialog {

    private final I18n i18n;
    private final String title;
    private final String prompt;

    private String captured;

    TextCaptureDialog(I18n i18n, String title, String prompt) {
        this.i18n = i18n;
        this.title = title;
        this.prompt = prompt;
    }

    Optional<String> showAndWait() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(prompt);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);

        VBox box = new VBox(8);
        Text instruction = new Text(i18n.text("shortcut.capture.hint"));
        instruction.getStyleClass().add("shortcut-capture-hint");
        box.getChildren().add(instruction);

        Label renderedKey = new Label(captured == null ? i18n.text("shortcut.key.unset") : captured);
        renderedKey.setWrapText(true);
        box.getChildren().add(renderedKey);

        javafx.scene.control.Button clear = new javafx.scene.control.Button(i18n.text("shortcut.capture.clear"));
        clear.setOnAction(e -> {
            captured = null;
            renderedKey.setText(i18n.text("shortcut.key.unset"));
        });
        box.getChildren().add(clear);

        dialog.getDialogPane().setContent(box);

        dialog.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!event.isControlDown() && !event.isMetaDown() && !event.isAltDown()
                    && !event.isShortcutDown()) {
                // Letters without modifier are also legitimate shortcuts; we accept them
                // as long as they are not navigation keys the dialog itself needs.
            }
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.ESCAPE
                    || event.getCode() == KeyCode.TAB) {
                return;
            }
            String name = buildNameFromEvent(event);
            if (name == null) {
                return;
            }
            try {
                KeyCombination combination = KeyCombination.valueOf(name);
                captured = renderKeyCombination(combination);
            } catch (IllegalArgumentException e) {
                // If the current press does not correspond to a valid KeyCombination
                // name (e.g. an unrecognised key), just ignore it.
                return;
            }
        });

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            if (captured == null) {
                return "";
            }
            return captured;
        });

        return dialog.showAndWait();
    }


    private String renderKeyCombination(KeyCombination combination) {
        KeyCode code = ((KeyCodeCombination) combination).getCode();
        String text = combination.toString();
        boolean hasCtrl = text.contains("Ctrl");
        boolean isMeta = text.contains("Meta");
        boolean hasAlt = text.contains("Alt");
        boolean hasShift = text.contains("Shift");
        StringBuilder sb = new StringBuilder();
        if (isMeta) {
            sb.append("Meta+");
        } else if (hasCtrl && !isMeta) {
            sb.append("Ctrl+");
        }
        if (hasShift && !isMeta) {
            sb.append("Shift+");
        }
        if (hasAlt && !isMeta) {
            sb.append("Alt+");
        }
        sb.append(code.getName());
        return sb.toString();
    }

    /**
     * Builds a KeyCombination name from a KeyEvent without relying on
     * KeyEvent.getCombination() or on KeyCombination.Modifier constants,
     * which are not available in this JavaFX version. The produced names
     * look like "Ctrl+A", "Ctrl+Shift+A", "Alt+Tab" or "A", which is what
     * KeyCombination.valueOf() already accepts elsewhere in the app.
     */
    private String buildNameFromEvent(KeyEvent event) {
        KeyCode code = event.getCode();
        if (code == KeyCode.UNDEFINED) {
            return null;
        }
        boolean ctrl = event.isControlDown();
        boolean meta = event.isMetaDown();
        boolean alt = event.isAltDown();
        boolean shift = event.isShiftDown();
        boolean shortcut = event.isShortcutDown();
        StringBuilder sb = new StringBuilder();
        if (meta) {
            sb.append("Meta+");
        } else if (ctrl || shortcut) {
            sb.append("Ctrl+");
        }
        if (shift) {
            sb.append("Shift+");
        }
        if (alt) {
            sb.append("Alt+");
        }
        sb.append(code.getName());
        return sb.toString();
    }
}
