-- Multi-board support: catalog table + board_id on columns.
-- ALTER TABLE ... RENAME rewrites child FK references in SQLite, so the
-- card table is rebuilt as well, with foreign keys disabled during the move.

PRAGMA foreign_keys = OFF;

CREATE TABLE board (
    id         TEXT PRIMARY KEY,
    name       TEXT    NOT NULL,
    created_at INTEGER NOT NULL
);

ALTER TABLE board_column RENAME TO board_column_old;
ALTER TABLE card RENAME TO card_old;

CREATE TABLE board_column (
    id          TEXT PRIMARY KEY,
    board_id    TEXT    NOT NULL REFERENCES board (id) ON DELETE CASCADE,
    title       TEXT    NOT NULL,
    description TEXT    NOT NULL DEFAULT '',
    color       TEXT    NOT NULL,
    position    INTEGER NOT NULL,
    wip_limit   INTEGER,
    created_at  INTEGER NOT NULL
);

CREATE TABLE card (
    id          TEXT PRIMARY KEY,
    column_id   TEXT    NOT NULL REFERENCES board_column (id) ON DELETE CASCADE,
    title       TEXT    NOT NULL,
    description TEXT    NOT NULL DEFAULT '',
    color       TEXT    NOT NULL,
    position    INTEGER NOT NULL,
    due_date    TEXT,
    labels      TEXT    NOT NULL DEFAULT '',
    created_at  INTEGER NOT NULL
);

INSERT INTO board (id, name, created_at)
VALUES ('default-board', 'My Board', 0);

INSERT INTO board_column (id, board_id, title, description, color, position, wip_limit, created_at)
SELECT id, 'default-board', title, description, color, position, wip_limit, created_at
FROM board_column_old;

INSERT INTO card (id, column_id, title, description, color, position, due_date, labels, created_at)
SELECT id, column_id, title, description, color, position, due_date, labels, created_at
FROM card_old;

DROP TABLE card_old;
DROP TABLE board_column_old;

DROP INDEX IF EXISTS idx_card_column;
DROP INDEX IF EXISTS idx_column_position;
CREATE INDEX idx_column_board_position ON board_column (board_id, position);

PRAGMA foreign_keys = ON;
