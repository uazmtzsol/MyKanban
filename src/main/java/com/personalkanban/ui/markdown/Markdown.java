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
 * scriptable context).
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
            %s
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

    /**
     * Full styled document for the WebView preview. The last two placeholders
     * are the raw HTML body and the value of a noscript hint.
     */
    public static String toStyledDocument(String markdown, String textColor, String backgroundColor,
                                          String codeBackground, String borderColor, String linkColor) {
        return PAGE_TEMPLATE.formatted(
                textColor, backgroundColor, codeBackground, codeBackground,
                borderColor, textColor, borderColor, linkColor,
                "<noscript>JavaScript is disabled for safety.</noscript>",
                toHtml(markdown));
    }
}
