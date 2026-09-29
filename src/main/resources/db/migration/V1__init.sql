-- Personal Kanban schema, version 1.
-- IDs are UUID strings; positions are zero-based list indexes maintained by the application.

CREATE TABLE board_column (
    id          TEXT PRIMARY KEY,
    title       TEXT    NOT NULL,
    description TEXT    NOT NULL DEFAULT '',
    color       TEXT    NOT NULL,
    position    INTEGER NOT NULL,
    wip_limit   INTEGER,
    created_at  TEXT    NOT NULL
);

CREATE TABLE card (
    id          TEXT PRIMARY KEY,
    column_id   TEXT    NOT NULL REFERENCES board_column (id) ON DELETE CASCADE,
    title       TEXT    NOT NULL,
    description TEXT    NOT NULL DEFAULT '',
    color       TEXT    NOT NULL,
    position    INTEGER NOT NULL,
    created_at  TEXT    NOT NULL
);

CREATE INDEX idx_column_position ON board_column (position);
CREATE INDEX idx_card_column ON card (column_id, position);
