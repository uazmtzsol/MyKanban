package com.personalkanban.ui;

import com.personalkanban.application.BoardService;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.input.KeyCombination;

/**
 * Owns the undo/redo user intents (GRASP Controller). Exposes observable
 * flags so toolbar buttons can bind their disabled state, and registers
 * Ctrl+Z / Ctrl+Shift+Z accelerators on the scene.
 */
final class UndoRedoController {

    private final BoardService service;
    private final I18n i18n;
    private final Runnable afterChange;
    private final BooleanProperty canUndo = new SimpleBooleanProperty();
    private final BooleanProperty canRedo = new SimpleBooleanProperty();

    UndoRedoController(BoardService service, I18n i18n, Runnable afterChange) {
        this.service = service;
        this.i18n = i18n;
        this.afterChange = afterChange;
        sync();
    }

    BooleanProperty canUndoProperty() {
        return canUndo;
    }

    BooleanProperty canRedoProperty() {
        return canRedo;
    }

    void undo() {
        try {
            service.undo();
            sync();
            afterChange.run();
        } catch (RuntimeException e) {
            error("Could not undo: " + e.getMessage());
        }
    }

    void redo() {
        try {
            service.redo();
            sync();
            afterChange.run();
        } catch (RuntimeException e) {
            error("Could not redo: " + e.getMessage());
        }
    }

    void bindScene(Scene scene) {
        scene.getAccelerators().put(KeyCombination.valueOf("Shortcut+Z"), this::undo);
        scene.getAccelerators().put(KeyCombination.valueOf("Shortcut+Shift+Z"), this::redo);
    }

    void sync() {
        canUndo.set(service.canUndo());
        canRedo.set(service.canRedo());
    }

    private void error(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.CLOSE);
        alert.setHeaderText(i18n.text("error.title"));
        alert.showAndWait();
    }
}
