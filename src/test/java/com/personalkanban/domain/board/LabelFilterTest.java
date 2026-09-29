package com.personalkanban.domain.board;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LabelFilterTest {

    private Card cardWith(String... labels) {
        return Card.restore(new CardId("c1"), new ColumnId("col1"),
                "title", "", BoardColor.BLUE, null, List.of(labels), Instant.now());
    }

    @Test
    void emptyFilterMatchesEverything() {
        LabelFilter filter = LabelFilter.none();
        assertThat(filter.matches(cardWith())).isTrue();
        assertThat(filter.matches(cardWith("work"))).isTrue();
        assertThat(filter.isEmpty()).isTrue();
    }

    @Test
    void andModeRequiresEveryLabel() {
        LabelFilter filter = new LabelFilter(List.of("work", "urgent"), LabelFilter.Mode.ALL);
        assertThat(filter.matches(cardWith("work", "urgent"))).isTrue();
        assertThat(filter.matches(cardWith("work", "urgent", "home"))).isTrue();
        assertThat(filter.matches(cardWith("work"))).isFalse();
        assertThat(filter.matches(cardWith("urgent"))).isFalse();
        assertThat(filter.matches(cardWith())).isFalse();
    }

    @Test
    void orModeRequiresAtLeastOneLabel() {
        LabelFilter filter = new LabelFilter(List.of("work", "urgent"), LabelFilter.Mode.ANY);
        assertThat(filter.matches(cardWith("work"))).isTrue();
        assertThat(filter.matches(cardWith("urgent", "home"))).isTrue();
        assertThat(filter.matches(cardWith("home"))).isFalse();
        assertThat(filter.matches(cardWith())).isFalse();
    }

    @Test
    void matchingIsCaseInsensitiveAndWhitespaceTolerant() {
        LabelFilter filter = new LabelFilter(List.of("  WORK ", "Urgent"), LabelFilter.Mode.ALL);
        assertThat(filter.matches(cardWith("work", "urgent"))).isTrue();
        assertThat(filter.labels()).containsExactly("work", "urgent");
    }

    @Test
    void filterNormalizesDuplicatesAndBlanks() {
        LabelFilter filter = new LabelFilter(
                java.util.Arrays.asList("Work", "", "work", null), LabelFilter.Mode.ANY);
        assertThat(filter.labels()).containsExactly("work");
    }
}
