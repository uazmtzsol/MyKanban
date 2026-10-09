package com.personalkanban.ui.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Session 10 (F3): the card PDF is real (selectable) text, survives the
 * WinAnsi limits of the standard Helvetica, wraps long lines and paginates.
 */
class CardPdfWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesAReadablePdfWithSelectableText() throws IOException {
        Path target = tempDir.resolve("card.pdf");
        CardPdfWriter.write("Buy a gift\n============\n\nDescription:\nFirst line\n", target);

        assertThat(Files.exists(target)).isTrue();
        try (PDDocument document = Loader.loadPDF(target.toFile())) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Buy a gift");
            assertThat(text).contains("First line");
        }
    }

    @Test
    void accentedLatinSurvivesAndEmojiBecomeAPlaceholder() throws IOException {
        Path target = tempDir.resolve("accented.pdf");
        CardPdfWriter.write("Regalo para Ana (se\u00F1or\u00EDa) \uD83D\uDE00", target);

        try (PDDocument document = Loader.loadPDF(target.toFile())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("se\u00F1or\u00EDa");  // ñ and í are WinAnsi
            assertThat(text).contains("?");                 // the emoji is not
            assertThat(text).doesNotContain("\uD83D\uDE00");
        }
    }

    @Test
    void longDocumentsPaginateInsteadOfRunningOffThePage() throws IOException {
        StringBuilder lines = new StringBuilder("Title\n");
        for (int i = 0; i < 200; i++) {
            lines.append("line ").append(i).append('\n');
        }
        Path target = tempDir.resolve("long.pdf");
        CardPdfWriter.write(lines.toString(), target);

        try (PDDocument document = Loader.loadPDF(target.toFile())) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
        }
    }

    @Test
    void longLinesWrapOnWordBoundaries() {
        String longLine = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi";
        List<String> wrapped = CardPdfWriter.wrap(longLine, 20);

        assertThat(wrapped).hasSizeGreaterThan(1);
        assertThat(String.join(" ", wrapped)).isEqualTo(longLine);
        assertThat(wrapped).allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(20));
    }

    @Test
    void wordsLongerThanTheBudgetAreHardSplit() {
        List<String> wrapped = CardPdfWriter.wrap("x".repeat(50), 20);

        assertThat(wrapped).hasSize(3);
        assertThat(String.join("", wrapped)).isEqualTo("x".repeat(50));
    }

    @Test
    void sanitizeKeepsAsciiAndLatin1AndReplacesTheRest() {
        assertThat(CardPdfWriter.sanitize("abc ABC 123")).isEqualTo("abc ABC 123");
        assertThat(CardPdfWriter.sanitize("se\u00F1o \u00A1vamos!")).isEqualTo("se\u00F1o \u00A1vamos!");
        assertThat(CardPdfWriter.sanitize("curly \u201Cquotes\u201D and \u2026"))
                .isEqualTo("curly \u201Cquotes\u201D and \u2026");
        assertThat(CardPdfWriter.sanitize("tab\there")).isEqualTo("tab here");
        assertThat(CardPdfWriter.sanitize("\uD83D\uDE00\u2764")).isEqualTo("???");
        assertThat(CardPdfWriter.sanitize("")).isEmpty();
        assertThat(CardPdfWriter.sanitize(null)).isEmpty();
    }
}
