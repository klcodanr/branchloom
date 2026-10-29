package com.jagent.desktop.ui.components.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TerminalFileLinkTest {
    private static final Path FILE = Path.of("src/App.java");

    @Test
    void exposesLocationAndSupportsValueEquality() {
        final TerminalFileLink link = new TerminalFileLink(FILE, 12, 4);
        final TerminalFileLink equal = new TerminalFileLink(FILE, 12, 4);

        assertEquals(FILE, link.path(), "path should be preserved");
        assertEquals(12, link.line(), "line should be preserved");
        assertEquals(4, link.column(), "column should be preserved");
        assertEquals(equal, link, "links with the same location should be equal");
        assertEquals(equal.hashCode(), link.hashCode(), "equal links should share a hash code");
        assertNotEquals(
                new TerminalFileLink(FILE, 13, 4),
                link,
                "different lines should produce different links");
        assertNotEquals(null, link, "a link should not equal null");
    }
}
