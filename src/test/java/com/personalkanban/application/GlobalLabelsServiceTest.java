package com.personalkanban.application;

import com.personalkanban.application.port.InMemoryBoardRepository;
import com.personalkanban.application.port.InMemoryLabelCatalogStore;
import com.personalkanban.application.port.InMemorySettingsStore;
import com.personalkanban.application.port.InMemoryUndoHistory;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Global label lifecycle: labels are a single per-database vocabulary, and a
 * rename or a removal must reach the cards of <em>every</em> board, not just
 * the active one. Reserved system labels are listed but never editable.
 */
class GlobalLabelsServiceTest {

    private final InMemoryBoardRepository repository = new InMemoryBoardRepository();
    private final InMemoryUndoHistory history = new InMemoryUndoHistory();
    private final InMemorySettingsStore settings = new InMemorySettingsStore();
    private final BoardService boardService =
            new BoardService(repository, history, settings);
    private final InMemoryLabelCatalogStore catalogStore = new InMemoryLabelCatalogStore();
    private final GlobalLabelsService labels = new GlobalLabelsService(boardService, catalogStore);

    // ------------------------------------------------------------------
    // Add
    // ------------------------------------------------------------------

    @Test
    void addedLabelIsSelectableOnEveryBoard() {
        labels.addLabel("Proyecto UAZ");

        assertThat(labels.vocabularyForActiveBoard(List.of()))
                .contains("Proyecto UAZ");

        // A brand-new board offers the same global vocabulary.
        BoardId other = boardService.createBoard("Otro tablero");
        boardService.openBoard(other);
        assertThat(labels.vocabularyForActiveBoard(List.of()))
                .contains("Proyecto UAZ");
    }

    @Test
    void duplicateAndBlankLabelsAreRejectedAndNothingIsPersisted() {
        labels.addLabel("Casa");
        int savesAfterFirst = catalogStore.saveCount();

        assertThatThrownBy(() -> labels.addLabel("casa"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> labels.addLabel("   "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(catalogStore.saveCount()).isEqualTo(savesAfterFirst);
        assertThat(labels.userLabels()).containsExactly("Casa");
    }

    // ------------------------------------------------------------------
    // Rename (cross-board)
    // ------------------------------------------------------------------

    @Test
    void renameUpdatesCardsOfTheActiveAndEveryStoredBoard() {
        BoardId firstBoard = boardService.activeBoardId();
        labels.addLabel("uaz");
        CardId here = cardWithLabel("uaz");
        BoardId otherBoard = boardWithCard("uaz");

        labels.renameLabel("uaz", "Proyecto UAZ");

        // Catalog: old identity gone, new canonical spelling present.
        assertThat(labels.names()).contains("Proyecto UAZ").doesNotContain("uaz");

        // The first board was swept in storage and its view reloaded on demand.
        boardService.openBoard(firstBoard);
        assertThat(boardService.board().findCard(here).orElseThrow().labels())
                .containsExactly("Proyecto UAZ");

        // The other board's stored copy was swept too.
        boardService.openBoard(otherBoard);
        assertThat(boardService.board().allCards())
                .flatExtracting(Card::labels)
                .containsExactly("Proyecto UAZ");
    }

    @Test
    void renameKeepsOtherLabelsOfTheSameCard() {
        labels.addLabel("vieja");
        labels.addLabel("otra");
        BoardId boardId = boardService.activeBoardId();
        CardId card = cardWithLabel("vieja", "otra");

        labels.renameLabel("vieja", "nueva");

        boardService.openBoard(boardId);
        assertThat(boardService.board().findCard(card).orElseThrow().labels())
                .containsExactly("nueva", "otra");
    }

    // ------------------------------------------------------------------
    // Remove (cross-board)
    // ------------------------------------------------------------------

    @Test
    void removeDropsTheLabelFromEveryBoardAndFromTheCatalog() {
        BoardId firstBoard = boardService.activeBoardId();
        labels.addLabel("Legacy");
        CardId here = cardWithLabel("Legacy");
        BoardId otherBoard = boardWithCard("Legacy");

        labels.removeLabel("Legacy");

        assertThat(labels.names()).doesNotContain("Legacy");
        boardService.openBoard(firstBoard);
        assertThat(boardService.board().findCard(here).orElseThrow().labels()).isEmpty();
        boardService.openBoard(otherBoard);
        assertThat(boardService.board().allCards()).allMatch(card -> card.labels().isEmpty());
    }

    @Test
    void removingAnUnknownLabelIsRejected() {
        assertThatThrownBy(() -> labels.removeLabel("fantasma"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Label not found");
    }

    // ------------------------------------------------------------------
    // Reserved system labels
    // ------------------------------------------------------------------

    @Test
    void reservedLabelsComeFirstAndAreNotEditable() {
        assertThat(labels.vocabularyForActiveBoard(List.of()))
                .startsWith(Card.LABEL_URGENT, Card.LABEL_IMPORTANT, Card.LABEL_ARCHIVED);

        assertThatThrownBy(() -> labels.removeLabel(Card.LABEL_URGENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("System label cannot be removed");
        assertThatThrownBy(() -> labels.renameLabel(Card.LABEL_IMPORTANT, "Importantisimo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("System label cannot be renamed");
    }

    @Test
    void reservedNamesCannotBeAddedAsUserLabels() {
        assertThatThrownBy(() -> labels.addLabel(Card.LABEL_ARCHIVED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------
    // Rename rules (user request): no collisions, no reserved targets,
    // case-insensitive identity ("et1" == "ET1").
    // ------------------------------------------------------------------

    @Test
    void cannotRenameToTheNameOfAnExistingLabelIgnoringCase() {
        labels.addLabel("trabajo");
        labels.addLabel("casa");

        assertThatThrownBy(() -> labels.renameLabel("casa", "Trabajo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        assertThat(labels.userLabels()).containsExactly("trabajo", "casa");
    }

    @Test
    void cannotRenameToAReservedSystemName() {
        labels.addLabel("nueva");

        assertThatThrownBy(() -> labels.renameLabel("nueva", Card.LABEL_URGENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
        assertThatThrownBy(() -> labels.renameLabel("nueva", "urgente"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(labels.names()).contains("nueva");
    }

    @Test
    void labelsIgnoreCaseSoOneSpellingIsTheSameLabel() {
        // "ET1" identifies the existing "et1": duplicates are rejected…
        labels.addLabel("et1");
        assertThatThrownBy(() -> labels.addLabel("ET1"))
                .isInstanceOf(IllegalArgumentException.class);

        // …a rename can start from either spelling…
        BoardId boardId = boardService.activeBoardId();
        CardId card = cardWithLabel("et1");
        labels.renameLabel("ET1", "Etiqueta 1");
        assertThat(labels.names()).contains("Etiqueta 1").doesNotContain("et1");

        // …and removal also matches by identity, on cards too.
        labels.addLabel("borrable");
        labels.removeLabel("BORRABLE");
        assertThat(labels.names()).doesNotContain("borrable");
        boardService.openBoard(boardId);
        assertThat(boardService.board().findCard(card).orElseThrow().labels())
                .containsExactly("Etiqueta 1");
    }

    // ------------------------------------------------------------------
    // Adoption (databases created before the global registry)
    // ------------------------------------------------------------------

    @Test
    void labelsAlreadyOnStoredCardsAreAdoptedIntoTheCatalog() {
        cardWithLabel("etiqueta heredada");
        cardWithLabel("urgente"); // reserved spelling on a card: no duplicate entry

        GlobalLabelsService fresh = new GlobalLabelsService(boardService, catalogStore);

        assertThat(fresh.names()).contains("etiqueta heredada");
        assertThat(fresh.names().stream()
                .filter(name -> name.equalsIgnoreCase(Card.LABEL_URGENT)).toList())
                .hasSize(1);
    }

    @Test
    void catalogSurvivesAServiceRestart() {
        labels.addLabel("persistente");
        cardWithLabel("persistente");

        GlobalLabelsService restarted = new GlobalLabelsService(boardService, catalogStore);

        assertThat(restarted.names()).contains("persistente");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** A card on the active board carrying exactly the given labels. */
    private CardId cardWithLabel(String... cardLabels) {
        ColumnId column = boardService.addColumn("Col", "", BoardColor.BLUE, WipLimit.unlimited());
        return boardService.addCard(column, "card", "", BoardColor.BLUE, null, List.of(cardLabels));
    }

    /** A new board whose single card carries the given label; stored, not active. */
    private BoardId boardWithCard(String label) {
        BoardId boardId = boardService.createBoard("Otro");
        boardService.openBoard(boardId);
        cardWithLabel(label);
        return boardId;
    }
}
