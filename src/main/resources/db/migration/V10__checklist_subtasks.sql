-- T16 · B8 (user request): one-level subtasks in a card's checklist.
-- parent_id is NULL for top-level items and points at the parent item for a
-- subtask. No foreign key on purpose: the aggregate (Card) owns the one-level
-- invariant, and a parent that disappears takes its children with it inside the
-- same aggregate, so the database never has to cascade.
ALTER TABLE card_checklist_item ADD COLUMN parent_id TEXT;

CREATE INDEX idx_checklist_parent ON card_checklist_item (parent_id);
