package com.personalkanban.ui.markdown;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;

import java.util.List;
import java.util.Set;

/**
 * Renders markdown to a self-contained HTML document (Pure Fabrication over
 * commonmark-java). GFM tables and strikethrough are enabled. The wrapper
 * style is hardcoded here — never loaded from the network — and the WebView
 * that displays it runs with JavaScript disabled, so card notes cannot
 * execute scripts (defense in depth: nothing user-typed ever reaches a
 * scriptable context). The template deliberately contains NO <noscript>
 * hint: with scripting disabled a WebKit-based WebView renders that element's
 * content visibly, so the "safety" note would show up in every preview.
 */
public final class Markdown {

    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(
                    TablesExtension.create(),
                    StrikethroughExtension.create()))
            .build();

    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
            .extensions(Set.of(
                    TablesExtension.create(),
                    StrikethroughExtension.create()))
            .build();

    private static final String PAGE_TEMPLATE = """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8">
            <style>
              body {
                font-family: "Segoe UI", System, sans-serif;
                font-size: 14px;
                color: %s;
                background: %s;
                padding: 4px 10px;
                line-height: 1.5;
              }
              code {
                font-family: Consolas, monospace;
                background: %s;
                padding: 1px 4px;
                border-radius: 3px;
              }
              pre {
                background: %s;
                padding: 8px;
                border-radius: 6px;
                overflow-x: auto;
              }
              pre code { background: transparent; padding: 0; }
              blockquote {
                border-left: 3px solid %s;
                margin-left: 0;
                padding-left: 10px;
                color: %s;
              }
              table { border-collapse: collapse; }
              th, td { border: 1px solid %s; padding: 4px 8px; }
              a { color: %s; }
              img { max-width: 100%%; }
              h1, h2, h3 { line-height: 1.2; }
            </style>
            </head>
            <body>
            %s
            </body>
            </html>
            """;

    private Markdown() {
    }

    /** Commonmark AST of the given markdown (for tests). */
    static Node parse(String markdown) {
        return PARSER.parse(markdown == null ? "" : markdown);
    }

    /** HTML fragment of the given markdown. */
    public static String toHtml(String markdown) {
        Node document = parse(markdown);
        return RENDERER.render(document);
    }

    /** Full styled document for the WebView preview (no script, no hints). */
    public static String toStyledDocument(String markdown, String textColor, String backgroundColor,
                                          String codeBackground, String borderColor, String linkColor) {
        return styledPage(toHtml(markdown), textColor, backgroundColor,
                codeBackground, borderColor, linkColor);
    }

    /** Styled page around a raw HTML body (used by the cheat sheet too). */
    public static String styledPage(String rawHtmlBody, String textColor, String backgroundColor,
                                    String codeBackground, String borderColor, String linkColor) {
        return PAGE_TEMPLATE.formatted(
                textColor, backgroundColor, codeBackground, codeBackground,
                borderColor, textColor, borderColor, linkColor, rawHtmlBody);
    }

    /**
     * Quick syntax reference as a two-column table: what you type (literal,
     * escaped) next to what you get (rendered by the very same engine, so
     * the guide can never drift from the editor's capabilities). Labels and
     * the sample word arrive localized; the syntax itself is universal.
     */
    public static String cheatsheetDocument(String writeLabel, String seeLabel, String hint,
                                            String sampleWord, String textColor, String backgroundColor,
                                            String codeBackground, String borderColor, String linkColor) {
        String w = sampleWord == null || sampleWord.isBlank() ? "Text" : sampleWord.strip();
        String[][] items = {
                {"# " + w + "\n## " + w + " 2"},
                {"**" + w + "**"},
                {"*" + w + "*"},
                {"~~" + w + "~~"},
                {"`" + w + "`"},
                {"- " + w + " 1\n- " + w + " 2\n- " + w + " 3"},
                {"1. " + w + " 1\n2. " + w + " 2"},
                {"> " + w},
                {"---"},
                {"[" + w + "](https://example.com)"},
                {"| A | B |\n|---|---|\n| 1 | 2 |"},
        };
        StringBuilder body = new StringBuilder();
        body.append("<p style=\"opacity:0.75;margin-top:0\">").append(escape(hint)).append("</p>\n");
        body.append("<table>\n<thead><tr><th>").append(escape(writeLabel))
                .append("</th><th>").append(escape(seeLabel)).append("</th></tr></thead>\n<tbody>\n");
        for (String[] item : items) {
            String source = item[0];
            body.append("<tr><td><code style=\"white-space:pre-wrap\">")
                    .append(escape(source)).append("</code></td><td>")
                    .append(toHtml(source)).append("</td></tr>\n");
        }
        body.append("</tbody>\n</table>");
        return styledPage(body.toString(), textColor, backgroundColor,
                codeBackground, borderColor, linkColor);
    }

    /** Minimal HTML escaping for literal source display. */
    private static String escape(String text) {
        return text == null ? "" : text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
