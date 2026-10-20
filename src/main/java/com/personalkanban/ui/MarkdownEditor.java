package com.personalkanban.ui;

import com.personalkanban.ui.markdown.Markdown;
import com.personalkanban.ui.theme.ThemeManager;
import javafx.scene.control.Button;
import javafx.scene.control.IndexRange;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;

import java.util.ArrayList;
import java.util.List;

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
    private final HBox header = new HBox(8);
    private final I18n i18n;
    private final ThemeManager themeManager;
    // Formatting toolbar buttons (user request): they only make sense while
    // editing, so they are disabled in preview mode.
    private final List<Button> formatButtons = new ArrayList<>();

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

        preview.getEngine().setJavaScriptEnabled(false);

        editor.setMinHeight(minHeight);
        preview.setMinHeight(minHeight);
        preview.setPrefHeight(minHeight);

        toggle.getStyleClass().add("md-mode-toggle");
        toggle.setOnAction(e -> applyMode());
        header.getChildren().add(toggle);
        addFormatToolbar();

        root.getChildren().addAll(header, center);
        VBox.setVgrow(center, Priority.ALWAYS);
        center.setMinHeight(minHeight);
        applyMode();
    }

    /** The whole editor (header + surface). */
    Pane node() {
        return root;
    }

    /**
     * Adds an extra button to the editor's own header row, next to the
     * edit/view mode toggle. The host uses it to keep its actions (save, add)
     * on the same line as the mode switch instead of far below the surface.
     */
    void addHeaderButton(Button button) {
        header.getChildren().add(button);
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
        formatButtons.forEach(button -> button.setDisable(view));
        if (view) {
            renderPreview();
        }
    }

    // ------------------------------------------------------------------
    // Formatting toolbar (user request): wrap the selection with markdown
    // markers so nothing has to be typed by hand. Each action keeps the
    // selection (or leaves the caret inside the markers when there is none).
    // ------------------------------------------------------------------

    private void addFormatToolbar() {
        header.getChildren().add(formatButton("B", "md.format.bold", () -> wrap("**", "**")));
        header.getChildren().add(formatButton("I", "md.format.italic", () -> wrap("*", "*")));
        header.getChildren().add(formatButton("H", "md.format.heading", () -> toggleLinePrefix("## ")));
        header.getChildren().add(formatButton("\u2022", "md.format.list", () -> toggleLinePrefix("- ")));
        header.getChildren().add(formatButton("\uD83D\uDD17", "md.format.link", this::insertLink));
        header.getChildren().add(formatButton("</>", "md.format.code", () -> wrap("`", "`")));
        header.getChildren().add(formatButton("\u232B", "md.format.clear", this::clearFormatting));
    }

    private Button formatButton(String glyph, String tipKey, Runnable action) {
        Button button = new Button(glyph);
        button.getStyleClass().add("md-format-button");
        button.setTooltip(new Tooltip(i18n.text(tipKey)));
        button.setFocusTraversable(false);
        button.setOnAction(e -> {
            action.run();
            editor.requestFocus();
        });
        formatButtons.add(button);
        return button;
    }

    /** Wraps the selection (or inserts an empty pair at the caret). */
    private void wrap(String before, String after) {
        IndexRange range = editor.getSelection();
        if (range.getLength() == 0) {
            int caret = editor.getCaretPosition();
            editor.insertText(caret, before + after);
            editor.positionCaret(caret + before.length());
            return;
        }
        String selected = editor.getText().substring(range.getStart(), range.getEnd());
        editor.replaceText(range, before + selected + after);
        editor.selectRange(range.getStart() + before.length(),
                range.getStart() + before.length() + selected.length());
    }

    /** Adds {@code prefix} to every selected line, or removes it when present. */
    private void toggleLinePrefix(String prefix) {
        String text = editor.getText();
        IndexRange range = editor.getSelection();
        int start = lineStart(text, range.getStart());
        int end = lineEnd(text, range.getEnd());
        String[] lines = text.substring(start, end).split("\n", -1);
        boolean allPrefixed = true;
        boolean anyContent = false;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            anyContent = true;
            if (!line.startsWith(prefix)) {
                allPrefixed = false;
            }
        }
        StringBuilder rebuilt = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                rebuilt.append('\n');
            }
            String line = lines[i];
            if (line.isBlank()) {
                rebuilt.append(line);
            } else if (allPrefixed && anyContent) {
                rebuilt.append(line.substring(prefix.length()));
            } else {
                rebuilt.append(prefix).append(line);
            }
        }
        editor.replaceText(start, end, rebuilt.toString());
        editor.selectRange(start, start + rebuilt.length());
    }

    /** Turns the selection into {@code [text](url)} (url left for the user). */
    private void insertLink() {
        IndexRange range = editor.getSelection();
        String label = range.getLength() == 0
                ? i18n.text("md.format.link.text")
                : editor.getText().substring(range.getStart(), range.getEnd());
        String replacement = "[" + label + "](url)";
        if (range.getLength() == 0) {
            editor.insertText(range.getStart(), replacement);
        } else {
            editor.replaceText(range, replacement);
        }
        // Select the "url" placeholder so it can be typed over directly.
        int urlStart = range.getStart() + 1 + label.length() + 2;
        editor.selectRange(urlStart, urlStart + 3);
    }

    /** Strips the common markdown markers from the selection (or whole text). */
    private void clearFormatting() {
        IndexRange range = editor.getSelection();
        boolean whole = range.getLength() == 0;
        String target = whole
                ? editor.getText()
                : editor.getText().substring(range.getStart(), range.getEnd());
        String cleaned = stripMarkdown(target);
        if (whole) {
            editor.setText(cleaned);
            editor.positionCaret(cleaned.length());
        } else {
            editor.replaceText(range, cleaned);
            editor.selectRange(range.getStart(), range.getStart() + cleaned.length());
        }
    }

    private static String stripMarkdown(String text) {
        String out = text;
        out = out.replaceAll("(?m)^\\s{0,3}#{1,6}\\s+", "");
        out = out.replaceAll("(?m)^\\s{0,3}[-*+]\\s+", "");
        out = out.replaceAll("(?m)^\\s{0,3}\\d+\\.\\s+", "");
        out = out.replaceAll("!?\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
        out = out.replaceAll("(?s)\\*\\*(.+?)\\*\\*", "$1");
        out = out.replaceAll("(?s)__(.+?)__", "$1");
        out = out.replaceAll("(?s)~~(.+?)~~", "$1");
        out = out.replaceAll("(?s)`(.+?)`", "$1");
        out = out.replaceAll("(?s)\\*(.+?)\\*", "$1");
        out = out.replaceAll("(?s)_(.+?)_", "$1");
        return out;
    }

    private static int lineStart(String text, int index) {
        int newline = text.lastIndexOf('\n', Math.max(0, index - 1));
        return newline < 0 ? 0 : newline + 1;
    }

    private static int lineEnd(String text, int index) {
        int newline = text.indexOf('\n', index);
        return newline < 0 ? text.length() : newline;
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
