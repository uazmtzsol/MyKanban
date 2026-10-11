package com.personalkanban;

import com.personalkanban.application.BoardService;
import com.personalkanban.application.GlobalLabelsService;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.Card;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check of the global label manager through the real wiring:
 * SQLite persistence ({@code SqliteLabelCatalogStore} on the
 * {@code app_setting} table), the cross-board sweep, and adoption of labels
 * that predate the registry. This is the path the desktop app takes when the
 * user opens "Etiquetas → Gestionar etiquetas…".
 */
class AppContextGlobalLabelsTest {

    @TempDir
    Path tempDir;

    private Path db() {
        return tempDir.resolve("labels.db");
    }

    private Path configFile() {
        return tempDir.resolve("config.properties");
    }

    @Test
    void addRenameRemoveSurviveRestartAndReachEveryBoard() {
        String secondBoardId;
        try (AppContext context = AppContext.openAt(db(), configFile())) {
            GlobalLabelsService labels = context.globalLabels();
            BoardService service = context.boardService();

            labels.addLabel("Compras");

            // A second board whose card carries the new global label.
            secondBoardId = service.createBoard("Otro").value();
            service.openBoard(new BoardId(secondBoardId));
            ColumnId column = service.addColumn("Col", "", BoardColor.BLUE, WipLimit.unlimited());
            service.addCard(column, "card", "", BoardColor.BLUE, null, List.of("Compras"));

            labels.renameLabel("Compras", "Supermercado");

            // Stored copy of the second board already carries the new name.
            assertThat(service.board().allCards())
                    .flatExtracting(Card::labels)
                    .containsExactly("Supermercado");

            labels.addLabel("Temporal");
            labels.removeLabel("Temporal");
        }

        // Restart: the catalog is re-read from SQLite, the sweep was durable.
        try (AppContext context = AppContext.openAt(db(), configFile())) {
            GlobalLabelsService labels = context.globalLabels();
            BoardService service = context.boardService();

            assertThat(labels.names()).contains("Supermercado")
                    .doesNotContain("Compras", "Temporal");

            service.openBoard(new BoardId(secondBoardId));
            assertThat(service.board().allCards())
                    .flatExtracting(Card::labels)
                    .containsExactly("Supermercado");
        }
    }

    @Test
    void labelsOnCardsCreatedBeforeTheRegistryAreAdoptedOnStartup() {
        String boardId;
        try (AppContext context = AppContext.openAt(db(), configFile())) {
            BoardService service = context.boardService();
            ColumnId column = service.addColumn("Col", "", BoardColor.BLUE, WipLimit.unlimited());
            // A label typed by hand into the card dialog: not in any catalog yet.
            service.addCard(column, "card", "", BoardColor.BLUE, null, List.of("etiqueta vieja"));
            boardId = service.activeBoardId().value();
        }

        try (AppContext context = AppContext.openAt(db(), configFile())) {
            assertThat(context.globalLabels().names()).contains("etiqueta vieja");
        }
    }

    @Test
    void reservedSystemLabelsAreListedFirstAndProtected() {
        try (AppContext context = AppContext.openAt(db(), configFile())) {
            GlobalLabelsService labels = context.globalLabels();

            assertThat(labels.vocabularyForActiveBoard(List.of()))
                    .startsWith(Card.LABEL_URGENT, Card.LABEL_IMPORTANT, Card.LABEL_ARCHIVED);

            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> labels.removeLabel(Card.LABEL_URGENT));
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> labels.renameLabel(Card.LABEL_ARCHIVED, "Casi"));
        }
    }
}
