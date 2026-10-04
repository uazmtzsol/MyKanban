package com.personalkanban.domain.board;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * One time-tracking entry of a card (a single "registro de tiempo").
 *
 * <p>An entry is created already started ({@code start} set, {@code end} null)
 * when the user first clicks the stopwatch; a second click closes it by
 * setting {@code end}. Entries are append-only from the UI's point of view:
 * start, end and comment of a closed entry are never edited, only the live
 * entry's comment can be typed (and it is normalized on close). Deletion is
 * allowed. This keeps the time log trustworthy for later analytics.</p>
 */
public final class TimelineEntry {

    private static final int MAX_COMMENT_LEN = 1000;

    private final EntryId id;
    private final CardId cardId;
    private Instant start;
    private Instant end;
    private String comment = "";

    private TimelineEntry(CardId cardId, EntryId id, Instant start, Instant end, String comment) {
        this.cardId = Objects.requireNonNull(cardId, "cardId");
        this.id = Objects.requireNonNull(id, "id");
        this.start = start;
        this.end = end;
        this.comment = normalizeComment(comment);
    }

    /** Creates a brand-new, not-yet-started entry. */
    public static TimelineEntry open(CardId cardId, EntryId id) {
        return new TimelineEntry(cardId, id, null, null, "");
    }

    /** Rebuilds an entry from stored state (persistence / memento). */
    public static TimelineEntry restore(CardId cardId, EntryId id, Instant start, Instant end, String comment) {
        return new TimelineEntry(cardId, id, start, end, comment);
    }

    /** Normalizes and sanitizes the comment (no control characters, bounded). */
    private static String normalizeComment(String raw) {
        if (raw == null) {
            return "";
        }
        String stripped = raw.strip();
        if (stripped.isEmpty()) {
            return "";
        }
        if (stripped.length() > MAX_COMMENT_LEN) {
            stripped = stripped.substring(0, MAX_COMMENT_LEN);
        }
        StringBuilder sb = new StringBuilder(stripped.length());
        for (int i = 0; i < stripped.length(); i++) {
            char ch = stripped.charAt(i);
            if (ch <= '\u001F' || ch == '\u007F') {
                continue; // drop control characters, keep visible text
            }
            sb.append(ch);
        }
        return sb.toString().strip();
    }

    /** Defensive copy, so a memento never aliases a live, mutable entry. */
    public TimelineEntry copy() {
        return new TimelineEntry(cardId, id, start, end, comment);
    }

    public CardId cardId() {
        return cardId;
    }

    public EntryId id() {
        return id;
    }

    /** True while the entry is running (started and not yet closed). */
    public boolean isRunning() {
        return start != null && end == null;
    }

    /** True once the entry has both endpoints recorded. */
    public boolean isClosed() {
        return start != null && end != null;
    }

    /** A blank placeholder entry (never persisted): nothing tracked yet. */
    public boolean isEmpty() {
        return start == null && end == null && comment.isBlank();
    }

    /** Starts a not-yet-started entry (first stopwatch click). */
    public void start(Instant now) {
        if (start != null) {
            throw new IllegalStateException("The entry is already started");
        }
        this.start = Objects.requireNonNull(now, "now");
        this.end = null;
    }

    /** Closes a running entry (second stopwatch click). */
    public void stop(Instant now) {
        if (start == null) {
            throw new IllegalStateException("The entry is not started");
        }
        if (end != null) {
            throw new IllegalStateException("The entry is already stopped");
        }
        this.end = Objects.requireNonNull(now, "now");
    }

    /**
     * Updates the comment. Allowed while the entry is running (or not yet
     * started); a closed entry is immutable by design.
     */
    public void setComment(String comment) {
        if (isClosed()) {
            throw new IllegalStateException("A closed entry cannot change its comment");
        }
        this.comment = normalizeComment(comment);
    }

    /** Clears the comment of a still-open entry. */
    public void clearComment() {
        if (isClosed()) {
            throw new IllegalStateException("A closed entry cannot change its comment");
        }
        this.comment = "";
    }

    public Instant start() {
        return start;
    }

    public Instant end() {
        return end;
    }

    /** Elapsed time: to {@code end} when closed, to {@code now} while running. */
    public Duration duration(Instant now) {
        if (start == null) {
            return Duration.ZERO;
        }
        Instant finish = end != null ? end : now;
        if (finish == null || finish.isBefore(start)) {
            return Duration.ZERO;
        }
        return Duration.between(start, finish);
    }

    /** Milliseconds elapsed (convenience for analytics and the UI). */
    public long durationMillis(Instant now) {
        return duration(now).toMillis();
    }

    public boolean hasComment() {
        return !comment.isBlank();
    }

    public String comment() {
        return comment;
    }

    @Override
    public String toString() {
        if (start == null) {
            return "Entry{" + id + " empty on " + cardId + "}";
        }
        return "Entry{" + id + " " + start + " → " + (end == null ? "running" : end) + "}";
    }
}
