-- V8: time-tracking per card ("registros de tiempo").
-- One row per tracked interval. An entry is created already started
-- (started_at set) and closed by filling ended_at; a running entry has
-- ended_at NULL. Start/end/comment of a closed entry are never edited by the
-- UI, only deleted. Times are stored as epoch milliseconds, like created_at.
CREATE TABLE timeline (
    id         TEXT PRIMARY KEY,
    card_id    TEXT NOT NULL REFERENCES card(id) ON DELETE CASCADE,
    started_at INTEGER,
    ended_at   INTEGER,
    comment    TEXT
);

CREATE INDEX idx_timeline_card ON timeline (card_id);
