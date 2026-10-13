package com.personalkanban.application;

import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User request T14 · B5.1: user-defined board templates survive a JSON round
 * trip with their columns, process names and card-view mode, and a corrupt
 * entry never breaks the Templates menu.
 */
class BoardTemplateTest {

    private static BoardTemplate sample() {
        return new BoardTemplate("Sprint", List.of(
                new BoardTemplate.TemplateColumn("Ideas", "not yet", BoardColor.BLUE,
                        WipLimit.unlimited(), false),
                new BoardTemplate.TemplateColumn("Doing", "", BoardColor.ORANGE,
                        WipLimit.of(3), false),
                new BoardTemplate.TemplateColumn("Hecho", "done things", BoardColor.GREEN,
                        WipLimit.unlimited(), true)),
                List.of("Desarrollo", "Revisión"),
                CardViewSettings.Mode.FULL);
    }

    @Test
    void jsonRoundTripKeepsEverything() {
        BoardTemplate restored = BoardTemplate.fromJson(sample().toJson()).orElseThrow();

        assertThat(restored.name()).isEqualTo("Sprint");
        assertThat(restored.processNames()).containsExactly("Desarrollo", "Revisión");
        assertThat(restored.viewMode()).isEqualTo(CardViewSettings.Mode.FULL);
        assertThat(restored.columns()).hasSize(3);
        assertThat(restored.columns().get(0).title()).isEqualTo("Ideas");
        assertThat(restored.columns().get(0).color()).isEqualTo(BoardColor.BLUE);
        assertThat(restored.columns().get(1).wipLimit()).isEqualTo(WipLimit.of(3));
        assertThat(restored.columns().get(2).done()).isTrue();
        assertThat(restored.columns().get(2).description()).isEqualTo("done things");
    }

    @Test
    void templatesNeverCarryCards() {
        // The JSON has no card section at all: applying a template starts empty.
        assertThat(sample().toJson()).doesNotContain("cards");
    }

    @Test
    void corruptJsonYieldsNothingInsteadOfThrowing() {
        assertThat(BoardTemplate.fromJson("{not json")).isEmpty();
        assertThat(BoardTemplate.fromJson("")).isEmpty();
        assertThat(BoardTemplate.fromJson(null)).isEmpty();
    }

    @Test
    void unknownColorAndModeFallBackToDefaults() {
        String json = "{\"name\":\"x\",\"columns\":[{\"title\":\"a\",\"color\":\"NEON\"}],"
                + "\"viewMode\":\"NOPE\"}";

        BoardTemplate restored = BoardTemplate.fromJson(json).orElseThrow();

        assertThat(restored.columns().get(0).color()).isEqualTo(BoardColor.DEFAULT);
        assertThat(restored.columns().get(0).wipLimit()).isEqualTo(WipLimit.unlimited());
        assertThat(restored.viewMode()).isEqualTo(CardViewSettings.DEFAULT_MODE);
    }
}
