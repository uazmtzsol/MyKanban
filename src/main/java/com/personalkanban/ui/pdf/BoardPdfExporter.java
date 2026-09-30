package com.personalkanban.ui.pdf;

import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Exports a JavaFX node (the visible board) to PDF: a pixel-perfect
 * snapshot, tiled poster-style across as many landscape pages as needed at a
 * fixed scale, so pages can be reassembled edge to edge. Pure UI concern —
 * no domain/application knowledge, only a {@link Node} in, a file out.
 */
public final class BoardPdfExporter {

    private static final float DPI = 150f;
    private static final float MARGIN_PT = 24f;
    private static final float PAGE_WIDTH_PT = PDRectangle.A4.getHeight(); // landscape
    private static final float PAGE_HEIGHT_PT = PDRectangle.A4.getWidth();

    private BoardPdfExporter() {
    }

    public static void export(Node node, Path target) throws IOException {
        BufferedImage image = toBufferedImage(node.snapshot(new SnapshotParameters(), null));
        writePdf(image, target);
    }

    /** Manual pixel copy: avoids depending on the javafx-swing module just for this. */
    private static BufferedImage toBufferedImage(WritableImage fxImage) {
        int width = (int) Math.round(fxImage.getWidth());
        int height = (int) Math.round(fxImage.getHeight());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = fxImage.getPixelReader();
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                row[x] = reader.getArgb(x, y);
            }
            image.setRGB(0, y, width, 1, row, 0, width);
        }
        return image;
    }

    private static void writePdf(BufferedImage image, Path target) throws IOException {
        float contentWidthPt = PAGE_WIDTH_PT - 2 * MARGIN_PT;
        float contentHeightPt = PAGE_HEIGHT_PT - 2 * MARGIN_PT;
        int tileWidthPx = (int) (contentWidthPt / 72f * DPI);
        int tileHeightPx = (int) (contentHeightPt / 72f * DPI);

        int cols = Math.max(1, (int) Math.ceil(image.getWidth() / (double) tileWidthPx));
        int rows = Math.max(1, (int) Math.ceil(image.getHeight() / (double) tileHeightPx));

        try (PDDocument document = new PDDocument()) {
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    addTilePage(document, image, row, col, rows, cols, tileWidthPx, tileHeightPx);
                }
            }
            document.save(target.toFile());
        }
    }

    private static void addTilePage(PDDocument document, BufferedImage image, int row, int col,
                                     int rows, int cols, int tileWidthPx, int tileHeightPx) throws IOException {
        int x = col * tileWidthPx;
        int y = row * tileHeightPx;
        int w = Math.min(tileWidthPx, image.getWidth() - x);
        int h = Math.min(tileHeightPx, image.getHeight() - y);
        BufferedImage tile = image.getSubimage(x, y, w, h);

        PDPage page = new PDPage(new PDRectangle(PAGE_WIDTH_PT, PAGE_HEIGHT_PT));
        document.addPage(page);
        PDImageXObject pdImage = LosslessFactory.createFromImage(document, tile);
        float tileWidthPt = w * 72f / DPI;
        float tileHeightPt = h * 72f / DPI;

        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.drawImage(pdImage, MARGIN_PT, PAGE_HEIGHT_PT - MARGIN_PT - tileHeightPt,
                    tileWidthPt, tileHeightPt);
            if (rows > 1 || cols > 1) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 8);
                content.newLineAtOffset(MARGIN_PT, MARGIN_PT / 2);
                content.showText("R" + (row + 1) + "/" + rows + " C" + (col + 1) + "/" + cols);
                content.endText();
            }
        }
    }
}
