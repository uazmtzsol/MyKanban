package com.personalkanban.ui;

import com.personalkanban.application.GlobalSearch;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Locale;
import java.util.Optional;

/**
 * Global search dialog (session 14, user request B2): type once, look through
 * every board.
 *
 * <p>Results update as the user types — the search is a plain in-memory scan
 * of the catalogue, so there is nothing to wait for — and each row says which
 * board, which card and <em>where</em> it matched, with a short excerpt. Enter
 * or a double click opens the selected hit; the dialog returns it, and
 * {@code BoardController} is the one that opens the board and marks the card
 * (the UI never mutates the service from here).</p>
 */
final class GlobalSearchDialog {

    private final GlobalSearch search;
    private final I18n i18n;

    GlobalSearchDialog(GlobalSearch search, I18n i18n) {
        this.search = search;
        this.i18n = i18n;
    }

    /** Shows the dialog; the result is the hit the user opened, if any. */
    Optional<GlobalSearch.Hit> show() {
        Dialog<GlobalSearch.Hit> dialog = new Dialog<>();
        dialog.setTitle(i18n.text("search.title"));
        dialog.setHeaderText(i18n.text("search.hint"));
        dialog.initModality(javafx.stage.Modality.WINDOW_MODAL);
        dialog.setResizable(true);

        TextField query = new TextField();
        query.setPromptText(i18n.text("search.prompt"));
        query.setPrefWidth(420);

        Label status = new Label();
        status.getStyleClass().add("detail-title");

        ListView<GlobalSearch.Hit> results = new ListView<>();
        results.setPrefHeight(320);
        results.setCellFactory(list -> new HitCell());
        results.setPlaceholder(new Label(i18n.text("search.empty")));

        VBox content = new VBox(8, query, status, results);
        content.setPadding(new Insets(10));
        VBox.setVgrow(results, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(560);

        ButtonType openType = new ButtonType(i18n.text("search.open"),
                ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(openType, ButtonType.CANCEL);

        // The field is what the user came for: it takes the focus, not "Abrir".
        dialog.setOnShown(event -> javafx.application.Platform.runLater(query::requestFocus));

        Runnable runSearch = () -> {
            GlobalSearch.Result result = search.search(query.getText(),
                    GlobalSearch.DEFAULT_LIMIT_PER_BOARD);
            results.setItems(FXCollections.observableArrayList(result.hits()));
            if (result.hits().isEmpty()) {
                status.setText(i18n.text("search.result.none"));
            } else if (result.truncated()) {
                status.setText(i18n.text("search.result.count.capped",
                        result.hits().size(), GlobalSearch.DEFAULT_LIMIT_PER_BOARD));
            } else {
                status.setText(i18n.text("search.result.count", result.hits().size()));
            }
            results.getSelectionModel().selectFirst();
        };
        query.textProperty().addListener((obs, old, value) -> runSearch.run());

        // Enter in the field opens the first hit without reaching for the button.
        query.setOnAction(event -> openSelected(dialog, results));
        results.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                openSelected(dialog, results);
            }
        });

        dialog.setResultConverter(button -> button == openType
                ? results.getSelectionModel().getSelectedItem()
                : null);
        runSearch.run();
        return Optional.ofNullable(dialog.showAndWait().orElse(null));
    }

    private void openSelected(Dialog<GlobalSearch.Hit> dialog,
                              ListView<GlobalSearch.Hit> results) {
        GlobalSearch.Hit selected = results.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        dialog.setResult(selected);
        dialog.close();
    }

    /** One result: "Board › Title" plus where it matched and the excerpt. */
    private final class HitCell extends ListCell<GlobalSearch.Hit> {

        @Override
        protected void updateItem(GlobalSearch.Hit hit, boolean empty) {
            super.updateItem(hit, empty);
            if (empty || hit == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Label title = new Label(hit.boardName() + " › " + hit.card().title());
            title.getStyleClass().add("detail-title");
            Label detail = new Label(i18n.text(fieldKey(hit)) + ": " + hit.snippet());
            detail.setWrapText(true);

            HBox row = new HBox(8, new VBox(2, title, detail));
            row.setAlignment(Pos.CENTER_LEFT);
            setText(null);
            setGraphic(row);
        }

        private String fieldKey(GlobalSearch.Hit hit) {
            return "search.field." + hit.field().name().toLowerCase(Locale.ROOT);
        }
    }
}
