package com.personalkanban.application;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The database file did not pass its integrity check, so the app refuses to work
 * on it instead of writing into a broken file (user request T17 · H-3).
 *
 * <p>Carries the newest healthy backup when there is one, so the UI can offer to
 * open that instead. <b>Nothing is deleted or overwritten</b> by this path: the
 * broken file stays where it is for the user to keep or inspect, and the backup
 * is opened as it is.</p>
 *
 * <p>It lives in the application layer on purpose: both the UI and the
 * composition root have to catch it, and neither of them may know the SQLite
 * package (see {@code ArchitectureTest}). It is a plain
 * {@link RuntimeException} for the same reason — the storage-specific exception
 * of the SQLite layer stays there.</p>
 */
public final class CorruptDatabaseException extends RuntimeException {

    private final transient Path databaseFile;
    private final transient Optional<Path> backup;

    public CorruptDatabaseException(Path databaseFile, Optional<Path> backup) {
        super(describe(databaseFile, backup));
        this.databaseFile = databaseFile;
        this.backup = backup == null ? Optional.empty() : backup;
    }

    /** The file that could not be opened (left exactly where it was). */
    public Path databaseFile() {
        return databaseFile;
    }

    /** Newest healthy copy of this database, when any survived. */
    public Optional<Path> backup() {
        return backup;
    }

    private static String describe(Path databaseFile, Optional<Path> backup) {
        return "The database at " + databaseFile
                + " failed its integrity check, so it was not opened."
                + backup.map(path -> " The newest healthy backup is " + path
                        + " (the app can open it instead, or restore it by hand"
                        + " — the damaged file was left untouched).")
                .orElse(" There is no healthy backup of it in the backups folder yet.");
    }
}
