package com.personalkanban.domain.board;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/**
 * One reminder moment of a card (user request T23 · B10): a date and an
 * <b>optional</b> time at which the app should show the card's reminder
 * message. A reminder with no time fires from the start of its day.
 *
 * <p>Deliberately a <b>moment</b> and not a repetition rule: "daily" and
 * "weekly" are just helpers that fill a card's list of moments, so there is a
 * single model, the calendar shows exactly what will fire, and the user can
 * drop single days afterwards.</p>
 *
 * <p>{@code acknowledged} belongs to the aggregate (not to the UI) so it
 * survives restarts and travels through undo/redo, export and sync — without
 * it, an answered reminder would nag again on every launch.</p>
 */
public record CardReminder(String id, LocalDate date, LocalTime time, boolean acknowledged) {

    /** Chronological order: by date, then by time (all-day first). */
    public static final Comparator<CardReminder> ORDER = Comparator
            .comparing(CardReminder::date)
            .thenComparing(CardReminder::time, Comparator.nullsFirst(Comparator.naturalOrder()));

    public CardReminder {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Reminder id must not be blank");
        }
        Objects.requireNonNull(date, "Reminder date must not be null");
    }

    /** Fresh, pending reminder. */
    public static CardReminder of(LocalDate date, LocalTime time) {
        return new CardReminder(UUID.randomUUID().toString(), date, time, false);
    }

    /** Copy with a new acknowledged state; keeps identity, date and time. */
    public CardReminder withAcknowledged(boolean newAcknowledged) {
        return new CardReminder(id, date, time, newAcknowledged);
    }

    /** The instant from which this reminder is due (00:00 when it has no time). */
    public LocalDateTime dueFrom() {
        return time == null ? date.atStartOfDay() : LocalDateTime.of(date, time);
    }

    /** Compact human form, e.g. {@code 2026-10-20} or {@code 2026-10-20 09:00}. */
    public String describe() {
        return time == null ? date.toString() : date + " " + time;
    }
}
