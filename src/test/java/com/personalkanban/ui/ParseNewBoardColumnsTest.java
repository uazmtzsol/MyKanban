package com.personalkanban.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure parsing tests for the new-board column seeding: a plain number yields
 * that many default-named columns, otherwise comma-separated titles. Matches
 * the user's acceptance examples.
 */
class ParseNewBoardColumnsTest {

    private static final java.util.function.IntFunction<String> ES =
            index -> "Columna " + index;

    @Test
    void plainNumberYieldsDefaultNamedColumns() {
        assertThat(Dialogs.parseNewBoardColumns("4", ES))
                .containsExactly("Columna 1", "Columna 2", "Columna 3", "Columna 4");
    }

    @Test
    void numberIgnoresSurroundingSpaces() {
        assertThat(Dialogs.parseNewBoardColumns("  3  ", ES))
                .containsExactly("Columna 1", "Columna 2", "Columna 3");
    }

    @Test
    void commaNamesYieldThoseColumns() {
        assertThat(Dialogs.parseNewBoardColumns("Por hacer, Haciendo, Hecho", ES))
                .containsExactly("Por hacer", "Haciendo", "Hecho");
    }

    @Test
    void commaNamesStripWhitespaceAndDropEmpties() {
        assertThat(Dialogs.parseNewBoardColumns(" Por hacer , , Haciendo, Hecho ", ES))
                .containsExactly("Por hacer", "Haciendo", "Hecho");
    }

    @Test
    void numberOutOfBoundsSignalsInvalid() {
        assertThat(Dialogs.parseNewBoardColumns("0", ES)).isNull();
        assertThat(Dialogs.parseNewBoardColumns("13", ES)).isNull();
        assertThat(Dialogs.parseNewBoardColumns("999", ES)).isNull();
    }

    @Test
    void blankYieldsNoColumns() {
        assertThat(Dialogs.parseNewBoardColumns(null, ES)).isEmpty();
        assertThat(Dialogs.parseNewBoardColumns("", ES)).isEmpty();
        assertThat(Dialogs.parseNewBoardColumns("   ", ES)).isEmpty();
    }

    @Test
    void defaultNameFunctionIsUsedForNumberedColumns() {
        assertThat(Dialogs.parseNewBoardColumns("2", index -> "Col #" + index))
                .containsExactly("Col #1", "Col #2");
    }
}
