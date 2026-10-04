package com.personalkanban.domain.board;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Ordered timeline of {@link TimelineEntry} entries for one card.
 *
 * <p>The stopwatch workflow is: {@link #startTracking(Instant)} creates and
 * starts a new entry; {@link #stopTracking(Instant)} closes the running one;
 * {@link #setRunningComment(String)} attaches a comment to it. Every entry
 * stays in the list even after being closed (append-only history). The list
 * is kept ordered by start time, with not-yet-started entries last.</p>
 */
public final class Timeline {

    private final CardId cardId;
    private final List<TimelineEntry> entries = new ArrayList<>();

    public Timeline(CardId cardId) {
        this.cardId = Objects.requireNonNull(cardId, "cardId");
    }

    public CardId cardId() {
        return cardId;
    }

    /** Unmodifiable snapshot of the entries, in chronological order. */
    public List<TimelineEntry> entries() {
        sort();
        return List.copyOf(entries);
    }

    /** The running (open) entry, or null when nothing is being tracked. */
    public TimelineEntry runningEntry() {
        return entries.stream().filter(TimelineEntry::isRunning).findFirst().orElse(null);
    }

    public boolean isTracking() {
        return runningEntry() != null;
    }

    /** Creates and starts a new entry (first stopwatch click). */
    public TimelineEntry startTracking(Instant now) {
        Objects.requireNonNull(now, "now");
        TimelineEntry entry = TimelineEntry.open(cardId, EntryId.newId());
        entry.start(now);
        entries.add(entry);
        sort();
        return entry;
    }

    /** Closes the running entry, returning it (or null when none is running). */
    public TimelineEntry stopTracking(Instant now) {
        Objects.requireNonNull(now, "now");
        TimelineEntry running = runningEntry();
        if (running == null) {
            return null;
        }
        running.stop(now);
        return running;
    }

    /** Sets the comment of the running entry, keeping its start/end untouched. */
    public TimelineEntry setRunningComment(String comment) {
        TimelineEntry running = runningEntry();
        if (running == null) {
            return null;
        }
        running.setComment(comment);
        return running;
    }

    /** Deletes an entry (closed or running); returns true when it existed. */
    public boolean removeEntry(EntryId id) {
        return entries.removeIf(entry -> entry.id().equals(id));
    }

    /** Replaces the whole list (persistence / memento restore). */
    public void restore(List<TimelineEntry> restored) {
        entries.clear();
        if (restored != null) {
            entries.addAll(restored);
        }
        sort();
    }

    /** Adds an existing entry (used by memento restore per card). */
    public void adopt(TimelineEntry entry) {
        Objects.requireNonNull(entry, "entry");
        entries.add(entry);
        sort();
    }

    private void sort() {
        entries.sort(Comparator.comparing(
                entry -> entry.start() == null ? Instant.MAX : entry.start()));
    }
}
