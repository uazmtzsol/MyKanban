package com.personalkanban.ui.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Writes the plain-text card export (session 10, F3) as a paginated A4 text
 * PDF. Unlike {@link BoardPdfExporter} — which snapshots a JavaFX node into a
 * bitmap — this produces <b>selectable text</b> with no UI involved, so it
 * works headless and keeps the file small.
 *
 * <p>Helvetica is limited to WinAnsi, so anything outside that repertoire
 * (emoji in a title, for instance) is replaced by {@code ?} instead of
 * failing the whole export.</p>
 */
public final class CardPdfWriter {

    private static final float MARGIN = 54f;          // ~19 mm
    private static final float LEADING = 15f;
    private static final float TITLE_SIZE = 16f;
    private static final float BODY_SIZE = 11f;
    private static final int WRAP_COLUMNS = 95;

    private CardPdfWriter() {
    }

    /** Writes {@code text} (the plain-text rendering) to {@code target}. */
    public static void write(String text, Path target) throws IOException {
        PDType1Font body = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font title = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        float pageHeight = PDRectangle.A4.getHeight();

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(document, page);
            float y = pageHeight - MARGIN;
            boolean firstLine = true;

            for (String rawLine : text.split("\\R", -1)) {
                String line = sanitize(rawLine);
                for (String wrapped : wrap(line, WRAP_COLUMNS)) {
                    float size = firstLine ? TITLE_SIZE : BODY_SIZE;
                    if (y - size < MARGIN) {
                        stream.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        stream = new PDPageContentStream(document, page);
                        y = pageHeight - MARGIN;
                    }
                    if (!wrapped.isEmpty()) {
                        stream.beginText();
                        stream.setFont(firstLine ? title : body, size);
                        stream.newLineAtOffset(MARGIN, y);
                        stream.showText(wrapped);
                        stream.endText();
                    }
                    y -= firstLine ? TITLE_SIZE + 8f : LEADING;
                    firstLine = false;
                }
            }
            stream.close();
            document.save(target.toFile());
        }
    }

    /**
     * Keeps what Helvetica can actually draw (ASCII plus the WinAnsi repertoire
     * — accented Latin, quotes, dashes, ellipsis) and replaces the rest.
     */
    static String sanitize(String line) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c < 0x20) {
                out.append(' '); // tabs and other control chars are not glyphs
            } else {
                out.append(isWinAnsi(c) ? c : '?');
            }
        }
        return out.toString();
    }

    private static boolean isWinAnsi(char c) {
        if (c < 0x80) {
            return true; // ASCII (control chars never reach here: they are stripped)
        }
        if (c >= 0xA0 && c <= 0xFF) {
            return true; // Latin-1 supplement (á é ñ ü ¿ ¡ … in Latin-1 range)
        }
        return "\u2013\u2014\u2018\u2019\u201c\u201d\u2020\u2021\u2022\u2026\u2030"
                .indexOf(c) >= 0 || c == '\u20AC'; // – — ‘ ’ “ ” † ‡ • … ‰ €
    }

    /** Greedy word wrap; hard-splits words longer than the column budget. */
    static java.util.List<String> wrap(String line, int maxColumns) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (line.length() <= maxColumns) {
            lines.add(line);
            return lines;
        }
        int start = 0;
        while (start < line.length()) {
            int end = Math.min(start + maxColumns, line.length());
            if (end < line.length()) {
                int space = line.lastIndexOf(' ', end);
                if (space > start) {
                    end = space;
                }
            }
            lines.add(line.substring(start, end));
            start = end;
            while (start < line.length() && line.charAt(start) == ' ') {
                start++;
            }
        }
        return lines;
    }
}
