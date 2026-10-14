package com.personalkanban.infrastructure.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rotating backups of the database file and the integrity check that decides
 * whether a file is worth opening at all (user request T17 · H-2 + H-3).
 *
 * <p>The copy is taken <b>before</b> the connection is opened and before the
 * migrations run, so every backup is the state the user had at the previous
 * launch: if a session or a migration damages the database, the previous one is
 * still there. The copies live in a {@code backups/} folder next to the
 * database (like {@code history/} and {@code conflicts/}), so a database stays a
 * self-contained unit one can move between computers.</p>
 *
 * <p>Rotation is decided by the <b>timestamp in the file name</b>, not by the
 * filesystem's modification time: the policy is then the same on every
 * filesystem, is trivially testable and a restored/renamed file cannot reshuffle
 * the order. Files that are not ours are never deleted.</p>
 */
public final class BackupService {

    /** Backups kept next to one database (older ones are pruned). */
    public static final int KEEP = 10;

    /** Folder name of the copies, next to the database file. */
    public static final String FOLDER = "backups";

    private static final String PREFIX = "kanban-";
    private static final String SUFFIX = ".db";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Pattern NAME = Pattern.compile(
            Pattern.quote(PREFIX) + "(\\d{8}-\\d{6})(?:-(\\d+))?" + Pattern.quote(SUFFIX));

    /** What the integrity check says about a file. */
    public enum Integrity {
        /** Readable and self-consistent: safe to open. */
        OK,
        /** SQLite says it is not (or no longer is) a database. */
        CORRUPT,
        /**
         * Could not even be read (missing, locked by something else, permissions).
         * Deliberately <b>not</b> reported as corruption: a transient failure must
         * not make the app refuse the user's own file.
         */
        UNREADABLE
    }

    private final Path databaseFile;

    public BackupService(Path databaseFile) {
        this.databaseFile = Objects.requireNonNull(databaseFile, "databaseFile");
    }

    /** The folder holding this database's copies. */
    public Path backupDirectory() {
        Path parent = databaseFile.toAbsolutePath().normalize().getParent();
        return (parent == null ? Path.of(".") : parent).resolve(FOLDER);
    }

    /**
     * Copies the database into {@link #backupDirectory()} and prunes the older
     * copies. Empty when there is nothing to copy yet (first launch, or a brand
     * new database file).
     */
    public Optional<Path> backup() {
        if (!Files.isRegularFile(databaseFile)) {
            return Optional.empty();
        }
        Path directory = backupDirectory();
        try {
            Files.createDirectories(directory);
            Path target = nextFreeName(directory, LocalDateTime.now());
            // No connection is open at this point (the caller backs up before
            // opening the database), so this is a complete file, not a snapshot
            // taken mid-write.
            Files.copy(databaseFile, target, StandardCopyOption.COPY_ATTRIBUTES);
            prune();
            return Optional.of(target);
        } catch (IOException e) {
            // A missing backup must never stop the app from opening the user's
            // data: the failure is reported, the open continues.
            throw new DataAccessException("Could not back up " + databaseFile + " into " + directory, e);
        }
    }

    /** The copies of this database, newest first. */
    public List<Path> list() {
        Path directory = backupDirectory();
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var files = Files.list(directory)) {
            return files.filter(BackupService::isBackup)
                    .sorted(Comparator.comparing(BackupService::sortKey).reversed())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * The newest copy that passes the integrity check — what the app offers the
     * user when the live database is unreadable (T17 · H-3). Nothing is deleted
     * or overwritten in the process: the broken file stays exactly where it is.
     */
    public Optional<Path> newestHealthyBackup() {
        return list().stream().filter(file -> integrity(file) == Integrity.OK).findFirst();
    }

    /**
     * Restores {@code backup} as a <b>new</b> file next to the damaged database,
     * named {@code <name>-restored-<stamp>.db}, and returns it (T17 · H-3).
     *
     * <p>This is the guided repair: nothing is overwritten — the damaged file and
     * the copy both stay exactly where they are — and the app simply opens the
     * restored one from then on. Restoring next to the original (instead of
     * inside {@code backups/}) keeps the copies of the restored database in the
     * same folder as the rest, not in a {@code backups/backups} nesting.</p>
     */
    public static Path restoreNextTo(Path backup, Path damagedDatabase) {
        Objects.requireNonNull(backup, "backup");
        Objects.requireNonNull(damagedDatabase, "damagedDatabase");
        Path directory = damagedDatabase.toAbsolutePath().normalize().getParent();
        if (directory == null) {
            directory = Path.of(".");
        }
        String name = damagedDatabase.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String stamp = STAMP.format(LocalDateTime.now());
        Path target = directory.resolve(base + "-restored-" + stamp + SUFFIX);
        int counter = 2;
        while (Files.exists(target)) {
            target = directory.resolve(base + "-restored-" + stamp + "-" + counter + SUFFIX);
            counter++;
        }
        try {
            Files.copy(backup, target);
        } catch (IOException e) {
            throw new DataAccessException("Could not restore " + backup + " next to "
                    + damagedDatabase, e);
        }
        return target;
    }

    /** Deletes every copy beyond {@link #KEEP}. */
    private void prune() {
        for (Path doomed : toDelete(list(), KEEP)) {
            try {
                Files.deleteIfExists(doomed);
            } catch (IOException ignored) {
                // A copy that cannot be deleted is not a reason to fail the open.
            }
        }
    }

    /**
     * Pure rotation policy: which of the given copies to delete to keep only the
     * newest {@code keep}, <b>oldest first</b> (the order they are pruned in).
     * Order is by name (timestamp, then counter), foreign files are ignored.
     */
    static List<Path> toDelete(List<Path> existing, int keep) {
        List<Path> newestFirst = existing.stream()
                .filter(BackupService::isBackup)
                .sorted(Comparator.comparing(BackupService::sortKey).reversed())
                .toList();
        if (newestFirst.size() <= keep) {
            return List.of();
        }
        List<Path> doomed = new ArrayList<>(newestFirst.subList(keep, newestFirst.size()));
        java.util.Collections.reverse(doomed);
        return List.copyOf(doomed);
    }

    /**
     * Checks a database file without opening it for work.
     *
     * <p>{@code PRAGMA integrity_check} walks the whole file and reports
     * problems (index/table mismatches, malformed pages); a file that is not a
     * database at all fails with a driver error instead, which is just as
     * decisive.</p>
     */
    public static Integrity integrity(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return Integrity.UNREADABLE;
        }
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + file.toAbsolutePath())) {
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("PRAGMA integrity_check")) {
                boolean ok = rows.next() && "ok".equalsIgnoreCase(rows.getString(1));
                return ok ? Integrity.OK : Integrity.CORRUPT;
            }
        } catch (SQLException e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
            boolean notADatabase = message.contains("not a database")
                    || message.contains("malformed")
                    || message.contains("encrypted")
                    || message.contains("corrupt");
            return notADatabase ? Integrity.CORRUPT : Integrity.UNREADABLE;
        }
    }

    private Path nextFreeName(Path directory, LocalDateTime moment) {
        String stamp = STAMP.format(moment);
        Path candidate = directory.resolve(PREFIX + stamp + SUFFIX);
        int counter = 2;
        while (Files.exists(candidate)) {
            candidate = directory.resolve(PREFIX + stamp + "-" + counter + SUFFIX);
            counter++;
        }
        return candidate;
    }

    private static boolean isBackup(Path file) {
        return file != null && file.getFileName() != null
                && NAME.matcher(file.getFileName().toString()).matches();
    }

    /** Sort key: the timestamp in the name, then the same-second counter. */
    private static String sortKey(Path file) {
        Matcher matcher = NAME.matcher(file.getFileName().toString());
        if (!matcher.matches()) {
            return "";
        }
        String counter = matcher.group(2) == null ? "" : String.format("%04d",
                Integer.parseInt(matcher.group(2)));
        return matcher.group(1) + "-" + counter;
    }

    /** Copies of one database, newest first (read-only helper for the UI/docs). */
    public static List<Path> listOf(Path databaseFile) {
        return new BackupService(databaseFile).list();
    }

    /** Backup file names of a folder, sorted the way the policy sees them. */
    static List<String> names(List<Path> files) {
        List<String> names = new ArrayList<>();
        for (Path file : files) {
            names.add(file.getFileName().toString());
        }
        return names;
    }
}
