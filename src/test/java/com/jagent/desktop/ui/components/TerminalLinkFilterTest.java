package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TerminalLinkFilterTest {
    @Test
    void findsUrlsAndExcludesTrailingPunctuation() {
        final var opened = new AtomicReference<String>();
        final var result =
                new TerminalLinkFilter(opened::set).apply("See https://example.com/path.");

        assertEquals(1, result.getItems().size(), "one URL should be detected");
        final var item = result.getItems().getFirst();
        assertEquals(4, item.getStartOffset(), "the URL should start at the match offset");
        assertEquals(28, item.getEndOffset(), "sentence punctuation should not be part of the URL");

        item.getLinkInfo().navigate();
        assertEquals("https://example.com/path", opened.get(), "navigation should open the URL");
    }

    @Test
    void findsMultipleUrlsAndReturnsNullWhenNoneExist() {
        final var filter = new TerminalLinkFilter(ignored -> {});

        final var result = filter.apply("https://one.test and HTTP://two.test");
        assertNotNull(result, "URLs should create a result");
        assertEquals(2, result.getItems().size(), "all URLs in a line should be detected");
        assertNull(filter.apply("no links here"), "lines without URLs should not create a result");
    }

    @Test
    void findsUrlSpanningTerminalLines() {
        final var opened = new AtomicReference<String>();
        final var result =
                new TerminalLinkFilter(opened::set).apply("https://example.com/long-\npath");

        assertEquals(1, result.getItems().size(), "a URL may span terminal lines");
        result.getItems().getFirst().getLinkInfo().navigate();
        assertEquals("https://example.com/long-path", opened.get(), "line breaks are not URL data");
    }

    @Test
    void onlyNavigatesWhenActivationIsAllowed() {
        final var opened = new AtomicReference<String>();
        final var allowed = new AtomicBoolean();
        final var result =
                new TerminalLinkFilter(opened::set, allowed::get).apply("https://example.com");

        assertNotNull(result, "the URL should be detected");
        result.getItems().getFirst().getLinkInfo().navigate();
        assertNull(opened.get(), "ordinary clicks should not navigate links");

        allowed.set(true);
        result.getItems().getFirst().getLinkInfo().navigate();
        assertEquals("https://example.com", opened.get(), "modified clicks should navigate links");
    }
}
