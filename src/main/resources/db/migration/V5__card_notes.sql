-- V5 (session 4.4): plain-text notes per card, distinct from the markdown
-- description. Empty default keeps every existing row valid.
ALTER TABLE card ADD COLUMN notes TEXT NOT NULL DEFAULT '';
