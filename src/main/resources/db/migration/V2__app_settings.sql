-- Application-level key/value settings (UI theme, language, ...).

CREATE TABLE app_setting (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);
