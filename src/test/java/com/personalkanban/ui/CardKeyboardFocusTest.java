package com.personalkanban.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wiring guards for the kanban keyboard navigation.
 *
 * <p>Both failures are silent: the arrow keys locate the card node through its
 * {@code userData} (without it {@code kanbanFocusCard} finds nothing and the
 * keys appear dead), and the focused card is only visible through the
 * stylesheet's {@code .card:focused} highlight. Neither can be asserted from a
 * unit test without booting a database-backed controller, so the source and
 * the shipped stylesheets are checked instead.</p>
 */
class CardKeyboardFocusTest {

    private static Path uiRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 4 && candidate != null; depth++) {
            Path probe = candidate.resolve("src/main/java/com/personalkanban/ui");
            if (Files.isDirectory(probe)) {
                return probe;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate the ui sources");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void cardNodesCarryTheirIdAndCanTakeFocus() throws IOException {
        String source = read(uiRoot().resolve("CardViewBuilder.java"));

        assertThat(source)
                .as("kanbanFocusCard locates the card through userData")
                .contains("view.setUserData(card.id())");
        assertThat(source)
                .as("a non-focusable node never shows the focus highlight")
                .contains("view.setFocusTraversable(true)");
    }

    @Test
    void bothThemesHighlightTheFocusedCard() throws IOException {
        for (String theme : List.of("light.css", "dark.css")) {
            String css = read(resourcesBase().resolve("css").resolve(theme));
            assertThat(css)
                    .as("%s must style the keyboard-focused card", theme)
                    .contains(".card:focused");
            assertThat(css.substring(css.indexOf(".card:focused")))
                    .as("%s: the highlight must be visible without a border "
                            + "(custom card colors set the border inline)", theme)
                    .contains("dropshadow");
        }
    }

    /** src/main/resources (the parent of the Java root). */
    private static Path resourcesBase() {
        Path javaRoot = uiRoot();            // .../src/main/java/com/personalkanban/ui
        Path mainRoot = javaRoot.getParent().getParent().getParent().getParent(); // src/main
        return mainRoot.resolve("resources");
    }

    @Test
    void focusIsScrolledIntoView() throws IOException {
        String source = read(uiRoot().resolve("BoardController.java"));

        assertThat(source)
                .as("a card reached with the arrow keys must be scrolled into view")
                .contains("scrollCardIntoView");
        assertThat(source)
                .as("navigation must respect the active filters")
                .contains("labelFilter::matches")
                .contains("quickFilter::matches");
    }
}
