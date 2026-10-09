package com.personalkanban.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Per-card reference files (documentation, images, PDFs...), kept as plain
 * files under {@code <data dir>/attachments/<card id>/}. Nothing is stored in
 * the database: the card id names the folder, so the files travel with the
 * data directory (and with the portable-mode folder) and can be dropped there
 * directly by the user.
 *
 * <p>All methods are pure file operations (no JavaFX), so they are unit
 * testable, and they never throw for a missing folder — an empty card simply
 * has no attachments.</p>
 */
final class AttachmentStore {

    /** Sub-directory of {@code Main.dataDirectory()} holding every card folder. */
    static final String DIRECTORY_NAME = "attachments";

    private AttachmentStore() {
    }

    /** The attachments folder of one card (may not exist yet). */
    static Path directory(Path dataDirectory, String cardId) {
        return dataDirectory.resolve(DIRECTORY_NAME).resolve(sanitize(cardId));
    }

    /** Attachments of a card, sorted by file name (empty when none exist). */
    static List<Path> list(Path dataDirectory, String cardId) {
        Path directory = directory(dataDirectory, cardId);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(
                            path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT)))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Number of attachments of a card (used by the card badge). */
    static int count(Path dataDirectory, String cardId) {
        return list(dataDirectory, cardId).size();
    }

    /**
     * Copies a chosen file into the card folder, never overwriting an existing
     * attachment: a duplicate name gets a {@code (2)}, {@code (3)}... suffix.
     * Returns the stored file, or null when the copy fails.
     */
    static Path importFile(Path dataDirectory, String cardId, Path source) {
        if (source == null || !Files.isRegularFile(source)) {
            return null;
        }
        try {
            Path directory = directory(dataDirectory, cardId);
            Files.createDirectories(directory);
            Path target = uniqueTarget(directory,
                    source.getFileName() == null ? "archivo" : source.getFileName().toString());
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
            return target;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Deletes one attachment; returns false when it could not be removed. */
    static boolean delete(Path file) {
        if (file == null) {
            return false;
        }
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            return false;
        }
    }

    /** {@code name.ext} -> {@code name (2).ext} when the name is taken. */
    private static Path uniqueTarget(Path directory, String fileName) {
        Path candidate = directory.resolve(fileName);
        if (!Files.exists(candidate)) {
            return candidate;
        }
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        for (int suffix = 2; suffix < 1000; suffix++) {
            candidate = directory.resolve(base + " (" + suffix + ")" + extension);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        return directory.resolve(base + "-" + System.nanoTime() + extension);
    }

    /** Keeps a card id safe as a single folder name on every OS. */
    private static String sanitize(String cardId) {
        if (cardId == null || cardId.isBlank()) {
            return "desconocida";
        }
        return cardId.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
