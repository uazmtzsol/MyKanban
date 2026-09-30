-- V6 (session 4.5): flat checklist per card (one level by user decision;
-- items may later be converted into cards, hence the stable id).
CREATE TABLE card_checklist_item (
    id        TEXT PRIMARY KEY,
    card_id   TEXT NOT NULL REFERENCES card(id) ON DELETE CASCADE,
    position  INTEGER NOT NULL,
    text      TEXT NOT NULL,
    done      INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_checklist_card_position ON card_checklist_item (card_id, position);
