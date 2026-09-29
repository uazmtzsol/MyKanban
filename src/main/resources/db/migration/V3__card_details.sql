-- Card details: optional due date (ISO-8601 text) and comma-separated labels.

ALTER TABLE card ADD COLUMN due_date TEXT;
ALTER TABLE card ADD COLUMN labels TEXT NOT NULL DEFAULT '';
