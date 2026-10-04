package com.personalkanban.application;

import com.personalkanban.application.port.InMemoryBoardRepository;
import com.personalkanban.application.port.InMemorySettingsStore;
import com.personalkanban.application.port.InMemoryUndoHistory;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Service-level tests for the session-6 time-tracking use cases. */
class SessionSixServiceTest {

    private final InMemoryBoardRepository repository = new InMemoryBoardRepository();
    private final InMemoryUndoHistory history = new InMemoryUndoHistory();
    private final InMemorySettingsStore settings = new InMemorySettingsStore();
    private final BoardService service = new BoardService(repository, history, settings);

    private CardId newCard() {
        BoardId board = service.createBoard("Session 6");
        service.openBoard(board);
        var columnId = service.addColumn("To Do", "", BoardColor.DEFAULT, WipLimit.unlimited());
        return service.addCard(columnId, "task", "", BoardColor.DEFAULT);
    }

    @Test
    void startAndStopTrackTimeAndAreUndoable() {
        CardId cardId = newCard();

        service.startTimeTracking(cardId);
        assertThat(service.isTracking(cardId)).isTrue();
        assertThat(service.timeEntriesOf(cardId)).hasSize(1);

        service.stopTimeTracking(cardId);
        assertThat(service.isTracking(cardId)).isFalse();
        assertThat(service.timeEntriesOf(cardId).get(0).isClosed()).isTrue();

        service.undo(); // undo the stop → running again
        assertThat(service.isTracking(cardId)).isTrue();
    }

    @Test
    void commentIsStoredAndBlankIsIgnored() {
        CardId cardId = newCard();
        service.startTimeTracking(cardId);

        service.commentRunningTimeEntry(cardId, "  Iniciando el registro  ");
        assertThat(service.timeEntriesOf(cardId).get(0).comment()).isEqualTo("Iniciando el registro");

        service.commentRunningTimeEntry(cardId, "   ");
        assertThat(service.timeEntriesOf(cardId).get(0).comment()).isEmpty();
    }

    @Test
    void removeEntryIsUndoable() {
        CardId cardId = newCard();
        service.startTimeTracking(cardId);
        service.stopTimeTracking(cardId);
        var entryId = service.timeEntriesOf(cardId).get(0).id();

        assertThat(service.removeTimeEntry(cardId, entryId)).isTrue();
        assertThat(service.timeEntriesOf(cardId)).isEmpty();

        service.undo();
        assertThat(service.timeEntriesOf(cardId)).hasSize(1);
    }

    @Test
    void removingAnUnknownEntryIsANoOp() {
        CardId cardId = newCard();
        assertThat(service.removeTimeEntry(cardId,
                com.personalkanban.domain.board.EntryId.newId())).isFalse();
    }
}
