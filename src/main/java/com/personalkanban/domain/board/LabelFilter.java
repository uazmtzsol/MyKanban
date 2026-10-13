package com.personalkanban.domain.board;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Label-based card filter (Information Expert on the matching rule).
 * <ul>
 *   <li>{@link Mode#ALL}: a card must carry <em>every</em> requested label (AND).</li>
 *   <li>{@link Mode#ANY}: a card must carry <em>at least one</em> requested label (OR).</li>
 *   <li>{@link Mode#EXCLUDE}: a card must carry <em>none</em> of the requested
 *       labels (NOT). With an <strong>empty</strong> list it means "cards with
 *       no labels at all" — see {@link #withoutLabels()}.</li>
 *   <li>An empty label list in {@link Mode#ALL}/{@link Mode#ANY} matches every
 *       card — "no filter".</li>
 * </ul>
 * Matching is case-insensitive; the filter never mutates cards.
 */
public record LabelFilter(List<String> labels, Mode mode) {

    public enum Mode { ALL, ANY, EXCLUDE }

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

    /**
     * The "cards with no labels at all" filter (user request, 2026-10-13):
     * an excluding filter with an empty label list.
     */
    public static LabelFilter withoutLabels() {
        return new LabelFilter(List.of(), Mode.EXCLUDE);
    }

    /** True only for the "no filter" case; {@link #withoutLabels()} is a filter. */
    public boolean isEmpty() {
        return mode != Mode.EXCLUDE && labels.isEmpty();
    }

    public boolean matches(Card card) {
        if (isEmpty()) {
            return true;
        }
        var cardLabels = card.labels().stream()
                .map(label -> label.strip().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        return switch (mode) {
            case ALL -> cardLabels.containsAll(labels);
            case ANY -> labels.stream().anyMatch(cardLabels::contains);
            // An unlabeled card carries none of them; an empty list asks for
            // exactly those unlabeled cards.
            case EXCLUDE -> labels.isEmpty()
                    ? cardLabels.isEmpty()
                    : labels.stream().noneMatch(cardLabels::contains);
        };
    }
}
