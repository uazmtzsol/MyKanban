package com.personalkanban.domain.board;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A work item. Deliberately immutable: every mutation goes through the
 * aggregate root ({@link Board}), which owns the invariants. Cards carry an
 * optional due date and a small set of free-form labels.
 */
public final class Card {

    private static final int MAX_LABELS = 8;
    private static final int MAX_LABEL_LENGTH = 40;

    /**
     * System labels with one-click affordances (star = important, bang =
     * urgent). Deliberately plain strings — they are ordinary labels in the
     * set (they join any bulk operation, filter, and future chip coloring);
     * only the UI gives them dedicated buttons. Case-insensitive matching
     * keeps "Urgente" and "urgente" the same label.
     */
    public static final String LABEL_IMPORTANT = "Importante";
    public static final String LABEL_URGENT = "Urgente";

    private final CardId id;
    private final ColumnId columnId;
    private String title;
    private String description;
    private BoardColor color;
    private LocalDate dueDate;
    private List<String> labels = List.of();
    private final Instant createdAt;

    Card(ColumnId columnId, String title, String description, BoardColor color) {
        this(Ids.newCardId(), columnId, title, description, color, null, List.of(), Instant.now());
    }

    Card(ColumnId columnId, String title, String description, BoardColor color,
         LocalDate dueDate, List<String> labels) {
        this(Ids.newCardId(), columnId, title, description, color, dueDate, labels, Instant.now());
    }

    Card(CardId id, ColumnId columnId, String title, String description, BoardColor color,
         LocalDate dueDate, List<String> labels, Instant createdAt) {
        if (id == null || columnId == null || color == null || createdAt == null) {
            throw new IllegalArgumentException("Card fields must not be null");
        }
        this.id = id;
        this.columnId = columnId;
        setTitle(title);
        setDescription(description);
        this.color = color;
        this.dueDate = dueDate;
        setLabels(labels);
        this.createdAt = createdAt;
    }

    static Card restore(CardId id, ColumnId columnId, String title, String description,
                        BoardColor color, LocalDate dueDate, List<String> labels, Instant createdAt) {
        return new Card(id, columnId, title, description, color, dueDate, labels, createdAt);
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

    void schedule(LocalDate newDueDate) {
        this.dueDate = newDueDate;
    }

    void tag(List<String> newLabels) {
        setLabels(newLabels);
    }

    /** Normalizes labels: stripped, deduplicated, bounded. */
    private void setLabels(List<String> labels) {
        if (labels == null) {
            this.labels = List.of();
            return;
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String label : labels) {
            if (label == null) {
                continue;
            }
            String stripped = label.strip();
            if (stripped.isEmpty()) {
                continue;
            }
            if (stripped.length() > MAX_LABEL_LENGTH) {
                throw new IllegalArgumentException(
                        "Label longer than " + MAX_LABEL_LENGTH + " chars: " + stripped);
            }
            if (unique.size() >= MAX_LABELS) {
                throw new IllegalArgumentException("A card may have at most " + MAX_LABELS + " labels");
            }
            unique.add(stripped);
        }
        this.labels = List.copyOf(unique);
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

    public LocalDate dueDate() {
        return dueDate;
    }

    public List<String> labels() {
        return labels;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean isOverdueOn(LocalDate today) {
        return dueDate != null && dueDate.isBefore(today);
    }

    /** True when this card carries the given label, ignoring case. */
    public boolean hasLabelIgnoreCase(String label) {
        return labels.stream().anyMatch(existing -> existing.equalsIgnoreCase(label));
    }
}
