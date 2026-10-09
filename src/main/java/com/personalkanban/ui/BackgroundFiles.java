package com.personalkanban.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Keeps the board background image inside the program's data directory
 * ({@code ~/.personalkanban/background/}, next to the database and the
 * preferences file — or {@code <pk.data.dir>/background/} in portable mode).
 *
 * <p>The preference stores an absolute path, so pointing at a file in
 * Downloads (or a removable drive) breaks silently whenever that file moves
 * or is cleaned up: the board then falls back to the plain color. Importing
 * a copy makes the preference self-contained, and {@link #repairSpec} can
 * even resurrect a previously imported image after the original is gone.</p>
 *
 * <p>All methods are pure file operations (no JavaFX), so they are unit
 * testable. Copy failures never throw: the background is cosmetic and must
 * not block the app — the original spec is returned unchanged instead.</p>
 */
final class BackgroundFiles {

    /** Sub-directory of {@code Main.dataDirectory()} that holds the copy. */
    static final String DIRECTORY_NAME = "background";

    private BackgroundFiles() {
    }

    /** The directory that holds the app's background copy (may not exist yet). */
    static Path directory(Path dataDirectory) {
        return dataDirectory.resolve(DIRECTORY_NAME);
    }

    /**
     * Copies the image of a {@code "path|dim"} spec into the program's data
     * directory and returns the spec pointing at that copy. Returns the spec
     * unchanged when it is null/blank, the file is missing, the file already
     * lives inside the data directory, or the copy fails.
     */
    static String importSpec(String spec, Path dataDirectory) {
        if (spec == null || spec.isBlank()) {
            return spec;
        }
        String[] parts = spec.split("\\|", 2);
        String suffix = parts.length == 2 ? "|" + parts[1] : "";
        File file = new File(parts[0]);
        if (!file.isFile()) {
            return spec;
        }
        try {
            Path source = file.toPath().toAbsolutePath().normalize();
            Path directory = directory(dataDirectory).toAbsolutePath().normalize();
            if (source.startsWith(directory)) {
                return spec; // already imported
            }
            Files.createDirectories(directory);
            Path target = directory.resolve(source.getFileName().toString());
            if (!source.equals(target)) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target + suffix;
        } catch (IOException | RuntimeException e) {
            return spec; // cosmetic preference: never block the app
        }
    }

    /**
     * Startup repair of a stored spec: imports the image when it still exists
     * outside the data directory (one-time migration), and points back at the
     * local copy when the original file disappeared (Downloads cleaned up,
     * drive unplugged). Returns the spec unchanged when there is nothing to
     * fix; compare with {@code Objects.equals} to decide whether to persist.
     */
    static String repairSpec(String spec, Path dataDirectory) {
        if (spec == null || spec.isBlank()) {
            return spec;
        }
        String[] parts = spec.split("\\|", 2);
        String suffix = parts.length == 2 ? "|" + parts[1] : "";
        File file = new File(parts[0]);
        if (file.isFile()) {
            return importSpec(spec, dataDirectory);
        }
        // Original missing: fall back to a copy with the same file name.
        Path local = directory(dataDirectory).resolve(
                new File(parts[0]).getName());
        if (Files.isRegularFile(local)) {
            return local + suffix;
        }
        return spec;
    }
}
