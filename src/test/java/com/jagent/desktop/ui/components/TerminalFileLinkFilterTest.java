package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TerminalFileLinkFilterTest {
    @TempDir private Path directory;

    @Test
    void findsRelativeFileWithLineAndColumnAndOpensIt() throws IOException {
        final Path file = directory.resolve("src/App.java");
        Files.createDirectories(directory.resolve("src"));
        Files.writeString(file, "class App {}");
        final var opened = new AtomicReference<TerminalFileLink>();
        final var result =
                new TerminalFileLinkFilter(directory, opened::set)
                        .apply("error: src/App.java:12:4");

        assertEquals(1, result.getItems().size(), "existing source paths should be linked");
        final var item = result.getItems().getFirst();
        assertEquals(7, item.getStartOffset(), "the link should start at the path");
        item.getLinkInfo().navigate();
        assertEquals(file, opened.get().path(), "relative paths should use the terminal directory");
        assertEquals(12, opened.get().line(), "line numbers should be preserved");
        assertEquals(4, opened.get().column(), "column numbers should be preserved");
    }

    @Test
    void rejectsUrlsMissingFilesAndDirectories() throws IOException {
        final Path directoryEntry = directory.resolve("src");
        Files.createDirectory(directoryEntry);
        final var filter = new TerminalFileLinkFilter(directory, ignored -> {});

        assertNull(filter.apply("https://example.test"), "URLs belong to the URL filter");
        assertNull(filter.apply("src"), "directories should not open as files");
        assertNull(filter.apply("missing.txt:3"), "missing paths should not be linked");
    }

    @Test
    void findsEveryExistingFileOnTheLine() throws IOException {
        Files.writeString(directory.resolve("first.txt"), "first");
        Files.writeString(directory.resolve("second.txt"), "second");

        final var result =
                new TerminalFileLinkFilter(directory, ignored -> {})
                        .apply("first.txt and second.txt");

        assertEquals(2, result.getItems().size(), "each existing path should be linked");
    }
}
