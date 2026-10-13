package com.personalkanban.ui;

import com.personalkanban.application.QuickCapture;
import com.personalkanban.domain.board.BoardColor;
import javafx.application.Platform;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Optional;

/**
 * Quick capture with inline syntax (user request T13 · B4). A single field with
 * the focus, a live preview of what the card will get, <b>Enter</b> to create
 * it and a <b>More options…</b> button that hands the parsed draft to the full
 * card dialog. The parsing itself lives in the pure {@link QuickCapture}.
 */
final class QuickCaptureDialog {

    /** What the user chose in the dialog. */
    enum Action {
        /** Create the card right away with the parsed draft. */
        CREATE,
        /** Open the full card dialog, prefilled with the parsed draft. */
        MORE
    }

    /** The dialog's answer: the chosen action plus the parsed draft. */
    record Result(Action action, QuickCapture.Draft draft) {
    }

    private static final DateTimeFormatter DUE_FORMAT =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

    private final I18n i18n;

    QuickCaptureDialog(I18n i18n) {
        this.i18n = i18n;
    }

    /**
     * The full-dialog form a draft turns into when the user picks «More
     * options…»: same title, labels and due date, everything else empty so the
     * user fills it in the familiar dialog.
     */
    static Dialogs.CardForm toCardForm(QuickCapture.Draft draft) {
        return new Dialogs.CardForm(draft.title(), "", BoardColor.DEFAULT,
                draft.dueDate(), draft.labels());
    }

    Optional<Result> showAndWait(LocalDate today) {
        LocalDate reference = today == null ? LocalDate.now() : today;
        Dialog<Result> dialog = new Dialog<>();
        dialog.setTitle(i18n.text("quick.title"));
        dialog.setHeaderText(i18n.text("quick.prompt"));

        ButtonType addType = new ButtonType(i18n.text("quick.add"), ButtonBar.ButtonData.OK_DONE);
        ButtonType moreType = new ButtonType(i18n.text("quick.more"), ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().setAll(addType, moreType, ButtonType.CANCEL);

        TextField field = new TextField();
        field.setPromptText(i18n.text("quick.prompt"));
        Label hint = new Label(i18n.text("quick.hint"));
        hint.setWrapText(true);
        hint.getStyleClass().add("quick-hint");
        Label preview = new Label();
        preview.setWrapText(true);
        preview.getStyleClass().add("quick-preview");

        var addButton = dialog.getDialogPane().lookupButton(addType);
        field.textProperty().addListener((obs, old, value) -> {
            QuickCapture.Draft draft = QuickCapture.parse(value, reference);
            preview.setText(previewText(draft, reference));
            addButton.setDisable(!draft.canCreate());
        });
        preview.setText(previewText(QuickCapture.parse("", reference), reference));
        addButton.setDisable(true);

        VBox box = new VBox(8, field, hint, preview);
        box.getStyleClass().add("quick-capture-box");
        dialog.getDialogPane().setContent(box);

        // The text field must own the focus, not the button bar.
        Platform.runLater(field::requestFocus);

        dialog.setResultConverter(button -> {
            QuickCapture.Draft draft = QuickCapture.parse(field.getText(), reference);
            if (button == addType) {
                return new Result(Action.CREATE, draft);
            }
            if (button == moreType) {
                return new Result(Action.MORE, draft);
            }
            return null;
        });
        return dialog.showAndWait();
    }

    /**
     * Human-readable preview of what the line will produce. Rendered in the UI
     * (not in {@link QuickCapture}) because it needs translations, and the
     * application layer must stay free of the ui package.
     */
    String previewText(QuickCapture.Draft draft, LocalDate today) {
        if (draft == null || !draft.canCreate()) {
            return i18n.text("quick.empty");
        }
        StringBuilder text = new StringBuilder(draft.title());
        if (!draft.labels().isEmpty()) {
            text.append('\n').append(i18n.text("quick.preview.labels")).append(": ")
                    .append(String.join(", ", draft.labels()));
        }
        if (draft.dueDate() != null) {
            text.append('\n').append(i18n.text("quick.preview.due")).append(": ")
                    .append(DUE_FORMAT.format(draft.dueDate()));
        }
        return text.toString();
    }
}
