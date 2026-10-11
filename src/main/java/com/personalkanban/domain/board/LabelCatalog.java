package com.personalkanban.domain.board;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Global label vocabulary: the set of labels a user can place on any card of
 * any board, plus the small reserved system set managed by the app itself.
 * <p>
 * Labels here are <em>plain names</em>, not per-board occurrences. A label on
 * a card only has meaning when it is present in the catalog; the catalog is
 * where rename/remove/add semantics live. System labels (urgent/important/
 * archived so far) live here too, but are not removable or renamable through
 * the user-visible label manager.
 * <p>
 * Identity is case-insensitive by design: {@code "urgent"} and {@code "Urgent"}
 * name the same catalog entry and the same on-card chip. The canonical spelling
 * is whatever is currently stored in the catalog for that identity.
 */
public final class LabelCatalog {

    /** Reserved labels the application owns; they cannot be renamed or removed. */
    public static final Set<String> RESERVED = Set.of(
            Card.LABEL_IMPORTANT,
            Card.LABEL_URGENT,
            Card.LABEL_ARCHIVED);

    private final Set<CatalogEntry> labels;

    /** Empty catalog seeded only with the reserved system labels. */
    public LabelCatalog() {
        this.labels = new LinkedHashSet<>();
        for (String reserved : RESERVED) {
            labels.add(new CatalogEntry(reserved));
        }
    }

    LabelCatalog(Set<CatalogEntry> labels) {
        this.labels = List.copyOf(labels).stream()
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    /**
     * Canonical, non-null label names in creation order (system labels first).
     */
    public List<String> names() {
        List<String> names = new java.util.ArrayList<>(labels.size());
        for (CatalogEntry entry : labels) {
            names.add(entry.canonical);
        }
        return names;
    }

    /**
     * True when {@code name} identifies a label in the catalog (case-insensitive).
     */
    public boolean has(String name) {
        if (name == null) {
            return false;
        }
        String normalized = normalize(name);
        return labels.stream().anyMatch(e -> e.canonical.equalsIgnoreCase(normalized));
    }

    /**
     * Canonical spelling of the label identified by {@code name}, or null when
     * {@code name} is not in the catalog.
     */
    public String canonical(String name) {
        if (name == null) {
            return null;
        }
        String normalized = normalize(name);
        return labels.stream()
                .filter(e -> e.canonical.equalsIgnoreCase(normalized))
                .map(e -> e.canonical)
                .findFirst()
                .orElse(null);
    }

    /**
     * All entries matching {@code name} (case-insensitive). Usually a single
     * element; the set form mirrors the internal representation.
     */
    public Set<CatalogEntry> entriesFor(String name) {
        if (name == null) {
            return Set.of();
        }
        String normalized = normalize(name);
        Set<CatalogEntry> result = new LinkedHashSet<>();
        for (CatalogEntry entry : labels) {
            if (entry.canonical.equalsIgnoreCase(normalized)) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Adds a new user label (not reserved, not blank, unique ignoring case).
     * Reserved system labels are never added through the catalog API — they are
     * bootstrapped in the constructor.
     */
    public void add(String name) {
        Objects.requireNonNull(name, "label name");
        String normalized = normalize(name);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Label name must not be blank");
        }
        if (RESERVED.contains(normalized)) {
            throw new IllegalArgumentException("System label cannot be added: " + name);
        }
        if (labels.stream().anyMatch(e -> e.canonical.equalsIgnoreCase(normalized))) {
            throw new IllegalArgumentException("Label already exists: " + name);
        }
        if (normalized.length() > Card.MAX_LABEL_LENGTH) {
            throw new IllegalArgumentException(
                    "Label too long (" + normalized.length() + " chars, max " + Card.MAX_LABEL_LENGTH + "): "
                            + normalized);
        }
        labels.add(new CatalogEntry(normalized));
    }

    /**
     * Renames the label identified by {@code currentName} to {@code newName}.
     * Reserved labels are not renamable. {@code newName} must not collide (ignoring
     * case) with any existing label, including itself when renamed to a different
     * spelling of the same identity.
     */
    public void rename(String currentName, String newName) {
        Objects.requireNonNull(currentName, "current");
        Objects.requireNonNull(newName, "target");
        String normalizedCurrent = normalize(currentName);
        String normalizedNew = normalize(newName);
        if (normalizedCurrent.isEmpty()) {
            throw new IllegalArgumentException("Current label must not be blank");
        }
        if (normalizedNew.isEmpty()) {
            throw new IllegalArgumentException("New label must not be blank");
        }
        CatalogEntry existing = findOne(normalizedCurrent)
                .orElseThrow(() -> new IllegalArgumentException("Label not found: " + currentName));
        if (RESERVED.contains(existing.canonical)) {
            throw new IllegalArgumentException("System label cannot be renamed: " + currentName);
        }
        Kind kind = CatalogEntry.kindFor(normalizedNew);
        if (kind == Kind.RESERVED) {
            throw new IllegalArgumentException("Cannot rename to a reserved system label: " + newName);
        }
        if (labels.stream().anyMatch(other ->
                !other.equals(existing) && other.canonical.equalsIgnoreCase(normalizedNew))) {
            throw new IllegalArgumentException("Another label already exists with that name: " + newName);
        }
        labels.remove(existing);
        labels.add(new CatalogEntry(normalizedNew, kind));
    }

    /**
     * Removes the label identified by {@code name} from the catalog. Reserved labels
     * are not removable. The label is removed even when cards still carry it; those
     * cards are cleaned up separately through the aggregate/service that coordinates
     * the catalog with the boards.
     */
    public void remove(String name) {
        Objects.requireNonNull(name, "label name");
        String normalized = normalize(name);
        CatalogEntry entry = findOne(normalized).orElseThrow(
                () -> new IllegalArgumentException("Label not found: " + name));
        if (RESERVED.contains(entry.canonical)) {
            throw new IllegalArgumentException("System label cannot be removed: " + name);
        }
        labels.remove(entry);
    }

    /**
     * Cleans the given card label set so it only contains labels that still exist
     * in the catalog. Cards never hold a label absent from the catalog after a
     * catalog change completes. Unknown labels are silently dropped (they are
     * already in the past tense — a renamed/removed entry).
     */
    public List<String> cleanCardLabels(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        Set<String> cleaned = new LinkedHashSet<>();
        for (String label : raw) {
            String canonical = canonical(label.strip());
            if (canonical != null) {
                cleaned.add(canonical);
            }
        }
        return List.copyOf(cleaned);
    }

    /**
     * Canonical names of the user-defined labels (reserved system labels
     * excluded), in creation order.
     */
    public List<String> userLabels() {
        List<String> user = new java.util.ArrayList<>();
        for (CatalogEntry entry : labels) {
            if (!entry.isReserved()) {
                user.add(entry.canonical);
            }
        }
        return List.copyOf(user);
    }

    /**
     * True when the catalog is empty of user labels (only reserved labels remain).
     */
    public boolean hasUserLabels() {
        return labels.stream().anyMatch(e -> !e.isReserved());
    }

    /** Number of user-defined labels. */
    public int userLabelCount() {
        int count = 0;
        for (CatalogEntry entry : labels) {
            if (!entry.isReserved()) {
                count++;
            }
        }
        return count;
    }

    private Optional<CatalogEntry> findOne(String normalized) {
        return labels.stream()
                .filter(e -> e.canonical.equalsIgnoreCase(normalized))
                .findFirst();
    }

    private static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String stripped = name.strip();
        return stripped.isEmpty() ? "" : stripped;
    }

    /** One entry in the global label catalog. */
    public record CatalogEntry(String canonical, Kind kind) {

        CatalogEntry(String canonical) {
            this(canonical, CatalogEntry.kindFor(canonical));
        }

        /** Reserved labels are the ones defined by the application itself. */
        public boolean isReserved() {
            return RESERVED.contains(canonical);
        }

        static Kind kindFor(String canonical) {
            if (RESERVED.contains(canonical)) {
                return Kind.RESERVED;
            }
            return Kind.USER;
        }

        @Override
        public String toString() {
            return canonical + (isReserved() ? " [system]" : " [user]");
        }
    }

    /** Who owns a label identity in this catalog. */
    public enum Kind {
        RESERVED,
        USER
    }
}
