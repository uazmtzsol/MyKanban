package com.personalkanban.ui;

import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.ui.markdown.Markdown;
import com.personalkanban.ui.theme.ThemeManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/**
 * Non-modal card detail window (Pure Fabrication): a markdown editor with a
 * live HTML preview side by side. The description itself remains plain text
 * in the domain — rendering is a pure UI concern. The preview WebView runs
 * with JavaScript disabled, so note content can never execute scripts.
 */
final class CardDetailWindow {

    private static final double SPLIT_RATIO = 0.5;

    private final CardId cardId;
    private final BoardController board;
    private final I18n i18n;
    private final ThemeManager themeManager;

    private final Stage stage = new Stage();
    private final TextArea editor = new TextArea();
    private final WebView preview = new WebView();

    private CardDetailWindow(Card card, BoardController board, I18n i18n, ThemeManager themeManager) {
        this.cardId = card.id();
        this.board = board;
        this.i18n = i18n;
        this.themeManager = themeManager;

        editor.setText(card.description());
        editor.getStyleClass().add("md-editor");
        editor.setWrapText(true);
        editor.textProperty().addListener((obs, old, value) -> renderPreview(value));
        editor.setTooltip(new Tooltip(i18n.text("card.md.editor.tip")));

        preview.getEngine().setJavaScriptEnabled(false);
        preview.setPrefHeight(300);

        Label titleLabel = new Label(card.title());
        titleLabel.getStyleClass().add("detail-title");
        titleLabel.setWrapText(true);

        var split = new javafx.scene.control.SplitPane(wrapEditor(), preview);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(SPLIT_RATIO);
        VBox.setVgrow(split, Priority.ALWAYS);

        Button save = new Button(i18n.text("dialog.ok"));
        save.setOnAction(e -> onSave());
        Button cancel = new Button(i18n.text("dialog.cancel"));
        cancel.setOnAction(e -> stage.close());
        ButtonBar buttons = new ButtonBar();
        buttons.getButtons().addAll(save, cancel);
        buttons.setPadding(new Insets(8));

        BorderPane layout = new BorderPane();
        layout.setTop(titleLabel);
        layout.setCenter(split);
        layout.setBottom(buttons);
        layout.getStyleClass().add("detail-window");
        layout.setPadding(new Insets(10));

        Scene scene = new Scene(layout, 900, 560);
        scene.getStylesheets().add(themeManager.stylesheet());
        stage.setScene(scene);
        stage.setTitle(i18n.text("card.detail.title") + " \u2014 " + card.title());

        renderPreview(editor.getText());
    }

    /** Opens (or focuses) a detail window for the given card. */
    static void open(Card card, BoardController board, I18n i18n, ThemeManager themeManager) {
        CardDetailWindow window = new CardDetailWindow(card, board, i18n, themeManager);
        window.stage.show();
        window.stage.toFront();
        Platform.runLater(window.editor::requestFocus);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private VBox wrapEditor() {
        Label caption = new Label(i18n.text("card.md.editor"));
        caption.getStyleClass().add("detail-caption");
        VBox editorBox = new VBox(4, caption, editor);
        VBox.setVgrow(editor, Priority.ALWAYS);
        return editorBox;
    }

    private void renderPreview(String markdown) {
        boolean dark = themeManager.isDark();
        preview.getEngine().loadContent(Markdown.toStyledDocument(
                markdown,
                dark ? "#e5e7eb" : "#1f2937",
                dark ? "#16181d" : "#ffffff",
                dark ? "#262a33" : "#f0f2f5",
                dark ? "#343947" : "#d7dbe2",
                dark ? "#42a5f5" : "#1976d2"));
    }

    private void onSave() {
        String markdown = editor.getText();
        board.onDescriptionSaved(cardId, markdown);
        stage.close();
    }
}
