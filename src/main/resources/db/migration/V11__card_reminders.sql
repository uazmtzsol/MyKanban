-- T23 · B10 (user request): programmable reminders on cards with a due date.
-- One row per reminder MOMENT (not per repetition rule): "daily" and "weekly"
-- are just ways to fill this list, so the calendar shows exactly what will
-- fire and the user can drop single days afterwards.
-- on_date is ISO-8601; on_time is ISO-8601 (HH:MM[:SS]) or NULL for a
-- reminder that fires from the start of its day. acknowledged survives
-- restarts on purpose: a reminder the user already attended must not nag
-- again on the next launch.
CREATE TABLE card_reminder (
    id           TEXT    PRIMARY KEY,
    card_id      TEXT    NOT NULL REFERENCES card (id) ON DELETE CASCADE,
    on_date      TEXT    NOT NULL,
    on_time      TEXT,
    acknowledged INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_reminder_card ON card_reminder (card_id, on_date);
