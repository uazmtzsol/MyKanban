package com.personalkanban.ui;

import com.personalkanban.application.BoardService;
import com.personalkanban.application.StylePrefs;
import com.personalkanban.application.port.InMemoryBoardRepository;
import com.personalkanban.application.port.InMemorySettingsStore;
import com.personalkanban.application.port.InMemoryUndoHistory;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User request T15 · B7: a label paints with the color the user chose in the
 * global label manager; with no rule it keeps the deterministic hash color.
 * B9 is covered by the translated palette names used in the chooser.
 */
class LabelChipStyleTest {

    @Test
    void withoutARuleTheHashPaletteClassIsUsed() {
        LabelChipStyle.Chip chip = LabelChipStyle.of("casa", key -> Optional.empty());

        assertThat(chip.styleClass()).isEqualTo(LabelChipStyle.hashClass("casa"));
        assertThat(chip.styleClass()).startsWith("chip-color-");
        assertThat(chip.inlineStyle()).isNull();
    }

    @Test
    void hashIsDeterministicAndInsideThePalette() {
        for (String label : new String[]{"casa", "Urgente", "work", "üñá"}) {
            assertThat(LabelChipStyle.hashClass(label)).isEqualTo(LabelChipStyle.hashClass(label));
            int slot = Integer.parseInt(LabelChipStyle.hashClass(label).substring("chip-color-".length()));
            assertThat(slot).isBetween(0, 7);
        }
    }

    @Test
    void withARuleTheChosenColorWins() {
        StylePrefs.StyleColors colors = new StylePrefs.StyleColors("#0f172a", "#ffffff");

        LabelChipStyle.Chip chip = LabelChipStyle.of("casa", key -> Optional.of(colors));

        assertThat(chip.styleClass()).isNull();
        assertThat(chip.inlineStyle())
                .contains("-fx-background-color: #0f172a;")
                .contains("-fx-text-fill: #ffffff;");
    }

    @Test
    void textContrastFollowsTheBackgroundLuminance() {
        assertThat(LabelChipStyle.contrastingText("#ffffff")).isEqualTo("#1f2937");
        assertThat(LabelChipStyle.contrastingText("#000000")).isEqualTo("#ffffff");
        assertThat(LabelChipStyle.contrastingText("not-a-color")).isEqualTo("#ffffff");
    }

    @Test
    void theManagerRuleIsWhatTheChipReadsBack() {
        InMemorySettingsStore settings = new InMemorySettingsStore();
        BoardService service = new BoardService(new InMemoryBoardRepository(),
                new InMemoryUndoHistory(), settings);
        String key = StylePrefs.labelKey("casa");

        // Writing the rule is exactly what the label manager does.
        service.setLabelStyle(key, false, "#123456", "#ffffff");
        service.setLabelStyle(key, true, "#123456", "#ffffff");
        LabelChipStyle.Chip painted =
                LabelChipStyle.of("casa", k -> service.labelStyle(k, false));
        assertThat(painted.inlineStyle()).contains("#123456");

        // "Automatic" clears it and the hash class comes back.
        service.clearLabelStyle(key);
        LabelChipStyle.Chip automatic =
                LabelChipStyle.of("casa", k -> service.labelStyle(k, false));
        assertThat(automatic.styleClass()).isEqualTo(LabelChipStyle.hashClass("casa"));
    }
}
