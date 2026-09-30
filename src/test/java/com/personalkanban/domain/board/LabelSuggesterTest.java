package com.personalkanban.domain.board;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure autocomplete logic: token detection, prefix suggestions, apply. */
class LabelSuggesterTest {

    private final LabelSuggester suggester = new LabelSuggester(List.of(
            "pri", "primer", "primo", "principal", "Urgente", "Importante", "uaz"));

    @Test
    void userExampleFindsAllPriWords() {
        // The user's acceptance example: typing "pr" suggests all four.
        assertThat(suggester.suggestions("pr", List.of()))
                .containsExactly("pri", "primer", "primo", "principal");
    }

    @Test
    void prefixMatchIsCaseInsensitiveAndKeepsOriginalSpelling() {
        assertThat(suggester.suggestions("URG", List.of())).containsExactly("Urgente");
        assertThat(suggester.suggestions("imP", List.of())).containsExactly("Importante");
    }

    @Test
    void cardOwnLabelsAreExcluded() {
        // The card already carries "Urgente": it must not be suggested again.
        assertThat(suggester.suggestions("u", List.of("Urgente")))
                .containsExactly("uaz");
    }

    @Test
    void currentTokenIsolatesLastWordBeforeCaret() {
        assertThat(suggester.currentToken("et1 et2 pr", 10)).isEqualTo("pr");
        assertThat(suggester.currentToken("et1,et2 pr", 10)).isEqualTo("pr");
        assertThat(suggester.currentToken("et1,   et2  pr", 14)).isEqualTo("pr");
        // Caret inside a word returns the partial word.
        assertThat(suggester.currentToken("primer", 3)).isEqualTo("pri");
    }

    @Test
    void currentTokenEmptyAfterSeparatorOrWhenIdle() {
        assertThat(suggester.currentToken("et1 ", 4)).isEmpty();
        assertThat(suggester.currentToken("et1,", 4)).isEmpty();
        assertThat(suggester.currentToken("", 0)).isEmpty();
    }

    @Test
    void applyReplacesTokenAndAddsTrailingSpace() {
        assertThat(suggester.apply("et1 pr", 6, "principal"))
                .isEqualTo("et1 principal ");
        // With a following label, the suffix is preserved.
        assertThat(suggester.apply("et1 pr et3", 6, "principal"))
                .isEqualTo("et1 principal  et3");
    }

    @Test
    void applyWithEmptySuggestionReturnsTextUnchanged() {
        assertThat(suggester.apply("et1 pr", 6, " ")).isEqualTo("et1 pr");
        assertThat(suggester.apply(null, 0, "x")).isEmpty();
    }

    @Test
    void noMatchesYieldsEmptyList() {
        assertThat(suggester.suggestions("zzz", List.of())).isEmpty();
    }

    @Test
    void vocabularyIsDeduplicatedAndBlankFree() {
        LabelSuggester dirty = new LabelSuggester(List.of("uaz", " uaz ", "", "   "));
        assertThat(dirty.suggestions("u", List.of())).containsExactly("uaz");
    }
}
