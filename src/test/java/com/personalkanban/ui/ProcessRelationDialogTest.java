package com.personalkanban.ui;

import com.personalkanban.AppContext;
import com.personalkanban.domain.board.BoardColor;
import com.personalkanban.domain.board.CardId;
import com.personalkanban.domain.board.ColumnId;
import com.personalkanban.domain.board.ProcessId;
import com.personalkanban.domain.board.WipLimit;
import javafx.application.Platform;
import javafx.scene.Scene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T11 (informe de mejoras) · the process-view relation actions.
 *
 * <p>N-7: the green arrow ("add a new task here") must ask for a title — and
 * offer the process and due date — instead of silently inserting an empty
 * card. N-9: linking an existing card must only offer the members of the
 * anchor's process.</p>
 *
 * <p>The creation dialog and the picker are modal, so they are not driven
 * here; N-9's filter is exercised through the real controller
 * ({@link BoardController#linkCandidatesFor}), and N-7's dialog wiring is
 * checked on the controller's code, the same way
 * {@code ProcessViewLinkTest} checks the unlink confirmation.</p>
 */
class ProcessRelationDialogTest {

    private AppContext context;
    private BoardController controller;
    private ColumnId column;
    private CardId alpha;
    private CardId beta;
    private CardId lone;

    @BeforeAll
    static void startJavaFxToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // Toolkit already initialized by another test in this JVM.
        }
        Platform.setImplicitExit(false);
    }

    @BeforeEach
    void setUpBoard() throws Exception {
        Path dir = Files.createTempDirectory("pk-process-relations");
        context = AppContext.openAt(dir.resolve("kanban.db"), dir.resolve("config.properties"));
        column = context.boardService().addColumn("Todo", "", BoardColor.BLUE, WipLimit.unlimited());
        alpha = context.boardService().addCard(column, "Alfa", "", BoardColor.DEFAULT);
        beta = context.boardService().addCard(column, "Beta", "", BoardColor.DEFAULT);
        lone = context.boardService().addCard(column, "Suelta", "", BoardColor.DEFAULT);
        ProcessId process = context.boardService().addProcess("#flujo");
        context.boardService().assignProcessToCards(List.of(alpha, beta), process);

        fx(() -> {
            controller = new BoardController(context);
            Scene scene = new Scene(controller.root(), 1000, 700);
            scene.getStylesheets().addAll(context.themeManager().stylesheets());
            controller.bindScene(scene);
            controller.onSelectView(true);
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void linkingExistingOnlyOffersTheMembersOfTheAnchorsProcess() throws Exception {
        fx(() -> {
            List<CardId> candidates = controller.linkCandidatesFor(alpha).stream()
                    .map(card -> card.id()).toList();

            assertThat(candidates)
                    .as("only the other member of the process is offered")
                    .containsExactly(beta);
            assertThat(candidates)
                    .as("the anchor itself is never offered")
                    .doesNotContain(alpha);
            assertThat(candidates)
                    .as("a card outside the process is not offered")
                    .doesNotContain(lone);
            return null;
        });
    }

    @Test
    void aCardWithoutProcessFallsBackToEveryOtherCard() throws Exception {
        fx(() -> {
            List<CardId> candidates = controller.linkCandidatesFor(lone).stream()
                    .map(card -> card.id()).toList();

            assertThat(candidates).containsExactlyInAnyOrder(alpha, beta);
            return null;
        });
    }

    @Test
    void theGreenArrowAsksForATitleInsteadOfInsertingAnEmptyCard() throws Exception {
        String code = Files.readString(boardControllerSource());
        int start = code.indexOf("public void onProcessAddNewRelation(");
        int end = code.indexOf("List<com.personalkanban.domain.board.Card> linkCandidatesFor(");
        assertThat(start).as("onProcessAddNewRelation is where it was").isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        String method = code.substring(start, end);

        assertThat(method)
                .as("the creation goes through the card dialog")
                .contains("dialogs.cardDialog(");
        assertThat(method)
                .as("the anchor's process is preselected (N-7)")
                .contains("anchorCard.processId()");
        assertThat(method)
                .as("the new card uses the dialog's title")
                .contains("f.title()");
        assertThat(method)
                .as("the fixed empty-card title is gone")
                .doesNotContain("i18n.text(\"process.new.card.title\")");
    }

    // ------------------------------------------------------------------

    private static Path boardControllerSource() {
        Path candidate = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 4 && candidate != null; depth++) {
            Path probe = candidate.resolve("src/main/java/com/personalkanban/ui/BoardController.java");
            if (Files.isRegularFile(probe)) {
                return probe;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate BoardController.java");
    }

    private static <T> T fx(Supplier<T> action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(action.get());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });
        assertThat(done.await(30, TimeUnit.SECONDS)).as("FX action finished").isTrue();
        assertThat(error.get()).isNull();
        return result.get();
    }
}
