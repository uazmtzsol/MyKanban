-- V7 (session 4.6): precedence links between cards + processes
-- (named groups of related cards working toward one goal).

-- "from" precedes "to". Both endpoints cascade on card deletion.
CREATE TABLE card_link (
    from_card_id TEXT NOT NULL REFERENCES card(id) ON DELETE CASCADE,
    to_card_id   TEXT NOT NULL REFERENCES card(id) ON DELETE CASCADE,
    PRIMARY KEY (from_card_id, to_card_id)
);

CREATE INDEX idx_card_link_to ON card_link (to_card_id);

CREATE TABLE process (
    id         TEXT PRIMARY KEY,
    board_id   TEXT NOT NULL REFERENCES board(id) ON DELETE CASCADE,
    name       TEXT NOT NULL,
    position   INTEGER NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE INDEX idx_process_board_position ON process (board_id, position);

-- Optional process membership; deleting a process leaves cards unassigned.
ALTER TABLE card ADD COLUMN process_id TEXT REFERENCES process(id) ON DELETE SET NULL;
