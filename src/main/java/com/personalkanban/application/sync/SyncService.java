package com.personalkanban.application.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalkanban.application.BoardJsonMapper;
import com.personalkanban.application.BoardService;
import com.personalkanban.application.port.ConflictCopyStore;
import com.personalkanban.application.port.SettingsStore;
import com.personalkanban.application.port.SyncRepository;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reconciles the active board with the sync server (design §4/§4.1). The
 * cycle is deliberately simple because the app is single-author:
 *
 * <ol>
 *   <li>fetch the remote snapshot;</li>
 *   <li>no base yet and no remote board → push local (new board);</li>
 *   <li>base present and local unchanged → fast-forward to remote (no push);</li>
 *   <li>base present and remote unchanged → push local under the remote
 *       version;</li>
 *   <li>both changed (or first sync with an existing remote) → merge
 *       three-way, apply the merge locally, then push it.</li>
 * </ol>
 *
 * <p>The "base" is the last snapshot both sides agreed on, stored per board
 * in the settings ({@code sync.base.<boardId>}); it is what makes the
 * three-way merge and delete detection possible without per-field
 * timestamps. A losing push (409) means another device wrote in between: the
 * loop re-fetches and retries, bounded by {@link #MAX_ATTEMPTS}, and never
 * forces (forcing would clobber the other device).</p>
 *
 * <p>Applying a merged snapshot must not enter the local undo history
 * (design §7: undo is local and never synchronized), which is why it goes
 * through {@link BoardService#applySynced(BoardMemento)} rather than any
 * command.</p>
 */
public final class SyncService {

    private static final String BASE_PREFIX = "sync.base.";
    private static final int MAX_ATTEMPTS = 3;

    private final SyncRepository repository;
    private final BoardService boardService;
    private final SettingsStore settings;
    private final ConflictCopyStore copyStore;
    private final ObjectMapper mapper;

    public SyncService(SyncRepository repository, BoardService boardService,
                       SettingsStore settings, ConflictCopyStore copyStore) {
        this.repository = Objects.requireNonNull(repository);
        this.boardService = Objects.requireNonNull(boardService);
        this.settings = Objects.requireNonNull(settings);
        this.copyStore = Objects.requireNonNull(copyStore);
        this.mapper = BoardJsonMapper.create();
    }

    /** Synchronizes the active board; throws {@link SyncException} on failure. */
    public SyncReport syncActiveBoard() {
        BoardId boardId = boardService.activeBoardId();
        String name = boardService.activeBoardName();
        Optional<BoardMemento> savedBase = loadBase(boardId);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            BoardMemento local = boardService.snapshotOf();
            Optional<RemoteBoard> remoteOpt = repository.fetch(boardId);

            if (remoteOpt.isEmpty()) {
                PushResult result = repository.push(boardId, name, local, 0, false);
                if (result instanceof PushResult.Ok) {
                    saveBase(boardId, local);
                    return new SyncReport(name, SyncReport.Action.PUSHED_NEW,
                            List.of(), Optional.empty());
                }
                continue; // another device created it meanwhile: re-fetch and merge
            }

            RemoteBoard remote = remoteOpt.get();
            BoardMemento base = savedBase.orElse(BoardMemento.empty());
            boolean haveBase = savedBase.isPresent();

            if (haveBase && same(local, base)) {
                apply(boardId, remote.memento());
                saveBase(boardId, remote.memento());
                return new SyncReport(name, SyncReport.Action.FAST_FORWARD,
                        List.of(), Optional.empty());
            }

            if (haveBase && same(remote.memento(), base)) {
                PushResult result = repository.push(boardId, name, local, remote.version(), false);
                if (result instanceof PushResult.Ok) {
                    saveBase(boardId, local);
                    return new SyncReport(name, SyncReport.Action.PUSHED_LOCAL,
                            List.of(), Optional.empty());
                }
                continue; // lost the race: retry with the fresh remote
            }

            MergeOutcome outcome = SyncMerge.merge(base, local, remote.memento());
            apply(boardId, outcome.merged());
            PushResult result = repository.push(boardId, name, outcome.merged(),
                    remote.version(), false);
            if (result instanceof PushResult.Ok) {
                saveBase(boardId, outcome.merged());
                Optional<String> copyReference = Optional.empty();
                if (outcome.hasConflicts()) {
                    copyReference = copyStore.save(boardId, name, remote.memento(),
                            outcome.conflicts(), Instant.now());
                }
                return new SyncReport(name, SyncReport.Action.MERGED,
                        outcome.conflicts(), copyReference);
            }
            // Lost the race after merging; the merged snapshot stays applied
            // locally and the next attempt merges against the newer remote.
        }

        throw new SyncException(SyncException.Kind.SERVER, 0,
                "Sync did not converge after " + MAX_ATTEMPTS + " attempts");
    }

    // ------------------------------------------------------------------
    // Base snapshot + application helpers
    // ------------------------------------------------------------------

    /** Applies a snapshot outside the undo history and persists it. */
    private void apply(BoardId boardId, BoardMemento memento) {
        boardService.applySynced(memento);
    }

    private Optional<BoardMemento> loadBase(BoardId boardId) {
        return settings.get(BASE_PREFIX + boardId.value())
                .filter(value -> !value.isBlank())
                .flatMap(this::parse);
    }

    private void saveBase(BoardId boardId, BoardMemento memento) {
        try {
            settings.put(BASE_PREFIX + boardId.value(), mapper.writeValueAsString(memento));
        } catch (JsonProcessingException e) {
            // Never fail a successful sync over bookkeeping; the next run
            // simply merges from an empty base.
        }
    }

    private Optional<BoardMemento> parse(String json) {
        try {
            return Optional.ofNullable(mapper.readValue(json, BoardMemento.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    /** Canonical JSON equality: deterministic regardless of list order. */
    private boolean same(BoardMemento a, BoardMemento b) {
        try {
            return mapper.writeValueAsString(a).equals(mapper.writeValueAsString(b));
        } catch (JsonProcessingException e) {
            return false;
        }
    }
}
