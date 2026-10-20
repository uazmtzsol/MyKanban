-- T24 · B11 (user request): the card remembers WHEN it was finished.
-- Dropping a card into the board's finalized column marks it done on that day,
-- and the ✓ button on the card front does the same by hand, so this is a real
-- completion date and not a flag: "delivered late" is then a comparison with
-- due_date and reporting needs no extra join.
-- completed_at is ISO-8601 (yyyy-MM-dd) or NULL while the card is pending.
ALTER TABLE card ADD COLUMN completed_at TEXT;
