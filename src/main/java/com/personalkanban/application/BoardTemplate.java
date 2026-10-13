package com.personalkanban.application;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.WipLimit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A user-defined board template (user request T14 · B5, pure — no JavaFX).
 *
 * <p>It stores the <b>structure</b> the user wants to reuse — name, columns
 * (title, description, color, WIP limit, done flag), process names and the
 * board's card-view mode — and deliberately <b>no cards</b>: creating a board
 * from it starts empty. Serialized as JSON into a {@code SettingsStore} entry
 * ({@code board.template.<name>}), so no schema migration is needed.</p>
 */
public final class BoardTemplate {

    /** One column of the template. */
    public record TemplateColumn(String title, String description, BoardColor color,
                                 WipLimit wipLimit, boolean done) {

        public TemplateColumn {
            title = title == null ? "" : title;
            description = description == null ? "" : description;
            color = color == null ? BoardColor.DEFAULT : color;
            wipLimit = wipLimit == null ? WipLimit.unlimited() : wipLimit;
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final String name;
    private final List<TemplateColumn> columns;
    private final List<String> processNames;
    private final CardViewSettings.Mode viewMode;

    public BoardTemplate(String name, List<TemplateColumn> columns, List<String> processNames,
                         CardViewSettings.Mode viewMode) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A template needs a name");
        }
        this.name = name.strip();
        this.columns = columns == null ? List.of() : List.copyOf(columns);
        this.processNames = processNames == null ? List.of() : List.copyOf(processNames);
        this.viewMode = viewMode == null ? CardViewSettings.DEFAULT_MODE : viewMode;
    }

    public String name() {
        return name;
    }

    public List<TemplateColumn> columns() {
        return columns;
    }

    public List<String> processNames() {
        return processNames;
    }

    public CardViewSettings.Mode viewMode() {
        return viewMode;
    }

    // ------------------------------------------------------------------
    // JSON round-trip (SettingsStore stores plain strings)
    // ------------------------------------------------------------------

    public String toJson() {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("name", name);
            List<Map<String, Object>> columnList = new ArrayList<>();
            for (TemplateColumn column : columns) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("title", column.title());
                entry.put("description", column.description());
                // Hex keeps custom colors self-describing (BoardColor identity).
                entry.put("color", column.color().hex());
                // 0 means unlimited (WipLimit has no public "value" accessor).
                entry.put("wip", column.wipLimit().asOptional().orElse(0));
                entry.put("done", column.done());
                columnList.add(entry);
            }
            payload.put("columns", columnList);
            payload.put("processes", processNames);
            payload.put("viewMode", viewMode.name());
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize the board template", e);
        }
    }

    /**
     * Reads a template from its JSON form. A malformed or unknown-format entry
     * yields an empty result instead of failing, so one corrupt setting can
     * never block the Templates menu.
     */
    @SuppressWarnings("unchecked")
    public static Optional<BoardTemplate> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> payload = MAPPER.readValue(json, Map.class);
            String name = payload.get("name") instanceof String value ? value : "";
            if (name.isBlank()) {
                return Optional.empty();
            }
            List<TemplateColumn> columns = new ArrayList<>();
            if (payload.get("columns") instanceof List<?> raw) {
                for (Object item : raw) {
                    if (item instanceof Map<?, ?> entry) {
                        columns.add(new TemplateColumn(
                                string(entry.get("title")),
                                string(entry.get("description")),
                                color(entry.get("color")),
                                wip(entry.get("wip")),
                                Boolean.TRUE.equals(entry.get("done"))));
                    }
                }
            }
            List<String> processes = new ArrayList<>();
            if (payload.get("processes") instanceof List<?> raw) {
                for (Object item : raw) {
                    if (item instanceof String process && !process.isBlank()) {
                        processes.add(process.strip());
                    }
                }
            }
            CardViewSettings.Mode mode = CardViewSettings.DEFAULT_MODE;
            if (payload.get("viewMode") instanceof String stored) {
                try {
                    mode = CardViewSettings.Mode.valueOf(stored);
                } catch (IllegalArgumentException ignored) {
                    // unknown mode: keep the default
                }
            }
            return Optional.of(new BoardTemplate(name, columns, processes, mode));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String string(Object value) {
        return value instanceof String text ? text : "";
    }

    private static BoardColor color(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            try {
                return BoardColor.fromStored(text);
            } catch (IllegalArgumentException ignored) {
                // unknown color: fall through to the default
            }
        }
        return BoardColor.DEFAULT;
    }

    private static WipLimit wip(Object value) {
        if (value instanceof Number number) {
            int limit = number.intValue();
            return limit > 0 ? WipLimit.of(limit) : WipLimit.unlimited();
        }
        return WipLimit.unlimited();
    }
}
