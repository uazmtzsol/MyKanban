package com.personalkanban.domain.board;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WipLimitTest {

    @Test
    void rejectsZeroAndNegativeValues() {
        assertThatThrownBy(() -> WipLimit.of(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WipLimit.of(-3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unlimitedIsRepresentedAsEmpty() {
        assertThat(WipLimit.unlimited().isUnlimited()).isTrue();
        assertThat(WipLimit.unlimited().asOptional()).isEmpty();
    }

    @Test
    void exceedingIsCountBased() {
        WipLimit limit = WipLimit.of(2);
        assertThat(limit.isExceededBy(2)).isFalse();
        assertThat(limit.isExceededBy(3)).isTrue();
    }

    @Test
    void describeShowsCountOverLimit() {
        assertThat(WipLimit.of(5).describe(3)).isEqualTo("3/5");
        assertThat(WipLimit.unlimited().describe(3)).isEqualTo("3");
    }
}
