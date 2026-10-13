package com.personalkanban.application;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * View preferences of ONE board (pure model, no JavaFX): the default card
 * view mode plus per-card overrides, and — since N-14 — what each card front
 * shows below the title (description / tasks / notes). Effective mode of a
 * card = its override ?? the board default. Serialized as JSON into {@code SettingsStore}
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

    /**
     * What the card front shows BELOW the title (user request N-14): the
     * description (as before), the task list, or the card's notes
     * ("comentarios" — the user confirmed they mean the notes).
     * Chosen per card with the button on the card itself.
     */
    public enum Content {
        DESCRIPTION,   // 1) the description, honouring the view Mode
        CHECKLIST,     // 2) the task list, checkable right on the card
        NOTES          // 3) the notes of the card
    }

    public static final Content DEFAULT_CONTENT = Content.DESCRIPTION;

    private Mode boardDefault = DEFAULT_MODE;
    /** Card id (string form) → mode; absent = follow the board default. */
    private Map<String, Mode> overrides = new HashMap<>();
    /** Card id → content; absent = DESCRIPTION (the pre-existing look). */
    private Map<String, Content> contentOverrides = new HashMap<>();

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

    /**
     * Effective content of a card: its override, or DESCRIPTION when it has
     * none. Unlike {@link #effectiveMode(String)} there is no board default:
     * the board has always looked like DESCRIPTION, and the choice is made
     * card by card.
     */
    public Content effectiveContent(String cardId) {
        Content override = contentOverrides.get(Objects.requireNonNull(cardId));
        return override != null ? override : DEFAULT_CONTENT;
    }

    /**
     * Sets the per-card content. Passing null (or the default) removes the
     * override, so "description" is stored as "no override at all".
     */
    public void setContentOverride(String cardId, Content content) {
        Objects.requireNonNull(cardId);
        if (content == null || content == DEFAULT_CONTENT) {
            contentOverrides.remove(cardId);
        } else {
            contentOverrides.put(cardId, content);
        }
    }

    /** True when the card's front shows something other than the description. */
    public boolean hasContentOverride(String cardId) {
        return contentOverrides.containsKey(cardId);
    }

    /** Removes every per-card override (mode and content). */
    public void clearOverrides() {
        overrides.clear();
        contentOverrides.clear();
    }

    public int overrideCount() {
        return overrides.size();
    }

    public int contentOverrideCount() {
        return contentOverrides.size();
    }

    // ------------------------------------------------------------------
    // JSON round-trip (SettingsStore stores plain strings)
    // ------------------------------------------------------------------

    /**
     * Wire format: {"default": "TITLE_PREVIEW", "overrides": {"card-id": "FULL"},
     * "content": {"card-id": "CHECKLIST"}}. The {@code content} section is
     * absent on settings written before N-14; a missing section means
     * "every card keeps showing the description".
     */
    public String toJson() {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("default", boardDefault.name());
            Map<String, String> byName = new HashMap<>();
            overrides.forEach((id, mode) -> byName.put(id, mode.name()));
            payload.put("overrides", byName);
            Map<String, String> contentByName = new HashMap<>();
            contentOverrides.forEach((id, content) -> contentByName.put(id, content.name()));
            payload.put("content", contentByName);
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
            Object rawContent = payload.get("content");
            if (rawContent instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() instanceof String id && entry.getValue() instanceof String name) {
                        settings.contentOverrides.put(id, Content.valueOf(name));
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
