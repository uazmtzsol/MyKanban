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
 *
 * <p>Session 10 (F2): the budget is enforced in <em>lines</em>, not in AST
 * blocks — the old counting rendered a whole ten-line paragraph in preview
 * mode, so the 3-line preview looked identical to the full mode. Blocks and
 * list items are now separated by an explicit line break, which is also what
 * makes them occupy separate rows at all: a {@code TextFlow} flows its
 * children inline, so without a {@code \n} two paragraphs would run together
 * as one line. The cut is marked with an ellipsis.</p>
 */
public final class MarkdownSummary {

    private MarkdownSummary() {
    }

    /** Renders at most {@code maxLines} lines, without a character cap. */
    public static List<Text> render(String markdown, int maxLines) {
        return render(markdown, maxLines, Integer.MAX_VALUE);
    }

    /**
     * Renders at most {@code maxLines} lines <b>and</b> at most
     * {@code maxChars} characters, whichever comes first; the cut is marked
     * with {@code …}. Pass {@link Integer#MAX_VALUE} for both to render
     * everything (the full mode).
     */
    public static List<Text> render(String markdown, int maxLines, int maxChars) {
        List<Text> out = new ArrayList<>();
        if (markdown == null || markdown.isBlank() || maxLines <= 0 || maxChars <= 0) {
            return out;
        }
        LineBudget budget = new LineBudget(maxLines, maxChars);
        Node document = Markdown.parse(markdown);
        boolean first = true;
        for (Node block = document.getFirstChild(); block != null && !budget.exhausted();
             block = block.getNext()) {
            if (!first) {
                keep(out, budget, "\n", null);
            }
            renderBlock(block, out, budget);
            first = false;
        }
        if (budget.exhausted() && !out.isEmpty()) {
            Text last = out.getLast();
            last.setText(budget.markCut(last.getText()));
        }
        return out;
    }

    private static void renderBlock(Node block, List<Text> out, LineBudget budget) {
        if (block instanceof Heading heading) {
            keep(out, budget, inlineText(heading), "-fx-font-weight: bold;");
        } else if (block instanceof Paragraph paragraph) {
            List<Text> segments = new ArrayList<>();
            collectInlines(paragraph, segments, null);
            String joined = segments.stream().map(Text::getText).reduce("", String::concat);
            if (!joined.isBlank()) {
                keep(out, budget, joined, null);
            }
        } else if (block instanceof BulletList list) {
            renderListItems(list, out, budget, "\u2022 ");
        } else if (block instanceof OrderedList list) {
            renderListItems(list, out, budget, "1. ");
        } else if (block instanceof BlockQuote quote) {
            keep(out, budget, inlineText(quote), "-fx-font-style: italic;");
        } else if (block instanceof ThematicBreak) {
            keep(out, budget, "\u2014".repeat(16), null);
        }
    }

    /** Adds the text if the budget keeps it (a cut may drop it entirely). */
    private static void keep(List<Text> out, LineBudget budget, String content, String style) {
        String kept = budget.accept(content);
        if (!kept.isEmpty()) {
            out.add(styled(kept, style));
        }
    }

    private static void renderListItems(Node list, List<Text> out, LineBudget budget, String marker) {
        boolean first = true;
        for (Node item = list.getFirstChild(); item != null && !budget.exhausted(); item = item.getNext()) {
            if (item instanceof ListItem) {
                if (!first) {
                    keep(out, budget, "\n", null);
                }
                keep(out, budget, marker + inlineText(item), null);
                first = false;
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
                // Session 4 request: the card front must SHOW the line breaks
                // the user typed (a plain newline inside a Text wraps visually).
                out.add(new Text("\n"));
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
