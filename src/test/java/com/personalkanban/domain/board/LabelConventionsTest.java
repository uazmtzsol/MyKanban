package com.personalkanban.domain.board;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Rules for user-entered labels: no leading '#', simple names only. */
class LabelConventionsTest {

    @Test
    void rejectsLabelsStartingWithHash() {
        assertThat(LabelConventions.isValid("#compras")).isFalse();
        assertThat(LabelConventions.startsWithHash("#compras")).isTrue();
        assertThat(LabelConventions.isValid("compras")).isTrue();
    }

    @Test
    void rejectsBlankAndControlCharacters() {
        assertThat(LabelConventions.isValid(null)).isFalse();
        assertThat(LabelConventions.isValid("   ")).isFalse();
        assertThat(LabelConventions.isValid("a,b")).isFalse();
        assertThat(LabelConventions.isValid("a;b")).isFalse();
        assertThat(LabelConventions.isValid("a#b")).isFalse();
    }

    @Test
    void splitsOnSpacesCommasAndSemicolons() {
        assertThat(LabelConventions.split("et1 et2 et3")).containsExactly("et1", "et2", "et3");
        assertThat(LabelConventions.split("et1,et2;et3")).containsExactly("et1", "et2", "et3");
        assertThat(LabelConventions.split("  et1,   et2  et3 ")).containsExactly("et1", "et2", "et3");
        assertThat(LabelConventions.split("#compras")).isEmpty();
        assertThat(LabelConventions.split(null)).isEmpty();
    }
}
