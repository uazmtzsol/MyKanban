package com.personalkanban.application;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * View preferences of ONE board (pure model, no JavaFX): the default card
 * view mode plus per-card overrides. Effective mode of a card = its override
 * ?? the board default. Serialized as JSON into {@code SettingsStore}
 * (key {@code ui.cardview.<boardId>}) — no schema migration needed, and the
 * preferences survive restarts.
 */
public final class CardViewSettings {

    /** The three view modes the user asked for. */
    public enum Mode {
        TITLE_ONLY,      // 1) just the title
        TITLE_PREVIEW,   // 2) title + first 3 lines
        FULL             // 3) title + full markdown summary
    }

    public static final Mode DEFAULT_MODE = Mode.TITLE_PREVIEW;

    private Mode boardDefault = DEFAULT_MODE;
    /** Card id (string form) → mode; absent = follow the board default. */
    private Map<String, Mode> overrides = new HashMap<>();

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public CardViewSettings() {
    }

    public Mode boardDefault() {
        return boardDefault;
    }

    public void setBoardDefault(Mode mode) {
        this.boardDefault = mode == null ? DEFAULT_MODE : mode;
    }

    /** Effective mode of a card: override ?? board default. */
    public Mode effectiveMode(String cardId) {
        Mode override = overrides.get(Objects.requireNonNull(cardId));
        return override != null ? override : boardDefault;
    }

    /** Sets (or clears with null) the per-card override. */
    public void setOverride(String cardId, Mode mode) {
        Objects.requireNonNull(cardId);
        if (mode == null) {
            overrides.remove(cardId);
        } else {
            overrides.put(cardId, mode);
        }
    }

    /** True when the card no longer follows the board default. */
    public boolean hasOverride(String cardId) {
        return overrides.containsKey(cardId);
    }

    /** Removes every per-card override (they all follow the board default). */
    public void clearOverrides() {
        overrides.clear();
    }

    public int overrideCount() {
        return overrides.size();
    }

    // ------------------------------------------------------------------
    // JSON round-trip (SettingsStore stores plain strings)
    // ------------------------------------------------------------------

    /** Wire format: {"default": "TITLE_PREVIEW", "overrides": {"card-id": "FULL"}}. */
    public String toJson() {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("default", boardDefault.name());
            Map<String, String> byName = new HashMap<>();
            overrides.forEach((id, mode) -> byName.put(id, mode.name()));
            payload.put("overrides", byName);
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize card view settings", e);
        }
    }

    public static CardViewSettings fromJson(String json) {
        CardViewSettings settings = new CardViewSettings();
        if (json == null || json.isBlank()) {
            return settings;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = MAPPER.readValue(json, Map.class);
            Object rawDefault = payload.get("default");
            if (rawDefault instanceof String name) {
                settings.boardDefault = Mode.valueOf(name);
            }
            Object rawOverrides = payload.get("overrides");
            if (rawOverrides instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() instanceof String id && entry.getValue() instanceof String name) {
                        settings.overrides.put(id, Mode.valueOf(name));
                    }
                }
            }
            return settings;
        } catch (Exception e) {
            // Corrupt or future-format settings must never block the app.
            return new CardViewSettings();
        }
    }
}
