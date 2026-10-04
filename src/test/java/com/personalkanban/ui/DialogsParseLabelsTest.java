package com.personalkanban.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure parsing tests for the P1 label rules: separators are optional and
 * mixable (spaces or commas), matching the user's acceptance examples.
 * No JavaFX involved — {@link Dialogs#parseLabels} is static and UI-free.
 */
class DialogsParseLabelsTest {

    @Test
    void spacesOnlyYieldSeparateLabels() {
        assertThat(Dialogs.parseLabels("et1 et2 et3"))
                .containsExactly("et1", "et2", "et3");
    }

    @Test
    void commasOnlyYieldSeparateLabels() {
        assertThat(Dialogs.parseLabels("et1,et2,et3"))
                .containsExactly("et1", "et2", "et3");
    }

    @Test
    void mixedSeparatorsAndExtraSpacesYieldSeparateLabels() {
        assertThat(Dialogs.parseLabels("et1,   et2  et3"))
                .containsExactly("et1", "et2", "et3");
    }

    @Test
    void blanksAndTrailingSeparatorsAreDropped() {
        assertThat(Dialogs.parseLabels("  et1 , , et2   "))
                .containsExactly("et1", "et2");
    }

    @Test
    void hashPrefixedLabelsAreRejectedOnManualEntry() {
        assertThat(Dialogs.parseLabels("#compras"))
                .as("process labels start with '#' and must not be typed by hand")
                .isEmpty();
        assertThat(Dialogs.parseLabels("et1 #compras et2"))
                .containsExactly("et1", "et2");
    }

    @Test
    void blankInputYieldsNoLabels() {
        assertThat(Dialogs.parseLabels(null)).isEmpty();
        assertThat(Dialogs.parseLabels("")).isEmpty();
        assertThat(Dialogs.parseLabels("   ")).isEmpty();
        assertThat(Dialogs.parseLabels(" , , ")).isEmpty();
    }
}
