package com.personalkanban.ui;

import com.personalkanban.AppContext;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListView;
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
 * Regression test for «Etiquetas → Gestionar etiquetas...»: the dialog used to
 * throw a {@link NullPointerException} while building itself, because the first
 * {@code updateButtons()} ran before the color chooser existed, so the window
 * never opened at all.
 */
class LabelManagerDialogTest {

    private AppContext context;
    private I18n i18n;

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
    void setUp() throws Exception {
        Path dir = Files.createTempDirectory("pk-label-manager");
        context = AppContext.openAt(dir.resolve("kanban.db"), dir.resolve("config.properties"));
        i18n = new I18n(context.savedLocale());
        context.globalLabels().addLabel("casa");
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    private LabelManagerDialog newDialog() {
        return new LabelManagerDialog(i18n, context.globalLabels(), context.boardService(),
                new Dialogs(i18n), () -> { });
    }

    @Test
    void buildingTheDialogDoesNotThrow() throws Exception {
        // The bug: this threw NPE, so the label manager never opened.
        Dialog<Void> dialog = fx(() -> newDialog().buildDialog());

        assertThat(dialog).isNotNull();
        assertThat(dialog.getTitle()).isEqualTo(i18n.text("labels.title"));
    }

    @Test
    void theDialogListsTheCatalogWithNothingSelected() throws Exception {
        Dialog<Void> dialog = fx(() -> newDialog().buildDialog());
        ListView<String> list = listOf(dialog);

        assertThat(list.getItems()).contains("casa", "Importante");
        assertThat(list.getSelectionModel().getSelectedItem()).isNull();

        // Nothing is selected yet: the actions that need one are disabled.
        assertThat(buttonOf(dialog, i18n.text("labels.rename"))).isFalse();
        assertThat(buttonOf(dialog, i18n.text("labels.remove"))).isFalse();
        assertThat(colorBoxOf(dialog)).isFalse();
    }

    @Test
    void selectingAUserLabelEnablesEveryAction() throws Exception {
        Dialog<Void> dialog = fx(() -> newDialog().buildDialog());
        ListView<String> list = listOf(dialog);

        fx(() -> {
            list.getSelectionModel().select("casa");
            return null;
        });

        assertThat(buttonOf(dialog, i18n.text("labels.rename"))).isTrue();
        assertThat(buttonOf(dialog, i18n.text("labels.remove"))).isTrue();
        assertThat(colorBoxOf(dialog)).isTrue();
    }

    @Test
    void aSystemLabelCanBeListedButNotChanged() throws Exception {
        Dialog<Void> dialog = fx(() -> newDialog().buildDialog());
        ListView<String> list = listOf(dialog);

        fx(() -> {
            list.getSelectionModel().select("Importante");
            return null;
        });

        assertThat(buttonOf(dialog, i18n.text("labels.rename"))).isFalse();
        assertThat(buttonOf(dialog, i18n.text("labels.remove"))).isFalse();
    }

    @Test
    void renamingALabelCarriesItsColorAlong() throws Exception {
        String oldKey = com.personalkanban.application.StylePrefs.labelKey("casa");
        String newKey = com.personalkanban.application.StylePrefs.labelKey("Hogar");
        context.boardService().setLabelStyle(oldKey, false, "#ff0000", "#ffffff");
        context.boardService().setLabelStyle(oldKey, true, "#aa0000", "#ffffff");

        fx(() -> {
            newDialog().moveColorRule("casa", "Hogar");
            return null;
        });

        assertThat(context.boardService().labelStyle(newKey, false))
                .hasValueSatisfying(colors -> assertThat(colors.background()).isEqualTo("#ff0000"));
        assertThat(context.boardService().labelStyle(newKey, true))
                .hasValueSatisfying(colors -> assertThat(colors.background()).isEqualTo("#aa0000"));
        assertThat(context.boardService().labelStyle(oldKey, false)).isEmpty();
        assertThat(context.boardService().labelStyle(oldKey, true)).isEmpty();
    }

    // ------------------------------------------------------------------
    // Helpers: walk the real dialog content
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static ListView<String> listOf(Dialog<Void> dialog) {
        return (ListView<String>) find(dialog, node -> node instanceof ListView);
    }

    private static boolean buttonOf(Dialog<Void> dialog, String text) {
        Button button = (Button) find(dialog,
                node -> node instanceof Button b && text.equals(b.getText()));
        return !button.isDisabled();
    }

    private static boolean colorBoxOf(Dialog<Void> dialog) {
        ComboBox<?> combo = (ComboBox<?>) find(dialog, node -> node instanceof ComboBox);
        return !combo.isDisabled();
    }

    private static Node find(Dialog<Void> dialog, java.util.function.Predicate<Node> match) {
        Node found = search(dialog.getDialogPane().getContent(), match);
        assertThat(found).as("node present in the dialog").isNotNull();
        return found;
    }

    private static Node search(Node node, java.util.function.Predicate<Node> match) {
        if (node == null) {
            return null;
        }
        if (match.test(node)) {
            return node;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node found = search(child, match);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
