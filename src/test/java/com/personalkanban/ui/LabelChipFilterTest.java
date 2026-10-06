package com.personalkanban.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Behavior of clicking a label chip (the pure half of B6). */
class LabelChipFilterTest {

    @Test
    void clickingALabelFiltersByIt() {
        assertThat(LabelChipFilter.toggle("", "work")).isEqualTo("work");
        assertThat(LabelChipFilter.toggle(null, "work")).isEqualTo("work");
        assertThat(LabelChipFilter.toggle("other", "work")).isEqualTo("work");
    }

    @Test
    void clickingTheSameLabelAgainClearsTheFilter() {
        assertThat(LabelChipFilter.toggle("work", "work")).isEmpty();
    }

    @Test
    void theToggleIgnoresCaseAndSurroundingSpaces() {
        assertThat(LabelChipFilter.toggle("Work", "work")).isEmpty();
        assertThat(LabelChipFilter.toggle("  work ", "work")).isEmpty();
        assertThat(LabelChipFilter.toggle("work", "  WORK ")).isEmpty();
    }

    @Test
    void clickingALabelReplacesAnExistingMultiLabelFilter() {
        assertThat(LabelChipFilter.toggle("work, urgent", "urgent")).isEqualTo("urgent");
    }

    @Test
    void clickingABlankChipLeavesTheFilterUnchanged() {
        assertThat(LabelChipFilter.toggle("work", null)).isEqualTo("work");
        assertThat(LabelChipFilter.toggle("work", "   ")).isEqualTo("work");
        assertThat(LabelChipFilter.toggle(null, null)).isEmpty();
    }

    @Test
    void theReturnedLabelIsTrimmed() {
        assertThat(LabelChipFilter.toggle("", "  work  ")).isEqualTo("work");
    }
}
