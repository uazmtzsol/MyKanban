package com.personalkanban.domain.board;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardColorTest {

    @Test
    void paletteHasEightNamedPresets() {
        assertThat(BoardColor.palette()).hasSize(8);
        assertThat(BoardColor.palette()).allSatisfy(color -> {
            assertThat(color.name()).isNotBlank();
            assertThat(color.isCustom()).isFalse();
        });
    }

    @Test
    void hexParsingResolvesPresetsByAppearance() {
        assertThat(BoardColor.fromHex("#2196f3")).isEqualTo(BoardColor.BLUE);
        assertThat(BoardColor.fromHex("#2196F3")).isEqualTo(BoardColor.BLUE);
    }

    @Test
    void unknownHexBecomesCustomColor() {
        BoardColor custom = BoardColor.fromHex("#123abc");
        assertThat(custom.isCustom()).isTrue();
        assertThat(custom.displayName()).isEqualTo("#123abc");
    }

    @Test
    void legacyNamesStillParse() {
        assertThat(BoardColor.fromStored("pink")).isEqualTo(BoardColor.PINK);
        assertThat(BoardColor.fromStored("PINK")).isEqualTo(BoardColor.PINK);
    }

    @Test
    void storedValuesAcceptHexAndNames() {
        assertThat(BoardColor.fromStored("#ff0000").hex()).isEqualTo("#ff0000");
        assertThat(BoardColor.fromStored("teal")).isEqualTo(BoardColor.TEAL);
    }

    @Test
    void rejectsMalformedHex() {
        assertThatThrownBy(() -> BoardColor.fromHex("red"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BoardColor.fromHex("#12345"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
