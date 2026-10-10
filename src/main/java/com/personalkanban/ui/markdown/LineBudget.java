package com.personalkanban.ui.markdown;

/**
 * Line/character budget for the card-front summary (session 10, F2).
 *
 * <p>The preview mode of a card must show the first 3 lines and stop; the old
 * code counted AST <em>blocks</em> instead of lines, so a single paragraph
 * with ten line breaks rendered completely and the preview looked exactly
 * like the full mode. This class is the (JavaFX-free) decision maker: it
 * accepts content piece by piece and reports how much may be kept, when the
 * budget ran out, and how the cut should be marked.</p>
 *
 * <p>Two limits, whichever comes first:</p>
 * <ul>
 *   <li><b>lines</b> — a line ends at an explicit {@code \n} (blocks are
 *       separated with one by {@link MarkdownSummary});</li>
 *   <li><b>characters</b> — a paragraph without any line break would still
 *       wrap to many rows in the 220px card front, so a soft cap keeps the
 *       preview close to three rows (~36 chars/row at 12px).</li>
 * </ul>
 */
final class LineBudget {

    private final int maxLines;
    private final int maxChars;
    private int linesUsed;
    private int charsUsed;
    private boolean truncated;

    LineBudget(int maxLines, int maxChars) {
        this.maxLines = Math.max(maxLines, 0);
        this.maxChars = Math.max(maxChars, 0);
    }

    /** True once some content had to be dropped. */
    boolean exhausted() {
        return truncated;
    }

    /**
     * Offers the next piece of content (one styled run, possibly containing
     * line breaks) and returns the part that fits the budget — a prefix when
     * the cut lands inside this piece, empty when nothing fits anymore.
     */
    String accept(String content) {
        if (truncated || content == null || content.isEmpty()) {
            return "";
        }
        StringBuilder kept = new StringBuilder();
        int start = 0;
        while (start < content.length()) {
            if (linesUsed >= maxLines) {
                truncated = true;
                return kept.toString();
            }
            int newline = content.indexOf('\n', start);
            int end = newline < 0 ? content.length() : newline + 1;
            String piece = content.substring(start, end);
            int remaining = maxChars - charsUsed;
            if (piece.length() > remaining) {
                String prefix = cutAtWord(piece.substring(0, Math.max(remaining, 0)));
                if (!prefix.isEmpty()) {
                    kept.append(prefix);
                    charsUsed += prefix.length();
                }
                truncated = true;
                return kept.toString();
            }
            kept.append(piece);
            charsUsed += piece.length();
            if (newline >= 0) {
                linesUsed++;
            }
            start = end;
        }
        return kept.toString();
    }

    /**
     * The ellipsis to append to the last kept text once the budget is spent:
     * a trailing line break is replaced (never followed) so the marker stays
     * inside the last visible row instead of opening a new one.
     */
    String markCut(String lastText) {
        if (!truncated || lastText == null || lastText.isEmpty()) {
            return lastText;
        }
        if (lastText.endsWith("\n")) {
            return lastText.substring(0, lastText.length() - 1) + "\u2026";
        }
        return lastText.endsWith("\u2026") ? lastText : lastText + "\u2026";
    }

    /** Prefers cutting before a word, but never throws the whole piece away. */
    private static String cutAtWord(String prefix) {
        int space = prefix.lastIndexOf(' ');
        if (space > 0 && space >= prefix.length() / 2) {
            return prefix.substring(0, space);
        }
        return prefix;
    }
}
