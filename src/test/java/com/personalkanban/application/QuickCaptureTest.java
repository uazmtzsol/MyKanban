package com.personalkanban.application;

import com.personalkanban.domain.board.Card;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User request T13 · B4: quick capture with inline syntax. Parsing is pure, so
 * every rule of the table in PLAN.md §7.2 is checked here without any UI.
 */
class QuickCaptureTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);

    @Test
    void plainTextBecomesTheTitle() {
        QuickCapture.Draft draft = QuickCapture.parse("Llamar al banco", TODAY);

        assertThat(draft.title()).isEqualTo("Llamar al banco");
        assertThat(draft.labels()).isEmpty();
        assertThat(draft.dueDate()).isNull();
        assertThat(draft.canCreate()).isTrue();
    }

    @Test
    void plusTokensBecomeLabelsInOrder() {
        QuickCapture.Draft draft = QuickCapture.parse("Comprar leche +casa +super", TODAY);

        assertThat(draft.title()).isEqualTo("Comprar leche");
        assertThat(draft.labels()).containsExactly("casa", "super");
    }

    @Test
    void bangAndStarAreTheQuickFlags() {
        QuickCapture.Draft draft = QuickCapture.parse("Entregar informe ! *", TODAY);

        assertThat(draft.title()).isEqualTo("Entregar informe");
        assertThat(draft.labels()).containsExactly(Card.LABEL_URGENT, Card.LABEL_IMPORTANT);
    }

    @Test
    void plusUrgenteAndPlusImportanteMapOntoTheSystemLabels() {
        QuickCapture.Draft draft = QuickCapture.parse("Pagar +urgente +importante", TODAY);

        assertThat(draft.labels()).containsExactly(Card.LABEL_URGENT, Card.LABEL_IMPORTANT);
    }

    @Test
    void duplicateLabelsAreCollapsedCaseInsensitively() {
        QuickCapture.Draft draft = QuickCapture.parse("X ! +urgente +URGENTE +Urgente", TODAY);

        assertThat(draft.labels()).containsExactly(Card.LABEL_URGENT);
    }

    @Test
    void relativeDatesAreResolved() {
        assertThat(QuickCapture.parse("A @hoy", TODAY).dueDate()).isEqualTo(TODAY);
        assertThat(QuickCapture.parse("A @mañana", TODAY).dueDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(QuickCapture.parse("A @+3d", TODAY).dueDate()).isEqualTo(TODAY.plusDays(3));
        assertThat(QuickCapture.parse("A @2026-10-20", TODAY).dueDate())
                .isEqualTo(LocalDate.of(2026, 10, 20));
    }

    @Test
    void invalidDateStaysInTheTitle() {
        QuickCapture.Draft draft = QuickCapture.parse("Reunión @32feb", TODAY);

        assertThat(draft.title()).isEqualTo("Reunión @32feb");
        assertThat(draft.dueDate()).isNull();
    }

    @Test
    void signsInsideWordsAreNeverInterpreted() {
        QuickCapture.Draft draft = QuickCapture.parse("Aprender C++ y a*b", TODAY);

        assertThat(draft.title()).isEqualTo("Aprender C++ y a*b");
        assertThat(draft.labels()).isEmpty();
    }

    @Test
    void aLonePlusOrAtIsPartOfTheTitle() {
        assertThat(QuickCapture.parse("Total + 5 @ nada", TODAY).title())
                .isEqualTo("Total + 5 @ nada");
    }

    @Test
    void emptyLineHasNoTitle() {
        QuickCapture.Draft draft = QuickCapture.parse("   ", TODAY);

        assertThat(draft.title()).isEmpty();
        assertThat(draft.canCreate()).isFalse();
    }

    @Test
    void fullExampleFromTheAcceptanceCriteria() {
        QuickCapture.Draft draft = QuickCapture.parse("Llamar al banco +casa ! @mañana", TODAY);

        assertThat(draft.title()).isEqualTo("Llamar al banco");
        assertThat(draft.labels()).containsExactly("casa", Card.LABEL_URGENT);
        assertThat(draft.dueDate()).isEqualTo(TODAY.plusDays(1));
    }

    @Test
    void labelsAreCappedAtTheDomainLimit() {
        QuickCapture.Draft draft = QuickCapture.parse(
                "X +a +b +c +d +e +f +g +h +i +j", TODAY);

        assertThat(draft.labels()).hasSize(QuickCapture.MAX_LABELS);
    }

    @Test
    void straySpacesAreCollapsedInTheTitle() {
        QuickCapture.Draft draft = QuickCapture.parse("   Uno    dos   +etiqueta   ", TODAY);

        assertThat(draft.title()).isEqualTo("Uno dos");
    }
}
