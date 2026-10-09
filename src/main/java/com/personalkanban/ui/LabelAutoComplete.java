package com.personalkanban.ui;

import com.personalkanban.domain.board.LabelSuggester;
import javafx.animation.PauseTransition;
import javafx.geometry.Bounds;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Popup;
import javafx.util.Duration;

/**
 * Label autocomplete popup (Pure Fabrication over the pure
 * {@link LabelSuggester}). Wraps any TextField: as the user types, a small
 * list appears under the caret showing matching known labels; select with
 * mouse click, or with ArrowDown/ArrowUp + Enter/Tab. Escape hides it.
 * Typing on always remains free — the popup merely accelerates, it never
 * blocks (it is a suggestion, not a constraint).
 *
 * <p>Keyboard interaction is owned by the attached TextField's key handler;
 * the popup only exposes the list and the apply action.</p>
 */
final class LabelAutoComplete {

    /** Contract with the host field: read state, replace token, move caret. */
    interface Host {
        String text();

        int caret();

        void replaceToken(String suggestion);

        /** Labels already present on the card being edited (excluded). */
        java.util.List<String> exclude();
    }

    private final LabelSuggester suggester;
    private final Host host;
    private final javafx.stage.Popup popup = new javafx.stage.Popup();
    private final ListView<String> list = new ListView<>();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(120));
    private boolean applying;

    /**
     * Optional dynamic vocabulary refresh (the board's labels change while
     * the app runs; long-lived fields like the filter read it on every
     * refresh). When absent, the suggester built at construction time is used.
     */
    private java.util.function.Supplier<java.util.List<String>> vocabularySupplier;

    void setVocabularySupplier(java.util.function.Supplier<java.util.List<String>> supplier) {
        this.vocabularySupplier = supplier;
    }

    private LabelSuggester effectiveSuggester() {
        if (vocabularySupplier == null) {
            return suggester;
        }
        return new LabelSuggester(vocabularySupplier.get());
    }

    LabelAutoComplete(LabelSuggester suggester, Host host, javafx.scene.control.TextField field) {
        this.suggester = suggester;
        this.host = host;
        list.getStyleClass().add("label-suggestions");
        list.setPrefSize(240, 160);
        list.setPlaceholder(null);
        list.setOnMouseClicked(event -> {
            if (applySelected()) {
                hide();
            }
        });
        popup.getContent().add(list);
        popup.setAutoHide(true);
        installPopupKeyFilter();

        field.textProperty().addListener((obs, was, now) -> scheduleShow());
        field.caretPositionProperty().addListener(obs -> scheduleShow());
        field.focusedProperty().addListener((obs, was, now) -> {
            if (!now) {
                hide();
            }
        });

        // Key handling lives on the field so typing flow stays native.
        field.setOnKeyPressed(event -> {
            boolean visible = popup.isShowing();
            switch (event.getCode()) {
                case DOWN -> {
                    if (visible) {
                        list.getSelectionModel().selectNext();
                        event.consume();
                    }
                }
                case UP -> {
                    if (visible) {
                        list.getSelectionModel().selectPrevious();
                        event.consume();
                    }
                }
                case ENTER, TAB -> {
                    if (visible && applySelected()) {
                        hide();
                        event.consume();
                    }
                }
                case ESCAPE -> {
                    if (visible) {
                        hide();
                        event.consume();
                    }
                }
                default -> { }
            }
        });
    }

    /**
     * Keyboard selection must be handled on the POPUP side, not on the field.
     * While the popup is showing, JavaFX's PopupWindow event redirector (owner
     * window capture phase) forwards every key as a copy into the popup's
     * scene, and if that copy is consumed there the ORIGINAL event is
     * suppressed before ever reaching the TextField — the field's own
     * onKeyPressed below then never runs for arrows/Enter/Tab. Consuming the
     * copy here therefore applies the suggestion AND keeps the original away
     * from scene-level handlers (e.g. a Dialog's Enter accelerator, which
     * would otherwise close the dialog instead of completing the label).
     *
     * <p>The filter lives on the popup scene so it covers both redirect
     * targets: the scene itself (no focus owner) and a focused node such as
     * the suggestion list (scene capture runs before the node's handlers).</p>
     */
    private void installPopupKeyFilter() {
        javafx.scene.Scene popupScene = list.getScene();
        if (popupScene == null) {
            return;
        }
        popupScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!popup.isShowing()) {
                return;
            }
            switch (event.getCode()) {
                case DOWN -> {
                    list.getSelectionModel().selectNext();
                    event.consume();
                }
                case UP -> {
                    list.getSelectionModel().selectPrevious();
                    event.consume();
                }
                case ENTER, TAB -> {
                    if (applySelected()) {
                        hide();
                        event.consume();
                    }
                }
                default -> { }
            }
        });
    }

    private void scheduleShow() {
        if (applying) {
            return;
        }
        debounce.setOnFinished(e -> refresh());
        debounce.playFromStart();
    }

    private void refresh() {
        LabelSuggester effective = effectiveSuggester();
        String token = effective.currentToken(host.text(), host.caret());
        java.util.List<String> matches = effective.suggestions(token, host.exclude());
        if (matches.isEmpty()) {
            hide();
            return;
        }
        list.getItems().setAll(matches);
        list.getSelectionModel().clearSelection();
        list.getSelectionModel().selectFirst();
        if (!popup.isShowing()) {
            javafx.scene.control.TextField field = ownerField;
            if (field != null && field.getScene() != null) {
                Bounds bounds = field.localToScreen(field.getBoundsInLocal());
                if (bounds != null) {
                    popup.show(field, bounds.getMinX(), bounds.getMaxY());
                }
            }
        }
    }

    private javafx.scene.control.TextField ownerField;

    void bindOwner(javafx.scene.control.TextField field) {
        this.ownerField = field;
    }

    /** Test hook: whether the suggestion popup is currently on screen. */
    boolean isPopupVisible() {
        return popup.isShowing();
    }

    private boolean applySelected() {
        String selected = list.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return false;
        }
        applying = true;
        try {
            host.replaceToken(selected);
        } finally {
            applying = false;
        }
        return true;
    }

    private void hide() {
        popup.hide();
    }

    static LabelAutoComplete attach(javafx.scene.control.TextField field,
                                    LabelSuggester suggester,
                                    java.util.function.Supplier<java.util.List<String>> exclude) {
        LabelAutoComplete ac = new LabelAutoComplete(suggester, new Host() {
            @Override
            public String text() {
                return field.getText();
            }

            @Override
            public int caret() {
                return field.getCaretPosition();
            }

            @Override
            public void replaceToken(String suggestion) {
                field.setText(suggester.apply(field.getText(), field.getCaretPosition(), suggestion));
                field.positionCaret(field.getText().length());
            }

            @Override
            public java.util.List<String> exclude() {
                return exclude.get();
            }
        }, field);
        ac.bindOwner(field);
        return ac;
    }
}
