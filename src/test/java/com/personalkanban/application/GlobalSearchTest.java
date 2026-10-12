package com.personalkanban.application;

import com.personalkanban.application.port.InMemoryBoardRepository;
import com.personalkanban.application.port.InMemorySettingsStore;
import com.personalkanban.application.port.InMemoryUndoHistory;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.BoardId;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.WipLimit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests of the cross-board search (session 14, B2). The per-board matching is
 * already covered by {@code BoardServiceTest}; what is checked here is the
 * catalogue-wide behaviour: every board is visited, the active one comes
 * first, hits carry the field they matched and a usable excerpt, and the
 * per-board cap protects the list from one huge board.
 */
class GlobalSearchTest {

    private final InMemoryBoardRepository repository = new InMemoryBoardRepository();
    private final InMemoryUndoHistory history = new InMemoryUndoHistory();
    private final InMemorySettingsStore settings = new InMemorySettingsStore();
    private final BoardService service = new BoardService(repository, history, settings);
    private final GlobalSearch search = new GlobalSearch(service);

    private BoardId addBoard(String name) {
        BoardId id = service.createBoard(name);
        service.openBoard(id);
        return id;
    }

    private ColumnId column(String title) {
        return service.addColumn(title, "", BoardColor.BLUE, WipLimit.unlimited());
    }

    @Test
    void findsCardsOnEveryBoardWithoutSwitchingTheActiveOne() {
        BoardId home = addBoard("Home");
        ColumnId todo = column("Todo");
        service.addCard(todo, "Buy groceries", "", BoardColor.BLUE);

        BoardId archive = addBoard("Archive");
        ColumnId inbox = column("Inbox");
        service.addCard(inbox, "Invoice 2025", "", BoardColor.BLUE);
        service.openBoard(home);

        List<GlobalSearch.Hit> hits = search.search("invoic");

        assertThat(hits).extracting(GlobalSearch.Hit::boardName).containsExactly("Archive");
        assertThat(hits).extracting(hit -> hit.card().title()).containsExactly("Invoice 2025");
        assertThat(service.activeBoardId()).isEqualTo(home);
    }

    @Test
    void listsTheActiveBoardFirstThenTheRest() {
        addBoard("Home");
        ColumnId homeColumn = column("Todo");
        service.addCard(homeColumn, "Nota común", "", BoardColor.BLUE);

        BoardId work = addBoard("Work");
        ColumnId workColumn = column("Todo");
        service.addCard(workColumn, "Nota común de trabajo", "", BoardColor.BLUE);

        BoardId later = addBoard("Later");
        ColumnId laterColumn = column("Todo");
        service.addCard(laterColumn, "Nota común antigua", "", BoardColor.BLUE);
        service.openBoard(work);

        List<GlobalSearch.Hit> hits = search.search("común");

        assertThat(hits).extracting(GlobalSearch.Hit::boardName)
                .containsExactly("Work", "Home", "Later");
    }

    @Test
    void saysWhereItMatchedAndShowsAnExcerpt() {
        addBoard("Home");
        ColumnId todo = column("Todo");
        service.addCard(todo, "Informe mensual", "", BoardColor.BLUE);
        CardId noted = service.addCard(todo, "Otra cosa", "", BoardColor.BLUE);
        service.setCardNotes(noted, "Primero una nota bastante larga para que el extracto"
                + " tenga que recortarse, y luego el informe y después más relleno para forzar el recorte.");
        service.addCard(todo, "Tercera", "", BoardColor.BLUE, null,
                java.util.List.of("informes"));
        CardId checked = service.addCard(todo, "Cuarta", "", BoardColor.BLUE);
        service.addChecklistItem(checked, "revisar informe");

        List<GlobalSearch.Hit> hits = search.search("informe");

        assertThat(hits).extracting(GlobalSearch.Hit::field)
                .containsExactly(GlobalSearch.Field.TITLE, GlobalSearch.Field.NOTES,
                        GlobalSearch.Field.LABEL, GlobalSearch.Field.CHECKLIST);
        assertThat(hits.get(0).snippet()).isEqualTo("Informe mensual");
        assertThat(hits.get(1).snippet()).startsWith("…")
                .contains("informe").endsWith("…");
        assertThat(hits.get(2).snippet()).isEqualTo("informes");
    }

    @Test
    void descriptionsCountAsAHitToo() {
        addBoard("Home");
        ColumnId todo = column("Todo");
        service.addCard(todo, "Sin pistas", "referencia interna 42", BoardColor.BLUE);

        List<GlobalSearch.Hit> hits = search.search("interna 42");

        assertThat(hits).extracting(GlobalSearch.Hit::field)
                .containsExactly(GlobalSearch.Field.DESCRIPTION);
    }

    @Test
    void capsHitsPerBoardAndSaysWhenItDid() {
        addBoard("Home");
        ColumnId todo = column("Todo");
        for (int i = 0; i < 5; i++) {
            service.addCard(todo, "Tarea repetida " + i, "", BoardColor.BLUE);
        }

        GlobalSearch.Result capped = search.search("repetida", 2);
        GlobalSearch.Result uncapped = search.search("repetida", 10);

        assertThat(capped.hits()).hasSize(2);
        assertThat(capped.truncated()).isTrue();
        assertThat(uncapped.hits()).hasSize(5);
        assertThat(uncapped.truncated()).isFalse();
    }

    @Test
    void aBlankQueryFindsNothingAnywhere() {
        addBoard("Home");
        service.addCard(column("Todo"), "Cualquier cosa", "", BoardColor.BLUE);

        assertThat(search.search("")).isEmpty();
        assertThat(search.search("   ")).isEmpty();
        assertThat(search.search(null)).isEmpty();
        assertThat(search.search("", 5).truncated()).isFalse();
    }

    @Test
    void theExcerptIsSingleLineAndTrimmed() {
        String text = "primera línea\nsegunda línea con la palabra clave\ntercera línea";

        String excerpt = GlobalSearch.excerpt(text, text.indexOf("clave"), "clave".length());

        assertThat(excerpt).doesNotContain("\n").contains("clave");

        String start = GlobalSearch.excerpt(text, 0, 7);
        assertThat(start).startsWith("primera").doesNotStartWith("…");
    }
}
