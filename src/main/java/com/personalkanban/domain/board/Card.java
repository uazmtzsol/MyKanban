package com.personalkanban.domain.board;

import java.time.Instant;
import java.util.Objects;

/**
 * A work item. Deliberately immutable: every mutation goes through the
 * aggregate root ({@link Board}), which owns the invariants.
 */
public final class Card {

    private final CardId id;
    private final ColumnId columnId;
    private String title;
    private String description;
    private BoardColor color;
    private final Instant createdAt;

    Card(ColumnId columnId, String title, String description, BoardColor color) {
        this(Ids.newCardId(), columnId, title, description, color, Instant.now());
    }

    Card(CardId id, ColumnId columnId, String title, String description, BoardColor color, Instant createdAt) {
        if (id == null || columnId == null || color == null || createdAt == null) {
            throw new IllegalArgumentException("Card fields must not be null");
        }
        this.id = id;
        this.columnId = columnId;
        setTitle(title);
        setDescription(description);
        this.color = color;
        this.createdAt = createdAt;
    }

    static Card restore(CardId id, ColumnId columnId, String title, String description,
                        BoardColor color, Instant createdAt) {
        return new Card(id, columnId, title, description, color, createdAt);
    }

    void rename(String newTitle) {
        setTitle(newTitle);
    }

    void describe(String newDescription) {
        setDescription(newDescription);
    }

    void recolor(BoardColor newColor) {
        this.color = Objects.requireNonNull(newColor, "color");
    }

    private void setTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Card title must not be blank");
        }
        this.title = title.strip();
    }

    private void setDescription(String description) {
        this.description = description == null ? "" : description.strip();
    }

    public CardId id() {
        return id;
    }

    public ColumnId columnId() {
        return columnId;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public BoardColor color() {
        return color;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
