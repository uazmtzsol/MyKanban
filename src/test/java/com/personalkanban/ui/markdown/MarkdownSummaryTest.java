package com.personalkanban.ui.markdown;

import javafx.scene.text.Text;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Session 10 (F2): the 3-line preview and the full mode must differ.
 *
 * <p>Root cause of the reported bug: the renderer counted AST blocks, so a
 * single paragraph with ten line breaks was \"one block\" and rendered
 * completely in preview mode. Here the preview is asserted on real line
 * counts of the rendered text.</p>
 */
class MarkdownSummaryTest {

    private static final int MAX = Integer.MAX_VALUE;

    private static String joined(List<Text> texts) {
        return texts.stream().map(Text::getText).reduce("", String::concat);
    }

    private static int rows(String text) {
        return text.isEmpty() ? 0 : (int) text.chars().filter(c -> c == '\n').count() + 1;
    }

    @Test
    void previewStopsAfterThreeLinesOfALongParagraph() {
        String markdown = "uno\ndos\ntres\ncuatro\ncinco\nseis";
        List<Text> preview = MarkdownSummary.render(markdown, 3, MAX);

        assertThat(rows(joined(preview))).as("preview must show 3 rows").isEqualTo(3);
        assertThat(joined(preview)).startsWith("uno\ndos\ntres");
        assertThat(joined(preview)).endsWith("\u2026");
    }

    @Test
    void fullModeRendersEveryLineWithoutAMarker() {
        String markdown = "uno\ndos\ntres\ncuatro\ncinco";
        String full = joined(MarkdownSummary.render(markdown, MAX, MAX));

        assertThat(rows(full)).isEqualTo(5);
        assertThat(full).doesNotContain("\u2026");
    }

    @Test
    void previewAndFullModeNoLongerAgreeOnTheSameDescription() {
        String markdown = "uno\ndos\ntres\ncuatro\ncinco\nseis";
        String preview = joined(MarkdownSummary.render(markdown, 3, MAX));
        String full = joined(MarkdownSummary.render(markdown, MAX, MAX));

        assertThat(preview).isNotEqualTo(full);
    }

    @Test
    void separateBlocksGetSeparateRows() {
        // A TextFlow flows its children inline: without an explicit break the
        // two paragraphs would render as one run-on row.
        String full = joined(MarkdownSummary.render("primera\n\nsegunda", MAX, MAX));

        assertThat(full).isEqualTo("primera\nsegunda");
    }

    @Test
    void everyListItemGetsItsOwnRow() {
        String full = joined(MarkdownSummary.render("- una\n- dos\n- tres", MAX, MAX));

        assertThat(full).isEqualTo("\u2022 una\n\u2022 dos\n\u2022 tres");
        assertThat(rows(full)).isEqualTo(3);
    }

    @Test
    void previewCountsListItemRowsToo() {
        List<Text> preview = MarkdownSummary.render(
                "- una\n- dos\n- tres\n- cuatro\n- cinco", 3, MAX);

        assertThat(rows(joined(preview))).isEqualTo(3);
        assertThat(joined(preview)).endsWith("\u2026");
    }

    @Test
    void unbrokenLongParagraphIsCappedByCharacters() {
        String markdown = "palabra ".repeat(40).strip();
        String preview = joined(MarkdownSummary.render(markdown, 3, 120));

        assertThat(preview.length()).isLessThanOrEqualTo(121);
        assertThat(preview).endsWith("\u2026");
        assertThat(joined(MarkdownSummary.render(markdown, MAX, MAX))).hasSizeGreaterThan(300);
    }

    @Test
    void blankOrNegativeInputRendersNothing() {
        assertThat(MarkdownSummary.render(null, 3, MAX)).isEmpty();
        assertThat(MarkdownSummary.render("   ", 3, MAX)).isEmpty();
        assertThat(MarkdownSummary.render("texto", 0, MAX)).isEmpty();
        assertThat(MarkdownSummary.render("texto", 3, 0)).isEmpty();
    }

    @Test
    void shortDescriptionSurvivesPreviewUntouched() {
        String markdown = "corta\nsegunda línea";
        String preview = joined(MarkdownSummary.render(markdown, 3, MAX));

        assertThat(preview).isEqualTo(markdown);
        assertThat(preview).doesNotContain("\u2026");
    }
}
