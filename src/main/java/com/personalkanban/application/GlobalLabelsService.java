package com.personalkanban.application;

import com.personalkanban.application.port.LabelCatalogStore;
import com.personalkanban.domain.board.BoardDescriptor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;
import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.CardSnapshot;
import com.personalkanban.domain.board.ColumnSnapshot;
import com.personalkanban.domain.board.LabelCatalog;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Application service for the <em>global</em> label vocabulary.
 *
 * <p>Labels are global: the catalog is a single per-database value
 * ({@link LabelCatalog}, persisted through {@link LabelCatalogStore}) and every
 * board's cards draw from it. This service owns the three user-facing
 * operations — add, rename, remove — and guarantees that a rename or a removal
 * also sweeps the labels of <em>every</em> stored board, so no card keeps
 * carrying a label that no longer exists under that name.</p>
 *
 * <p>The three reserved system labels ({@link Card#LABEL_URGENT},
 * {@link Card#LABEL_IMPORTANT}, {@link Card#LABEL_ARCHIVED}) live in the
 * catalog too, but they cannot be renamed or removed through this service; the
 * UI shows them as locked.</p>
 *
 * <p>These operations are deliberately not undoable per board: they are global
 * edits authored in one confirmation dialog, exactly like "delete board".</p>
 */
public final class GlobalLabelsService {

    private final BoardService boardService;
    private final LabelCatalogStore store;
    private LabelCatalog catalog;

    public GlobalLabelsService(BoardService boardService, LabelCatalogStore store) {
        this.boardService = Objects.requireNonNull(boardService, "boardService");
        this.store = Objects.requireNonNull(store, "store");
        LabelCatalog loaded = store.load();
        this.catalog = loaded == null ? new LabelCatalog() : loaded;
        adoptLabelsUsedOnBoards();
    }

    // ------------------------------------------------------------------
    // Catalog reads
    // ------------------------------------------------------------------

    /** Current global catalog (canonical names, system labels first). */
    public LabelCatalog catalog() {
        return catalog;
    }

    /** Every label name in the catalog, in storage order. */
    public List<String> names() {
        return catalog.names();
    }

    /** User-defined labels only (no reserved system labels). */
    public List<String> userLabels() {
        return catalog.userLabels();
    }

    /** The reserved system labels; never removable nor renamable. */
    public Set<String> reservedLabels() {
        return LabelCatalog.RESERVED;
    }

    /** True when {@code label} names one of the reserved system labels. */
    public boolean isReserved(String label) {
        return label != null && reservedLabels().contains(label.strip());
    }

    /**
     * Reserved system labels in their documented order (urgent, important,
     * archived) — the order the quick-flag UI already uses.
     */
    public List<String> systemLabels() {
        return List.of(Card.LABEL_URGENT, Card.LABEL_IMPORTANT, Card.LABEL_ARCHIVED);
    }

    /**
     * Autocomplete vocabulary offered on the active board: reserved labels
     * first (fixed order), then every global label, minus the ones in
     * {@code exclude} (typically the labels the card being edited already
     * carries). Case-insensitive dedup keeping the first-seen spelling.
     */
    public List<String> vocabularyForActiveBoard(List<String> exclude) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> vocabulary = new ArrayList<>();
        List<String> excludeLower = new ArrayList<>();
        if (exclude != null) {
            for (String value : exclude) {
                excludeLower.add(value.toLowerCase(Locale.ROOT));
            }
        }
        List<List<String>> groups = List.of(systemLabels(), catalog.names(),
                activeBoardLabelsInUse());
        for (List<String> group : groups) {
            for (String label : group) {
                String key = label.toLowerCase(Locale.ROOT);
                if (excludeLower.contains(key) || !seen.add(key)) {
                    continue;
                }
                vocabulary.add(label);
            }
        }
        return List.copyOf(vocabulary);
    }

    // ------------------------------------------------------------------
    // Global label lifecycle
    // ------------------------------------------------------------------

    /**
     * Adds a new global label to the catalog and persists it. The label becomes
     * selectable on any card of any board immediately. Blank, duplicate,
     * reserved and over-long names are rejected by {@link LabelCatalog#add}.
     */
    public void addLabel(String name) {
        catalog.add(name);
        store.save(catalog);
    }

    /**
     * Renames a global label: the catalog entry first, then every card of every
     * stored board that carries the old identity. Reserved labels cannot be
     * renamed ({@link LabelCatalog#rename} rejects them).
     */
    public void renameLabel(String currentName, String newName) {
        catalog.rename(currentName, newName);
        store.save(catalog);
        String canonical = catalog.canonical(newName);
        sweepAllBoards(labels -> renamed(labels, currentName, canonical));
        reloadActiveBoard();
    }

    /**
     * Removes a global label from the catalog and from every card of every
     * stored board. Reserved labels are rejected.
     */
    public void removeLabel(String name) {
        if (isReserved(name)) {
            throw new IllegalArgumentException("System label cannot be removed: " + name);
        }
        catalog.remove(name);
        store.save(catalog);
        sweepAllBoards(labels -> without(labels, name));
        reloadActiveBoard();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * Adopts labels that already exist on stored cards into the catalog
     * (databases created before the global registry keep their labels
     * manageable). Reserved and invalid names are skipped silently.
     */
    private void adoptLabelsUsedOnBoards() {
        boolean changed = false;
        for (BoardDescriptor descriptor : boardService.boards()) {
            BoardMemento memento = boardService.loadStored(descriptor.id());
            for (ColumnSnapshot column : memento.columns()) {
                for (CardSnapshot card : column.cards()) {
                    for (String label : card.labels()) {
                        if (tryAdopt(label)) {
                            changed = true;
                        }
                    }
                }
            }
        }
        if (changed) {
            store.save(catalog);
        }
    }

    private boolean tryAdopt(String label) {
        try {
            catalog.add(label);
            return true;
        } catch (IllegalArgumentException rejected) {
            // Reserved, duplicate (case-insensitive), blank or too long: the
            // catalog already represents that identity or cannot take it.
            return false;
        }
    }

    /**
     * Rewrites the labels of every card on every stored board through
     * {@code transform} and persists only the boards that actually changed.
     */
    private void sweepAllBoards(UnaryOperator<List<String>> transform) {
        for (BoardDescriptor descriptor : boardService.boards()) {
            BoardMemento memento = boardService.loadStored(descriptor.id());
            boolean boardChanged = false;
            List<ColumnSnapshot> columns = new ArrayList<>(memento.columns().size());
            for (ColumnSnapshot column : memento.columns()) {
                List<CardSnapshot> cards = new ArrayList<>(column.cards().size());
                boolean columnChanged = false;
                for (CardSnapshot card : column.cards()) {
                    List<String> after = transform.apply(card.labels());
                    if (after.equals(card.labels())) {
                        cards.add(card);
                    } else {
                        columnChanged = true;
                        cards.add(new CardSnapshot(card.id(), card.title(), card.description(),
                                card.color(), card.dueDate(), after, card.createdAt(),
                                card.notes(), card.checklist(), card.processId()));
                    }
                }
                if (columnChanged) {
                    boardChanged = true;
                    columns.add(new ColumnSnapshot(column.id(), column.title(),
                            column.description(), column.color(), column.wipLimit(),
                            column.createdAt(), column.done(), column.backgroundColor(),
                            cards));
                } else {
                    columns.add(column);
                }
            }
            if (boardChanged) {
                boardService.saveStored(descriptor.id(),
                        new BoardMemento(columns, memento.processes(), memento.links(),
                                memento.timeline()));
            }
        }
    }

    /** Replaces (first occurrence per identity) {@code oldName} with {@code newName}. */
    private static List<String> renamed(List<String> labels, String oldName, String newName) {
        List<String> rebuilt = new ArrayList<>(labels.size());
        boolean replaced = false;
        for (String label : labels) {
            if (!replaced && label.equalsIgnoreCase(oldName)) {
                rebuilt.add(newName);
                replaced = true;
            } else {
                rebuilt.add(label);
            }
        }
        return List.copyOf(rebuilt);
    }

    /** Drops every label whose identity matches {@code name}. */
    private static List<String> without(List<String> labels, String name) {
        List<String> remaining = labels.stream()
                .filter(label -> !label.equalsIgnoreCase(name))
                .toList();
        return List.copyOf(remaining);
    }

    /** Labels in use on the active board (defensive: adoption keeps this empty). */
    private List<String> activeBoardLabelsInUse() {
        List<String> used = new ArrayList<>();
        for (Card card : boardService.board().allCards()) {
            used.addAll(card.labels());
        }
        return used;
    }

    /**
     * Re-hydrates the active board from storage so the live view shows the
     * swept labels; the stored copy is already the swept one.
     */
    private void reloadActiveBoard() {
        BoardId activeId = boardService.activeBoardId();
        boardService.openBoard(activeId);
    }
}
