package com.personalkanban.application;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Global keyboard shortcuts a user can customize.
 *
 * <p>Representation is intentionally similar to the existing preference
 * classes in this package (pure model, JSON round-trip, no JavaFX/JDBC).
 * Storage key lives in {@code SettingsStore} as {@code ui.shortcuts}.
 */
public final class GlobalShortcuts {

    public record ShortcutSetting(String action, String keyCombination) {
        @JsonCreator
        public ShortcutSetting(@JsonProperty("action") String action,
                               @JsonProperty("keyCombination") String keyCombination) {
            this.action = Objects.requireNonNull(action);
            this.keyCombination = keyCombination == null ? "" : keyCombination;
        }

        public boolean isEmpty() {
            return keyCombination == null || keyCombination.isBlank();
        }
    }

    public enum Action {
        NEXT_CARD("next"),
        PREV_CARD("prev"),
        EDIT_CARD("edit"),
        EXIT("exit"),
        LABEL_FILTER("filter"),
        PROCESS_FILTER("process");

        final String storageName;

        Action(String storageName) {
            this.storageName = storageName;
        }

        public static Action fromStorageNameOrThrow(String name) {
            for (Action a : values()) {
                if (a.storageName.equals(name)) {
                    return a;
                }
            }
            throw new IllegalArgumentException("Unknown shortcut action: " + name);
        }

        /** Accepts any action name present in the persisted JSON, even if unknown now. */
        public static boolean isKnownActionName(String name) {
            for (Action a : values()) {
                if (a.storageName.equals(name)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final String DEFAULT_NEXT = "Right";
    private static final String DEFAULT_PREV = "Left";
    private static final String DEFAULT_EDIT = "Enter";
    private static final String DEFAULT_EXIT = "Escape";
    private static final String DEFAULT_LABEL_FILTER = "Ctrl+F";
    private static final String DEFAULT_PROCESS_FILTER = "Ctrl+P";

    private static final List<ShortcutSetting> DEFAULTS = List.of(
            new ShortcutSetting(Action.NEXT_CARD.name(), DEFAULT_NEXT),
            new ShortcutSetting(Action.PREV_CARD.name(), DEFAULT_PREV),
            new ShortcutSetting(Action.EDIT_CARD.name(), DEFAULT_EDIT),
            new ShortcutSetting(Action.EXIT.name(), DEFAULT_EXIT),
            new ShortcutSetting(Action.LABEL_FILTER.name(), DEFAULT_LABEL_FILTER),
            new ShortcutSetting(Action.PROCESS_FILTER.name(), DEFAULT_PROCESS_FILTER)
    );

    private List<ShortcutSetting> settings;

    public GlobalShortcuts(List<ShortcutSetting> settings) {
        this.settings = List.copyOf(Objects.requireNonNull(settings));
    }

    /** Builds the default shortcuts (the most common, usual values). */
    public GlobalShortcuts() {
        this.settings = DEFAULTS;
    }

    public List<ShortcutSetting> settings() {
        return settings;
    }

    public String get(Action action) {
        return settings.stream()
                .filter(s -> s.action().equals(action.name()))
                .findFirst()
                .map(ShortcutSetting::keyCombination)
                .orElse("");
    }

    public void set(Action action, String keyCombination) {
        String normalized = normalize(keyCombination);
        Map<String, ShortcutSetting> byAction = settings.stream()
                .collect(Collectors.toMap(ShortcutSetting::action, s -> s, (a, b) -> b, LinkedHashMap::new));
        byAction.put(action.name(), new ShortcutSetting(action.name(), normalized));
        this.settings = List.copyOf(byAction.values());
    }

    /**
     * Returns the localized display label for a storage action name.
     * Localized strings live in the i18n bundles; this only maps to the
     * storage key, never to the visible string.
     */
    public String storageLabelKey(Action action) {
        return switch (action) {
            case NEXT_CARD -> "shortcut.next.card";
            case PREV_CARD -> "shortcut.prev.card";
            case EDIT_CARD -> "shortcut.edit.card";
            case EXIT -> "shortcut.exit";
            case LABEL_FILTER -> "shortcut.filter.labels";
            case PROCESS_FILTER -> "shortcut.filter.process";
        };
    }

    /** Serializes to the value stored in SettingsStore. */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(settings);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize global shortcuts", e);
        }
    }

    /** Deserializes from the value stored in SettingsStore. */
    public static GlobalShortcuts fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new GlobalShortcuts();
        }
        try {
            List<Object> untyped = MAPPER.readValue(json, MAPPER.getTypeFactory()
                    .constructCollectionType(List.class, Object.class));
            List<ShortcutSetting> converted = new ArrayList<>();
            for (Object item : untyped) {
                try {
                    ShortcutSetting setting = MAPPER.convertValue(item, ShortcutSetting.class);
                    converted.add(setting);
                } catch (Exception ignored) {
                    // Skip individual malformed entries instead of failing the whole shortcuts.
                }
            }
            if (converted.isEmpty()) {
                return new GlobalShortcuts();
            }
            return new GlobalShortcuts(converted);
        } catch (Exception e) {
            return new GlobalShortcuts();
        }
    }

    /** Normalizes a key combination string for storage/validation. */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.strip();
        if (s.isEmpty()) {
            return "";
        }
        // Canonicalize whitespace: collapse internal whitespace to a single space.
        s = s.replaceAll("\\s+", " ");
        // Strip optional surrounding quotes if present.
        if (s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1).strip();
        }
        return s;
    }

    /** Valid action values used when reading back from JSON. */
    public static List<String> validActionNames() {
        return DEFAULTS.stream().map(ShortcutSetting::action).distinct().toList();
    }

    /** Default key combination for each action. */
    public static String defaultFor(Action action) {
        return switch (action) {
            case NEXT_CARD -> DEFAULT_NEXT;
            case PREV_CARD -> DEFAULT_PREV;
            case EDIT_CARD -> DEFAULT_EDIT;
            case EXIT -> DEFAULT_EXIT;
            case LABEL_FILTER -> DEFAULT_LABEL_FILTER;
            case PROCESS_FILTER -> DEFAULT_PROCESS_FILTER;
        };
    }
}
