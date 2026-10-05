package com.personalkanban.application;

import com.personalkanban.application.port.SettingsStore;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * User-customizable colors for the card "quick flag" highlights
 * (Importante / Urgente / Importante y urgente) and for arbitrary
 * labels or label combinations. Every choice is stored separately
 * for the light and the dark theme, so a board stays readable in
 * both. Anything the user has not customized falls back to the
 * developer defaults (flags) or to the system colors (labels).
 *
 * <p>Storage layout (SettingsStore keys):
 * <ul>
 *   <li>{@code ui.priority.<state>.<theme>.bg|text} — flag highlight</li>
 *   <li>{@code ui.label.<key>.<theme>.bg|text} — label colors, where
 *       {@code <key>} is the URL-safe Base64 of the label name, or of
 *       the sorted labels joined by {@code } for a combination</li>
 * </ul>
 */
public final class StylePrefs {

    /** Quick-flag states a user can recolor. */
    public static final String IMPORTANT = "important";
    public static final String URGENT = "urgent";
    public static final String URGENT_IMPORTANT = "urgent-important";

    private static final String PRIORITY_PREFIX = "ui.priority.";
    private static final String LABEL_PREFIX = "ui.label.";

    /** Developer defaults: amber, orange, red — per theme. */
    private static final StyleColors DEFAULT_IMPORTANT_LIGHT =
            new StyleColors("#FDE68A", "#78350F");
    private static final StyleColors DEFAULT_IMPORTANT_DARK =
            new StyleColors("#78350F", "#FDE68A");
    private static final StyleColors DEFAULT_URGENT_LIGHT =
            new StyleColors("#FDBA74", "#7C2D12");
    private static final StyleColors DEFAULT_URGENT_DARK =
            new StyleColors("#7C2D12", "#FDBA74");
    private static final StyleColors DEFAULT_URGENT_IMPORTANT_LIGHT =
            new StyleColors("#F87171", "#7F1D1D");
    private static final StyleColors DEFAULT_URGENT_IMPORTANT_DARK =
            new StyleColors("#7F1D1D", "#FCA5A5");

    private final SettingsStore store;

    /** A background/text pair; either may be null (unset). */
    public record StyleColors(String background, String text) {
    }

    public StylePrefs(SettingsStore store) {
        this.store = store;
    }

    // ------------------------------------------------------------------
    // Quick-flag highlight colors
    // ------------------------------------------------------------------

    /** Effective highlight colors for a flag state and theme. */
    public StyleColors priority(String state, boolean dark) {
        StyleColors saved = read(PRIORITY_PREFIX + state + "." + theme(dark));
        if (saved != null) {
            return saved;
        }
        return defaults(state, dark);
    }

    /**
     * Persists one flag-state color pair for one theme. A null field
     * clears that field back to the developer default.
     */
    public void setPriority(String state, boolean dark, String background, String text) {
        String base = PRIORITY_PREFIX + state + "." + theme(dark);
        putOrClear(base + ".bg", background);
        putOrClear(base + ".text", text);
    }

    /** Removes every customization of one flag state (both themes). */
    public void clearPriority(String state) {
        for (boolean dark : new boolean[]{false, true}) {
            String base = PRIORITY_PREFIX + state + "." + theme(dark);
            store.put(base + ".bg", "");
            store.put(base + ".text", "");
        }
    }

    /** Developer defaults for a flag state and theme (no storage read). */
    public StyleColors defaultPriority(String state, boolean dark) {
        return defaults(state, dark);
    }

    private StyleColors defaults(String state, boolean dark) {
        if (URGENT.equals(state)) {
            return dark ? DEFAULT_URGENT_DARK : DEFAULT_URGENT_LIGHT;
        }
        if (URGENT_IMPORTANT.equals(state)) {
            return dark ? DEFAULT_URGENT_IMPORTANT_DARK : DEFAULT_URGENT_IMPORTANT_LIGHT;
        }
        return dark ? DEFAULT_IMPORTANT_DARK : DEFAULT_IMPORTANT_LIGHT;
    }

    // ------------------------------------------------------------------
    // Label / label-combination colors
    // ------------------------------------------------------------------

    /** Storage key of a single label. */
    public static String labelKey(String label) {
        return encode(label);
    }

    /**
     * Storage key of a label combination: the labels sorted and joined
     * with a separator that cannot appear in a label name, then encoded.
     * Two combinations match only when they hold exactly the same labels.
     */
    public static String combinationKey(List<String> labels) {
        return encode(String.join("", labels.stream().sorted().toList()));
    }

    private static String encode(String raw) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Saved colors of one label/combination key and theme, if any. */
    public Optional<StyleColors> labelColors(String key, boolean dark) {
        return Optional.ofNullable(read(LABEL_PREFIX + key + "." + theme(dark)));
    }

    /**
     * Persists one label/combination color pair for one theme. A null
     * field clears that field (the label falls back to the next rule).
     */
    public void setLabelColors(String key, boolean dark, String background, String text) {
        String base = LABEL_PREFIX + key + "." + theme(dark);
        putOrClear(base + ".bg", background);
        putOrClear(base + ".text", text);
    }

    /** Removes every customization of one label/combination key. */
    public void clearLabelColors(String key) {
        for (boolean dark : new boolean[]{false, true}) {
            String base = LABEL_PREFIX + key + "." + theme(dark);
            store.put(base + ".bg", "");
            store.put(base + ".text", "");
        }
    }

    /**
     * Resolves the effective colors of a card's label set: an exact
     * combination match wins, then the first single label (in the
     * card's own order) that carries a customization. Empty when
     * nothing is customized — the card keeps the system colors.
     */
    public Optional<StyleColors> resolveLabelColors(List<String> labels, boolean dark) {
        if (labels == null || labels.isEmpty()) {
            return Optional.empty();
        }
        Optional<StyleColors> combination =
                labelColors(combinationKey(labels), dark);
        if (combination.isPresent()) {
            return combination;
        }
        for (String label : labels) {
            Optional<StyleColors> single = labelColors(labelKey(label), dark);
            if (single.isPresent()) {
                return single;
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // Rule enumeration (preferences dialog lists what exists)
    // ------------------------------------------------------------------

    /**
     * Encoded keys of every label/combination rule that carries at
     * least one saved color, in stable (decoded) order. The UI
     * decodes them with {@link #describeRule} to show what each
     * rule matches.
     */
    public List<String> labelRuleKeys() {
        return store.keys(LABEL_PREFIX).stream()
                .map(key -> key.substring(LABEL_PREFIX.length()))
                .map(key -> RULE_SUFFIX.matcher(key).replaceFirst(""))
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Human-readable name of an encoded rule key: the label itself,
     * or the combination's labels joined with " + ".
     */
    public static String describeRule(String encodedKey) {
        String decoded = new String(
                Base64.getUrlDecoder().decode(encodedKey),
                java.nio.charset.StandardCharsets.UTF_8);
        return Arrays.stream(decoded.split("\u001f"))
                .collect(java.util.stream.Collectors.joining(" + "));
    }

    private static final java.util.regex.Pattern RULE_SUFFIX =
            java.util.regex.Pattern.compile("\\.(light|dark)\\.(bg|text)$");

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private StyleColors read(String key) {
        String background = store.get(key + ".bg").orElse("");
        String text = store.get(key + ".text").orElse("");
        if (background.isEmpty() && text.isEmpty()) {
            return null;
        }
        return new StyleColors(
                background.isEmpty() ? null : background,
                text.isEmpty() ? null : text);
    }

    private void putOrClear(String key, String value) {
        store.put(key, value == null ? "" : value);
    }

    private static String theme(boolean dark) {
        return dark ? "dark" : "light";
    }
}
