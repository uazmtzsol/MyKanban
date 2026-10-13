package com.personalkanban.application;

import com.personalkanban.application.port.InMemoryBoardRepository;
import com.personalkanban.application.port.InMemorySettingsStore;
import com.personalkanban.application.port.InMemoryUndoHistory;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User request T14 · B5: user-defined templates (structure only, applied to a
 * brand-new empty board) and the purge of archived cards across boards.
 */
class ArchivedPurgeTest {

    private final InMemoryBoardRepository repository = new InMemoryBoardRepository();
    private final InMemoryUndoHistory history = new InMemoryUndoHistory();
    private final InMemorySettingsStore settings = new InMemorySettingsStore();
    private final BoardService service = new BoardService(repository, history, settings);

    private BoardId board;
    private ColumnId column;

    private void setUp() {
        board = service.createBoard("Origin");
        service.openBoard(board);
        column = service.addColumn("Por hacer", "", BoardColor.BLUE, WipLimit.of(4));
        service.addColumn("Hecho", "", BoardColor.GREEN, WipLimit.unlimited());
        service.setColumnDone(service.board().columns().get(1).id(), true);
        service.addProcess("Desarrollo");
    }

    @Test
    void savedTemplateIsListedAndCanBeRemoved() {
        setUp();

        service.saveAsTemplate("Mi plantilla");
        assertThat(service.hasTemplate("Mi plantilla")).isTrue();
        assertThat(service.templates()).extracting(BoardTemplate::name)
                .containsExactly("Mi plantilla");

        service.removeTemplate("Mi plantilla");
        assertThat(service.hasTemplate("Mi plantilla")).isFalse();
        assertThat(service.templates()).isEmpty();
    }

    @Test
    void aBoardCreatedFromATemplateCopiesStructureButNoCards() {
        setUp();
        service.addCard(column, "no debe viajar", "", BoardColor.DEFAULT);
        service.saveAsTemplate("Reutilizable");

        BoardTemplate template = service.templates().get(0);
        BoardId created = service.createBoardFromTemplate(template, "Nuevo");

        assertThat(service.activeBoardId()).isEqualTo(created);
        assertThat(service.board().columns()).extracting(c -> c.title())
                .containsExactly("Por hacer", "Hecho");
        assertThat(service.board().columns().get(0).wipLimit()).isEqualTo(WipLimit.of(4));
        assertThat(service.board().columns().get(1).isDone()).isTrue();
        assertThat(service.processes()).extracting(p -> p.name()).containsExactly("Desarrollo");
        // Structure only: the new board starts empty.
        assertThat(service.board().allCards()).isEmpty();
    }

    @Test
    void purgeDeletesOnlyArchivedCardsAndCanBeUndone() {
        setUp();
        var keep = service.addCard(column, "activa", "", BoardColor.DEFAULT);
        var archived = service.addCard(column, "vieja", "", BoardColor.DEFAULT);
        service.toggleCardLabel(archived, Card.LABEL_ARCHIVED);

        assertThat(service.archivedCardCount()).isEqualTo(1);

        int removed = service.purgeArchivedCards();

        assertThat(removed).isEqualTo(1);
        assertThat(service.board().allCards()).extracting(Card::title).containsExactly("activa");
        assertThat(service.board().findCard(archived)).isEmpty();
        assertThat(service.board().findCard(keep)).isPresent();

        service.undo();
        assertThat(service.board().allCards()).extracting(Card::title)
                .containsExactlyInAnyOrder("activa", "vieja");
    }

    @Test
    void purgeAcrossBoardsRemovesThemAllAndKeepsTheActiveBoard() {
        setUp();
        var first = service.addCard(column, "uno", "", BoardColor.DEFAULT);
        service.toggleCardLabel(first, Card.LABEL_ARCHIVED);

        BoardId other = service.createBoard("Other");
        service.openBoard(other);
        ColumnId otherColumn = service.addColumn("C", "", BoardColor.DEFAULT, WipLimit.unlimited());
        var second = service.addCard(otherColumn, "dos", "", BoardColor.DEFAULT);
        service.toggleCardLabel(second, Card.LABEL_ARCHIVED);
        service.openBoard(board); // the user is standing on the first board

        assertThat(service.archivedCardCount()).isEqualTo(2);

        int removed = service.purgeArchivedCards();

        assertThat(removed).isEqualTo(2);
        assertThat(service.activeBoardId()).isEqualTo(board);
        assertThat(service.board().allCards()).isEmpty();
        assertThat(service.archivedCardsOf(other)).isEmpty();
    }

    @Test
    void purgeWithNothingArchivedDoesNothing() {
        setUp();
        service.addCard(column, "activa", "", BoardColor.DEFAULT);

        assertThat(service.archivedCardCount()).isZero();
        assertThat(service.purgeArchivedCards()).isZero();
        assertThat(service.board().allCards()).hasSize(1);
    }
}
