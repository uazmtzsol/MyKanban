package com.personalkanban.application;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.EntryId;
import com.personalkanban.domain.board.TimelineEntry;

import java.io.IOException;
import java.time.Instant;

/**
 * Shared Jackson mapper for the board wire format (undo history and JSON
 * export/import). {@code TimelineEntry} is a mutable domain class without
 * bean-style getters, so Jackson needs an explicit module; keeping it here
 * lets every adapter use the same mapper while the domain stays free of
 * serialization concerns. Jackson is a technology-neutral library, so living
 * in the application layer is not a layering violation.
 */
public final class BoardJsonMapper {

    private BoardJsonMapper() {
    }

    /** A new mapper configured for board mementos (records + timeline). */
    public static ObjectMapper create() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(timelineModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private static SimpleModule timelineModule() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(TimelineEntry.class, new JsonSerializer<>() {
            @Override
            public void serialize(TimelineEntry entry, JsonGenerator gen, SerializerProvider serializers)
                    throws IOException {
                gen.writeStartObject();
                gen.writeStringField("cardId", entry.cardId().value());
                gen.writeStringField("id", entry.id().value());
                gen.writeStringField("start", entry.start() == null ? null : entry.start().toString());
                gen.writeStringField("end", entry.end() == null ? null : entry.end().toString());
                gen.writeStringField("comment", entry.comment());
                gen.writeEndObject();
            }
        });
        module.addDeserializer(TimelineEntry.class, new JsonDeserializer<>() {
            @Override
            public TimelineEntry deserialize(JsonParser parser, DeserializationContext context)
                    throws IOException {
                JsonNode node = parser.getCodec().readTree(parser);
                Instant start = node.hasNonNull("start") ? Instant.parse(node.get("start").asText()) : null;
                Instant end = node.hasNonNull("end") ? Instant.parse(node.get("end").asText()) : null;
                String comment = node.hasNonNull("comment") ? node.get("comment").asText() : "";
                return TimelineEntry.restore(new CardId(node.get("cardId").asText()),
                        new EntryId(node.get("id").asText()), start, end, comment);
            }
        });
        return module;
    }
}
