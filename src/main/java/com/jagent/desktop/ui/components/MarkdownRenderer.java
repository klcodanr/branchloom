package com.jagent.desktop.ui.components;

import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/** Converts GitHub Markdown into HTML suitable for Swing's HTML editor. */
public final class MarkdownRenderer {
    private static final Parser PARSER = Parser.builder().build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().build();

    private MarkdownRenderer() {}

    public static String toHtml(final String markdown) {
        return RENDERER.render(PARSER.parse(UiText.valueOrDefault(markdown, "")));
    }
}
