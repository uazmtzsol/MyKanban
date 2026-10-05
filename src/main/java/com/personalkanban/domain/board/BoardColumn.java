package com.personalkanban.domain.board;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A column of the board: the Information Expert for its own cards. Enforces
 * the WIP invariant. Card order is simply the list order; no stored position
 * field exists to drift out of sync (single source of truth).
 */
public final class BoardColumn {

    private final ColumnId id;
    private String title;
    private String description;
    private BoardColor color;
    private WipLimit wipLimit;
    private boolean done;
    private String backgroundColor;
    private final List<Card> cards = new ArrayList<>();
    private final Instant createdAt;

    BoardColumn(String title, String description, BoardColor color, WipLimit wipLimit) {
        this(Ids.newColumnId(), title, description, color, wipLimit, Instant.now(), false, null);
    }

    BoardColumn(ColumnId id, String title, String description, BoardColor color,
                WipLimit wipLimit, Instant createdAt) {
        this(id, title, description, color, wipLimit, createdAt, false, null);
    }

    BoardColumn(ColumnId id, String title, String description, BoardColor color,
                WipLimit wipLimit, Instant createdAt, boolean done, String backgroundColor) {
        if (id == null || color == null || wipLimit == null || createdAt == null) {
            throw new IllegalArgumentException("Column fields must not be null");
        }
        this.id = id;
        setTitle(title);
        setDescription(description);
        this.color = color;
        this.wipLimit = wipLimit;
        this.done = done;
        this.backgroundColor = normalizeColor(backgroundColor);
        this.createdAt = createdAt;
    }

    static BoardColumn restore(ColumnId id, String title, String description, BoardColor color,
                               WipLimit wipLimit, Instant createdAt) {
        return restore(id, title, description, color, wipLimit, createdAt, false, null);
    }

    static BoardColumn restore(ColumnId id, String title, String description, BoardColor color,
                               WipLimit wipLimit, Instant createdAt, boolean done,
                               String backgroundColor) {
        return new BoardColumn(id, title, description, color, wipLimit, createdAt, done, backgroundColor);
    }

    // ------------------------------------------------------------------
    // Commands (package-private: only the Board aggregate may call these)
    // ------------------------------------------------------------------

    void rename(String newTitle) {
        setTitle(newTitle);
    }

    void describe(String newDescription) {
        setDescription(newDescription);
    }

    void recolor(BoardColor newColor) {
        this.color = Objects.requireNonNull(newColor, "color");
    }

    /** Marks/unmarks the column as the board's "done" column. */
    void markDone(boolean done) {
        this.done = done;
    }

    /** Sets the column background color (hex string) or null to clear it. */
    void setBackground(String backgroundColor) {
        this.backgroundColor = normalizeColor(backgroundColor);
    }

    void limitTo(WipLimit newLimit) {
        Objects.requireNonNull(newLimit, "wipLimit");
        if (newLimit.isExceededBy(cards.size())) {
            throw new IllegalArgumentException(
                    "New WIP limit " + newLimit.value() + " is below the current card count " + cards.size());
        }
        this.wipLimit = newLimit;
    }

    /** Appends a card, enforcing the WIP limit, and returns the resulting event. */
    CardAdded addCard(String title, String description, BoardColor color,
                      java.time.LocalDate dueDate, List<String> labels) {
        return addCard(Ids.newCardId(), title, description, color, dueDate, labels);
    }

    /** Appends a card with a pre-minted id (session-4 extras on the aggregate). */
    CardAdded addCard(CardId cardId, String title, String description, BoardColor color,
                      java.time.LocalDate dueDate, List<String> labels) {
        if (wipLimit.isExceededBy(cards.size() + 1)) {
            throw new com.personalkanban.domain.exception.WipLimitExceededException(id, wipLimit.value());
        }
        Card card = new Card(cardId, id, title, description, color, dueDate, labels, Instant.now());
        cards.add(card);
        return new CardAdded(card.id(), id, Instant.now());
    }

    /** Color used for cards converted from checklist items (same as new columns). */
    BoardColor defaultCardColor() {
        return BoardColor.DEFAULT;
    }

    void removeCard(CardId cardId) {
        cards.removeIf(card -> card.id().equals(cardId));
    }

    void clearCards() {
        cards.clear();
    }

    /** Replaces the whole card order (priority sort); aggregate-checked. */
    void replaceCards(List<Card> newOrder) {
        cards.clear();
        cards.addAll(newOrder);
    }

    /**
     * Moves a card to {@code targetIndex} within this column, or moves it in
     * from {@code source} when different. On a WIP violation the card is put
     * back on the source column and the move is rejected atomically.
     */
    void moveCardFrom(BoardColumn source, CardId cardId, int targetIndex) {
        Card card;
        int effectiveTarget = targetIndex;
        if (source == this) {
            card = cardById(cardId)
                    .orElseThrow(() -> new com.personalkanban.domain.exception.NotFoundException(cardId));
            int sourceIndex = cards.indexOf(card);
            cards.remove(card);
            // "Insert before index N" is relative to the pre-removal list;
            // shift left when the removal happened before the target slot.
            if (sourceIndex < effectiveTarget) {
                effectiveTarget--;
            }
        } else {
            card = source.extract(cardId);
            if (wipLimit.isExceededBy(cards.size() + 1)) {
                source.append(card);
                throw new com.personalkanban.domain.exception.WipLimitExceededException(id, wipLimit.value());
            }
        }
        cards.add(Math.clamp(effectiveTarget, 0, cards.size()), card);
    }

    private void append(Card card) {
        cards.add(card);
    }

    private Card extract(CardId cardId) {
        Card card = cardById(cardId)
                .orElseThrow(() -> new com.personalkanban.domain.exception.NotFoundException(cardId));
        cards.remove(card);
        return card;
    }

    /**
     * Bulk-moves the given cards from this column into {@code target},
     * appending them in list order (same package: only the Board aggregate
     * orchestrates this). No WIP check here — the aggregate validates the
     * whole batch before mutating anything, keeping the move atomic.
     */
    void transferCardsTo(BoardColumn target, List<CardId> cardIds) {
        for (CardId cardId : cardIds) {
            Card card = cardById(cardId)
                    .orElseThrow(() -> new com.personalkanban.domain.exception.NotFoundException(cardId));
            cards.remove(card);
            target.cards.add(card);
        }
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public ColumnId id() {
        return id;
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

    public WipLimit wipLimit() {
        return wipLimit;
    }

    /** True when this column is the board's "done" column. */
    public boolean isDone() {
        return done;
    }

    /** Column background color (hex), or null when none. */
    public String backgroundColor() {
        return backgroundColor;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** Unmodifiable snapshot of the cards in board order. */
    public List<Card> cards() {
        return List.copyOf(cards);
    }

    public int cardCount() {
        return cards.size();
    }

    public boolean isFull() {
        return wipLimit.isExceededBy(cards.size());
    }

    public boolean hasCard(CardId cardId) {
        return cards.stream().anyMatch(card -> card.id().equals(cardId));
    }

    public Optional<Card> cardById(CardId cardId) {
        return cards.stream().filter(card -> card.id().equals(cardId)).findFirst();
    }

    // ------------------------------------------------------------------
    // Memento support (package-private; see BoardMemento)
    // ------------------------------------------------------------------

    void adoptForMemento(Card card) {
        cards.add(card);
    }

    private void setTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Column title must not be blank");
        }
        this.title = title.strip();
    }

    private void setDescription(String description) {
        this.description = description == null ? "" : description.strip();
    }

    private static String normalizeColor(String backgroundColor) {
        if (backgroundColor == null || backgroundColor.isBlank()) {
            return null;
        }
        return backgroundColor.strip();
    }
}
