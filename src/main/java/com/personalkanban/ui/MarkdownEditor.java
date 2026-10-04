package com.personalkanban.ui;

import com.personalkanban.ui.markdown.Markdown;
import com.personalkanban.ui.theme.ThemeManager;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;

/**
 * Reusable multiline text editor with a markdown source mode and a rendered
 * view mode, toggled by a pencil/eye button (user request, session 6). It is
 * meant to replace every multiline editor in the app (card description,
 * notes, time-entry comments, ...). The preview WebView runs with JavaScript
 * disabled, so user text can never execute scripts.
 *
 * <p>The host reads the value with {@link #text()} and owns persistence; the
 * component only handles the edit/view switch and rendering.</p>
 */
final class MarkdownEditor {

    private final TextArea editor = new TextArea();
    private final WebView preview = new WebView();
    private final ToggleButton toggle = new ToggleButton();
    private final StackPane center = new StackPane(editor, preview);
    private final VBox root = new VBox(4);
    private final I18n i18n;
    private final ThemeManager themeManager;

    /**
     * @param initialText  markdown source to edit
     * @param promptKey    i18n key for the editor prompt (may be null)
     * @param minHeight    preferred height of the editing surface
     */
    MarkdownEditor(String initialText, String promptKey, double minHeight,
                   I18n i18n, ThemeManager themeManager) {
        this.i18n = i18n;
        this.themeManager = themeManager;

        editor.getStyleClass().add("md-editor");
        editor.setText(initialText == null ? "" : initialText);
        editor.setWrapText(true);
        if (promptKey != null) {
            editor.setPromptText(i18n.text(promptKey));
        }
        editor.setTooltip(new Tooltip(i18n.text("card.md.editor.tip")));

        preview.getEngine().setJavaScriptEnabled(false);

        editor.setMinHeight(minHeight);
        preview.setMinHeight(minHeight);
        preview.setPrefHeight(minHeight);

        toggle.getStyleClass().add("md-mode-toggle");
        toggle.setOnAction(e -> applyMode());
        HBox header = new HBox(8, toggle);

        root.getChildren().addAll(header, center);
        VBox.setVgrow(center, Priority.ALWAYS);
        center.setMinHeight(minHeight);
        applyMode();
    }

    /** The whole editor (header + surface). */
    Pane node() {
        return root;
    }

    /** Current markdown source, regardless of the visible mode. */
    String text() {
        return editor.getText();
    }

    void setText(String markdown) {
        editor.setText(markdown == null ? "" : markdown);
        if (isViewMode()) {
            renderPreview();
        }
    }

    boolean isViewMode() {
        return toggle.isSelected();
    }

    /** Forces edit mode and focuses the text area (used when opening dialogs). */
    void focusEditor() {
        toggle.setSelected(false);
        applyMode();
        editor.requestFocus();
    }

    private void applyMode() {
        boolean view = toggle.isSelected();
        editor.setVisible(!view);
        editor.setManaged(!view);
        preview.setVisible(view);
        preview.setManaged(view);
        toggle.setText(view ? "\uD83D\uDC41" : "\u270E"); // eye / pencil
        toggle.setTooltip(new Tooltip(i18n.text(view ? "md.mode.edit.tip" : "md.mode.view.tip")));
        if (view) {
            renderPreview();
        }
    }

    private void renderPreview() {
        boolean dark = themeManager != null && themeManager.isDark();
        preview.getEngine().loadContent(Markdown.toStyledDocument(
                editor.getText(),
                dark ? "#e5e7eb" : "#1f2937",
                dark ? "#16181d" : "#ffffff",
                dark ? "#262a33" : "#f0f2f5",
                dark ? "#343947" : "#d7dbe2",
                dark ? "#42a5f5" : "#1976d2"));
    }
}
