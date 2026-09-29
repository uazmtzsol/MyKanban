package com.personalkanban.ui.markdown;

import javafx.scene.text.Text;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.ThematicBreak;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight native markdown summary renderer (no WebView): walks the
 * commonmark AST and produces styled {@link Text} nodes for the card-front
 * description preview. Headings, emphasis, strong, inline code, lists and
 * quotes are enough for a glanceable preview; the full render lives in the
 * detail window's WebView.
 */
public final class MarkdownSummary {

    private MarkdownSummary() {
    }

    /** Renders at most {@code maxLines} lines of styled text. */
    public static List<Text> render(String markdown, int maxLines) {
        List<Text> lines = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return lines;
        }
        Node document = Markdown.parse(markdown);
        for (Node block = document.getFirstChild(); block != null && lines.size() < maxLines;
             block = block.getNext()) {
            renderBlock(block, lines, maxLines);
        }
        return lines;
    }

    private static void renderBlock(Node block, List<Text> lines, int maxLines) {
        if (block instanceof Heading heading) {
            lines.add(styled(inlineText(heading), "-fx-font-weight: bold;"));
        } else if (block instanceof Paragraph paragraph) {
            List<Text> segments = new ArrayList<>();
            collectInlines(paragraph, segments, null);
            Text joined = new Text(segments.stream().map(Text::getText).reduce("", String::concat));
            if (!joined.getText().isBlank()) {
                lines.add(joined);
            }
        } else if (block instanceof BulletList list) {
            renderListItems(list, lines, maxLines, "\u2022 ");
        } else if (block instanceof OrderedList list) {
            renderListItems(list, lines, maxLines, "1. ");
        } else if (block instanceof BlockQuote quote) {
            lines.add(styled(inlineText(quote), "-fx-font-style: italic;"));
        } else if (block instanceof ThematicBreak) {
            lines.add(new Text("\u2014".repeat(16)));
        }
    }

    private static void renderListItems(Node list, List<Text> lines, int maxLines, String marker) {
        for (Node item = list.getFirstChild(); item != null && lines.size() < maxLines; item = item.getNext()) {
            if (item instanceof ListItem) {
                lines.add(new Text(marker + inlineText(item)));
            }
        }
    }

    /** Walks inline nodes, tracking bold/italic/code style per segment. */
    private static void collectInlines(Node node, List<Text> out, String style) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof StrongEmphasis) {
                collectInlines(child, out, "-fx-font-weight: bold;");
            } else if (child instanceof Emphasis) {
                collectInlines(child, out, "-fx-font-style: italic;");
            } else if (child instanceof Code code) {
                out.add(styled(code.getLiteral(), style));
            } else if (child instanceof org.commonmark.node.Text textNode) {
                out.add(styled(textNode.getLiteral(), style));
            } else if (child instanceof SoftLineBreak) {
                out.add(new Text(" "));
            } else if (child instanceof HardLineBreak) {
                out.add(new Text("\n"));
            } else {
                collectInlines(child, out, style);
            }
        }
    }

    private static Text styled(String content, String style) {
        Text text = new Text(content);
        if (style != null && !style.isEmpty()) {
            text.setStyle(style);
        }
        return text;
    }

    private static String inlineText(Node node) {
        StringBuilder builder = new StringBuilder();
        collectPlainText(node, builder);
        return builder.toString().strip();
    }

    private static void collectPlainText(Node node, StringBuilder builder) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof org.commonmark.node.Text textNode) {
                builder.append(textNode.getLiteral());
            } else if (child instanceof Code code) {
                builder.append(code.getLiteral());
            } else {
                collectPlainText(child, builder);
            }
        }
    }
}
