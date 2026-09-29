package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarkdownRendererTest {
    @Test
    void rendersGitHubMarkdownAsHtml() {
        final String html = MarkdownRenderer.toHtml("## Summary\n\n**important** and `code`");

        assertTrue(html.contains("<h2>Summary</h2>"), "headings should become HTML headings");
        assertTrue(
                html.contains("<strong>important</strong>"), "emphasis should become strong HTML");
        assertTrue(html.contains("<code>code</code>"), "inline code should become HTML code");
    }
}
