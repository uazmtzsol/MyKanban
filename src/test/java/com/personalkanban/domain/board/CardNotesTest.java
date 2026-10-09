package com.personalkanban.domain.board;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Parsing/serialization of the multi-note text stored on a card. */
class CardNotesTest {

    @Test
    void blankTextHasNoNotes() {
        assertThat(CardNotes.parse(null).isEmpty()).isTrue();
        assertThat(CardNotes.parse("").isEmpty()).isTrue();
        assertThat(CardNotes.parse("   \n  ").isEmpty()).isTrue();
    }

    @Test
    void legacyPlainTextBecomesOneUntitledNote() {
        CardNotes notes = CardNotes.parse("Comprar leche\ny llamar al fontanero");

        assertThat(notes.size()).isEqualTo(1);
        assertThat(notes.notes().get(0).title()).isEmpty();
        assertThat(notes.notes().get(0).body()).isEqualTo("Comprar leche\ny llamar al fontanero");
    }

    @Test
    void titledNotesAreSplitByHeading() {
        CardNotes notes = CardNotes.parse("""
                ## Compras

                Leche, pan

                ## Recordatorios

                Llamar al fontanero
                """);

        assertThat(notes.size()).isEqualTo(2);
        assertThat(notes.notes().get(0).title()).isEqualTo("Compras");
        assertThat(notes.notes().get(0).body()).isEqualTo("Leche, pan");
        assertThat(notes.notes().get(1).title()).isEqualTo("Recordatorios");
        assertThat(notes.notes().get(1).body()).isEqualTo("Llamar al fontanero");
    }

    @Test
    void titleOnlyNoteIsKept() {
        CardNotes notes = CardNotes.parse("## Pendiente");

        assertThat(notes.size()).isEqualTo(1);
        assertThat(notes.notes().get(0).title()).isEqualTo("Pendiente");
        assertThat(notes.notes().get(0).body()).isEmpty();
    }

    @Test
    void bodyWithBlankLinesAndMarkdownIsPreserved() {
        CardNotes notes = CardNotes.parse("""
                ## Ideas

                - punto uno

                - punto dos

                *cursiva*
                """);

        assertThat(notes.notes().get(0).body())
                .isEqualTo("- punto uno\n\n- punto dos\n\n*cursiva*");
    }

    @Test
    void serializeRoundTrips() {
        CardNotes original = CardNotes.empty()
                .withNote("Compras", "Leche, pan")
                .withNote("Pendiente", "");

        CardNotes reparsed = CardNotes.parse(original.serialize());

        assertThat(reparsed.notes()).isEqualTo(original.notes());
    }

    @Test
    void serializeOfLegacyNoteUsesHeadingSoItRoundTrips() {
        CardNotes legacy = CardNotes.parse("Nota antigua\ncon dos líneas");

        String serialized = legacy.serialize();
        CardNotes reparsed = CardNotes.parse(serialized);

        assertThat(serialized).startsWith("## ");
        assertThat(reparsed.notes()).isEqualTo(legacy.notes());
        assertThat(reparsed.notes().get(0).body()).isEqualTo("Nota antigua\ncon dos líneas");
    }

    @Test
    void operationsAddReplaceAndRemoveById() {
        CardNotes notes = CardNotes.empty()
                .withNote("A", "uno")
                .withNote("B", "dos");

        assertThat(notes.replaced(1, "B2", "dos bis").notes().get(1))
                .isEqualTo(new CardNotes.Note("B2", "dos bis"));
        assertThat(notes.removed(0).notes())
                .containsExactly(new CardNotes.Note("B", "dos"));
        assertThat(notes.removed(9)).isSameAs(notes);
        assertThat(notes.replaced(-1, "x", "y")).isSameAs(notes);
    }

    @Test
    void blankOperationsAreIgnored() {
        CardNotes notes = CardNotes.parse("## A\n\nuno");

        assertThat(notes.withNote("  ", "  ")).isSameAs(notes);
        // Blanking the only note removes it instead of storing an empty text.
        assertThat(notes.replaced(0, "", "").isEmpty()).isTrue();
        assertThat(CardNotes.empty().serialize()).isEmpty();
    }

    @Test
    void textAroundHeadingsIsKeptAsTheFirstNote() {
        CardNotes notes = CardNotes.parse("""
                Texto suelto

                ## Con título

                cuerpo
                """);

        assertThat(notes.size()).isEqualTo(2);
        assertThat(notes.notes().get(0).title()).isEmpty();
        assertThat(notes.notes().get(0).body()).isEqualTo("Texto suelto");
    }
}
