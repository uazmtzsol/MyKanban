package com.personalkanban.application.sync;

import com.personalkanban.application.BoardService;
import com.personalkanban.application.port.InMemoryBoardRepository;
import com.personalkanban.application.port.InMemorySettingsStore;
import com.personalkanban.application.port.InMemorySyncRepository;
import com.personalkanban.application.port.InMemoryUndoHistory;
import com.personalkanban.application.port.RecordingConflictCopyStore;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.BoardMemento;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.CardSnapshot;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.ColumnSnapshot;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SyncServiceTest {

    private final InMemoryBoardRepository repository = new InMemoryBoardRepository();
    private final InMemoryUndoHistory history = new InMemoryUndoHistory();
    private final InMemorySettingsStore settings = new InMemorySettingsStore();
    private final BoardService service = new BoardService(repository, history, settings);
    private final InMemorySyncRepository remote = new InMemorySyncRepository();
    private final RecordingConflictCopyStore copies = new RecordingConflictCopyStore();
    private final SyncService sync = new SyncService(remote, service, settings, copies);

    private BoardId boardId;
    private ColumnId columnId;
    private CardId cardId;

    private void freshBoardWithCard() {
        boardId = service.createBoard("Sync Test");
        service.openBoard(boardId);
        columnId = service.addColumn("Todo", "", BoardColor.BLUE, WipLimit.unlimited());
        cardId = service.addCard(columnId, "Card", "desc", BoardColor.BLUE);
    }

    @Test
    void firstSyncUploadsTheNewBoardAndStoresTheBase() {
        freshBoardWithCard();

        SyncReport report = sync.syncActiveBoard();

        assertThat(report.action()).isEqualTo(SyncReport.Action.PUSHED_NEW);
        assertThat(remote.fetch(boardId)).isPresent();
        assertThat(settings.get("sync.base." + boardId.value())).isPresent();
    }

    @Test
    void localEditPushesWhenTheRemoteDidNotMove() {
        freshBoardWithCard();
        sync.syncActiveBoard(); // establishes the base

        service.editCard(cardId, "Renamed locally", "desc", BoardColor.BLUE);
        int pushesBefore = remote.pushes;

        SyncReport report = sync.syncActiveBoard();

        assertThat(report.action()).isEqualTo(SyncReport.Action.PUSHED_LOCAL);
        assertThat(remote.pushes).isEqualTo(pushesBefore + 1);
        assertThat(remote.fetch(boardId).orElseThrow().memento().columns().get(0).cards())
                .extracting(CardSnapshot::title).containsExactly("Renamed locally");
    }

    @Test
    void remoteAheadFastForwardsWithoutPushingWhenLocalIsUnchanged() {
        freshBoardWithCard();
        sync.syncActiveBoard();
        int pushesBefore = remote.pushes;

        remote.putRemote(boardId, "Sync Test", withExtraColumn(service.snapshotOf()));

        SyncReport report = sync.syncActiveBoard();

        assertThat(report.action()).isEqualTo(SyncReport.Action.FAST_FORWARD);
        assertThat(remote.pushes).isEqualTo(pushesBefore);
        assertThat(service.board().columns()).hasSize(2);
    }

    @Test
    void bothChangedMergesAndPreservesAConflictCopy() {
        freshBoardWithCard();
        sync.syncActiveBoard();

        service.editCard(cardId, "local", "desc", BoardColor.BLUE);
        remote.putRemote(boardId, "Sync Test", withCardTitle(service.snapshotOf(), cardId, "remote"));

        SyncReport report = sync.syncActiveBoard();

        assertThat(report.action()).isEqualTo(SyncReport.Action.MERGED);
        assertThat(report.hasConflicts()).isTrue();
        assertThat(copies.saves).isEqualTo(1);
        assertThat(copies.lastConflicts).isNotEmpty();
        // The losing (remote) side is what gets preserved.
        assertThat(copies.lastSnapshot.columns().get(0).cards())
                .extracting(CardSnapshot::title).containsExactly("remote");
        // The merge is applied locally, deterministically.
        assertThat(service.board().findCard(cardId).orElseThrow().title()).isEqualTo("remote");
    }

    @Test
    void syncDoesNotPolluteTheUndoHistory() {
        freshBoardWithCard();
        sync.syncActiveBoard();

        int undoDepthBefore = history.depth(boardId);
        remote.putRemote(boardId, "Sync Test", withExtraColumn(service.snapshotOf()));
        sync.syncActiveBoard(); // fast-forward applies remote state

        assertThat(history.depth(boardId)).isEqualTo(undoDepthBefore);
    }

    // ------------------------------------------------------------------
    // Memento mutators for building the "remote" side
    // ------------------------------------------------------------------

    private static BoardMemento withExtraColumn(BoardMemento source) {
        List<ColumnSnapshot> columns = new ArrayList<>(source.columns());
        columns.add(new ColumnSnapshot(new ColumnId("remote-column"), "Remote", "",
                BoardColor.GREEN, WipLimit.unlimited(), Instant.now(), false, null, List.of()));
        return new BoardMemento(columns, source.processes(), source.links(), source.timeline());
    }

    private static BoardMemento withCardTitle(BoardMemento source, CardId cardId, String title) {
        List<ColumnSnapshot> columns = new ArrayList<>();
        for (ColumnSnapshot column : source.columns()) {
            List<CardSnapshot> cards = new ArrayList<>();
            for (CardSnapshot card : column.cards()) {
                if (card.id().equals(cardId)) {
                    cards.add(new CardSnapshot(card.id(), title, card.description(), card.color(),
                            card.dueDate(), card.labels(), card.createdAt(), card.notes(),
                            card.checklist(), card.processId()));
                } else {
                    cards.add(card);
                }
            }
            columns.add(new ColumnSnapshot(column.id(), column.title(), column.description(),
                    column.color(), column.wipLimit(), column.createdAt(), column.done(),
                    column.backgroundColor(), cards));
        }
        return new BoardMemento(columns, source.processes(), source.links(), source.timeline());
    }
}
