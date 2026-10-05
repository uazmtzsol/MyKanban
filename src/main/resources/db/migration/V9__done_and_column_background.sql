-- Session 8: "Finalizado" column flag + per-column background color.
-- At most one column per board carries done = 1 (enforced by the domain).
ALTER TABLE board_column ADD COLUMN done INTEGER NOT NULL DEFAULT 0;
ALTER TABLE board_column ADD COLUMN background_color TEXT;
