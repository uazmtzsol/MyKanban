package com.personalkanban.domain.board;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Label-based card filter (Information Expert on the matching rule).
 * <ul>
 *   <li>{@link Mode#ALL}: a card must carry <em>every</em> requested label (AND).</li>
 *   <li>{@link Mode#ANY}: a card must carry <em>at least one</em> requested label (OR).</li>
 *   <li>An empty label list matches every card — "no filter".</li>
 * </ul>
 * Matching is case-insensitive; the filter never mutates cards.
 */
public record LabelFilter(List<String> labels, Mode mode) {

    public enum Mode { ALL, ANY }

    public LabelFilter {
        Objects.requireNonNull(mode, "mode");
        labels = labels == null ? List.of()
                : labels.stream()
                        .filter(Objects::nonNull)
                        .map(label -> label.strip().toLowerCase(Locale.ROOT))
                        .filter(label -> !label.isEmpty())
                        .distinct()
                        .toList();
    }

    /** A filter that matches everything. */
    public static LabelFilter none() {
        return new LabelFilter(List.of(), Mode.ANY);
    }

    public boolean isEmpty() {
        return labels.isEmpty();
    }

    public boolean matches(Card card) {
        if (isEmpty()) {
            return true;
        }
        var cardLabels = card.labels().stream()
                .map(label -> label.strip().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        return mode == Mode.ALL
                ? cardLabels.containsAll(labels)
                : labels.stream().anyMatch(cardLabels::contains);
    }
}
