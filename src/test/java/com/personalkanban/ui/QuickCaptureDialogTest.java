package com.personalkanban.ui;

import com.personalkanban.application.QuickCapture;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User request T13 · B4: the quick-capture dialog shows a live preview and its
 * «More options…» entry prefills the full card dialog. Neither needs the
 * dialog to be shown, so both helpers are checked directly.
 */
class QuickCaptureDialogTest {

    private final I18n i18n = new I18n(Locale.of("es"));
    private final QuickCaptureDialog dialog = new QuickCaptureDialog(i18n);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);

    @Test
    void emptyDraftShowsTheEmptyHint() {
        QuickCapture.Draft empty = QuickCapture.parse("", TODAY);

        assertThat(dialog.previewText(empty, TODAY)).isEqualTo(i18n.text("quick.empty"));
    }

    @Test
    void previewShowsTitleLabelsAndDueDate() {
        QuickCapture.Draft draft = QuickCapture.parse("Llamar al banco +casa ! @mañana", TODAY);

        String preview = dialog.previewText(draft, TODAY);

        assertThat(preview).contains("Llamar al banco");
        assertThat(preview).contains(i18n.text("quick.preview.labels") + ": casa, Urgente");
        assertThat(preview).contains(i18n.text("quick.preview.due"));
    }

    @Test
    void previewOfATitleOnlyDraftShowsJustTheTitle() {
        QuickCapture.Draft draft = QuickCapture.parse("Solo título", TODAY);

        assertThat(dialog.previewText(draft, TODAY)).isEqualTo("Solo título");
    }

    @Test
    void moreOptionsCarriesTheParsedDraftIntoTheFullForm() {
        QuickCapture.Draft draft = QuickCapture.parse("Pagar agua ! @+2d +hogar", TODAY);

        Dialogs.CardForm form = QuickCaptureDialog.toCardForm(draft);

        assertThat(form.title()).isEqualTo("Pagar agua");
        assertThat(form.description()).isEmpty();
        assertThat(form.dueDate()).isEqualTo(TODAY.plusDays(2));
        assertThat(form.labels()).containsExactly("Urgente", "hogar");
    }
}
