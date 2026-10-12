package com.personalkanban.ui;

import com.personalkanban.ui.markdown.MarkdownSummary;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applies the real theme stylesheets to the real card-front nodes, so the
 * regressions the user reported are caught here instead of on screen:
 *
 * <ul>
 *   <li>session 11 — the card description stayed black in the dark theme
 *       ({@code .card-description .text} never matched, because a Text node
 *       built in code carries no style class of its own);</li>
 *   <li>session 11 — a selected card was indistinguishable from an unselected
 *       one, because the {@code .card.pk-color-*} rules that paint a colored
 *       card's accent outranked a single-class {@code .card-selected}.</li>
 * </ul>
 */
class CardThemeCssTest {

    @BeforeAll
    static void startJavaFxToolkit() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // Toolkit already initialized by another test in this JVM.
        }
        // Closing a window would otherwise trigger JavaFX's implicit exit and
        // leave every later Platform.runLater hanging.
        Platform.setImplicitExit(false);
    }

    /** Runs a query on the FX thread and returns its result. */
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
        assertThat(done.await(20, TimeUnit.SECONDS)).as("FX action finished").isTrue();
        assertThat(error.get()).isNull();
        return result.get();
    }

    /** Puts a node under a scene carrying one theme sheet and lays it out. */
    private static void applyTheme(Parent root, String stylesheet) {
        Scene scene = new Scene(root, 420, 320);
        scene.getStylesheets().add(
                CardThemeCssTest.class.getResource(stylesheet).toExternalForm());
        root.applyCss();
        root.layout();
    }

    /** The card-front description exactly as CardViewBuilder builds it. */
    private static TextFlow descriptionFlow(String markdown) {
        TextFlow flow = new TextFlow(
                MarkdownSummary.render(markdown, 3, 120).toArray(new Text[0]));
        flow.getStyleClass().add("card-description");
        flow.setPrefWidth(220);
        return flow;
    }

    private static List<Paint> descriptionFills(String markdown, String stylesheet)
            throws Exception {
        return fx(() -> {
            TextFlow flow = descriptionFlow(markdown);
            applyTheme(new VBox(flow), stylesheet);
            return flow.getChildrenUnmodifiable().stream()
                    .filter(Text.class::isInstance)
                    .map(node -> ((Text) node).getFill())
                    .toList();
        });
    }

    @Test
    void darkThemePaintsTheCardDescriptionReadably() throws Exception {
        List<Paint> fills = descriptionFills("Hola **mundo**\n- uno\n- dos", "/css/dark.css");
        assertThat(fills).isNotEmpty();
        assertThat(fills).allSatisfy(fill ->
                assertThat(fill).isEqualTo(Color.web("#c3cbd6")));
    }

    @Test
    void lightThemePaintsTheCardDescriptionReadably() throws Exception {
        List<Paint> fills = descriptionFills("Hola **mundo**", "/css/light.css");
        assertThat(fills).isNotEmpty();
        assertThat(fills).allSatisfy(fill ->
                assertThat(fill).isEqualTo(Color.web("#4b5563")));
    }

    @Test
    void aSelectedColoredCardLooksSelected() throws Exception {
        Color background = fx(() -> {
            Region card = new Region();
            card.getStyleClass().addAll("card", "pk-color-blue", "card-selected");
            applyTheme(new VBox(card), "/css/light.css");
            return (Color) card.getBackground().getFills().get(0).getFill();
        });
        assertThat(background).isEqualTo(Color.web("#cfe3fb"));
    }

    @Test
    void theCurrentCardStandsOutEvenWithAColorAccent() throws Exception {
        Color stroke = fx(() -> {
            Region card = new Region();
            card.getStyleClass().addAll("card", "pk-color-blue", "card-current");
            applyTheme(new VBox(card), "/css/dark.css");
            return (Color) card.getBorder().getStrokes().get(0).getTopStroke();
        });
        assertThat(stroke).isEqualTo(Color.web("#60a5fa"));
    }
}
