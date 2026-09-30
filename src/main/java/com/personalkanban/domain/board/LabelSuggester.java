package com.personalkanban.domain.board;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure logic behind the label autocomplete (no JavaFX — fully unit-testable).
 * The editor field holds several labels at once, so suggestions match only
 * the token being typed (after the last space/comma), matching by prefix,
 * case-insensitively. Already-present labels of the same card are excluded,
 * and the typed token always wins over the suggestion list (the user may be
 * coining a brand-new label).
 */
public final class LabelSuggester {

    /** Characters that separate labels inside the editor field. */
    private static final String SEPARATORS = "[,\\s\uFF0C]+";

    private final Set<String> vocabulary;

    public LabelSuggester(Iterable<String> vocabulary) {
        this.vocabulary = new LinkedHashSet<>();
        if (vocabulary != null) {
            for (String label : vocabulary) {
                if (label != null && !label.isBlank()) {
                    this.vocabulary.add(label.strip());
                }
            }
        }
    }

    /**
     * The token currently being typed in {@code text} at caret position
     * {@code caret} (empty when the caret sits on a separator or at the end
     * of a completed word).
     */
    public String currentToken(String text, int caret) {
        if (text == null || caret < 0 || caret > text.length()) {
            return "";
        }
        String prefix = text.substring(0, caret);
        String[] parts = prefix.split(SEPARATORS, -1);
        return parts.length == 0 ? "" : parts[parts.length - 1].stripLeading();
    }

    /**
     * Suggestions for the token being typed: prefix match,
     * case-insensitive, original spelling preserved, card's own labels
     * excluded (they are already on it), typed token itself excluded.
     */
    public List<String> suggestions(String token, Iterable<String> exclude) {
        String query = token.toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return List.of();
        }
        Set<String> present = new LinkedHashSet<>();
        if (exclude != null) {
            for (String label : exclude) {
                if (label != null) {
                    present.add(label.strip().toLowerCase(Locale.ROOT));
                }
            }
        }
        present.add(query);
        List<String> matches = new ArrayList<>();
        for (String candidate : vocabulary) {
            String lowered = candidate.toLowerCase(Locale.ROOT);
            if (lowered.startsWith(query) && !present.contains(lowered)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    /**
     * The full field text after applying {@code suggestion} in place of the
     * token at the caret, followed by a space — ready to keep typing.
     */
    public String apply(String text, int caret, String suggestion) {
        if (text == null || suggestion == null || suggestion.isBlank()) {
            return text == null ? "" : text;
        }
        int safeCaret = Math.clamp(caret, 0, text.length());
        String prefix = text.substring(0, safeCaret);
        String suffix = text.substring(safeCaret);
        String[] parts = prefix.split(SEPARATORS, -1);
        int tokenStart = parts.length == 0 ? 0 : safeCaret - parts[parts.length - 1].length();
        return prefix.substring(0, tokenStart) + suggestion.strip() + " " + suffix;
    }
}
