package com.jagent.desktop.services.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TerminalHistoryTest {
    @Test
    void createsParentsAndDeletesHistoryFiles(@TempDir final Path directory)
            throws java.io.IOException {
        final Path history = directory.resolve("nested").resolve("session.history");

        TerminalHistory.ensureParent(history.toString());
        Files.createFile(history);
        TerminalHistory.delete(history.toString());

        assertFalse(Files.exists(history), "history should be deleted");
        TerminalHistory.delete(null);
        TerminalHistory.delete("  ");
    }

    @Test
    void ignoresDeleteFailuresAndReportsParentCreationFailures(@TempDir final Path directory)
            throws java.io.IOException {
        final Path historyDirectory = directory.resolve("history");
        Files.createDirectories(historyDirectory);
        Files.createFile(historyDirectory.resolve("in-use"));
        TerminalHistory.delete(historyDirectory.toString());
        assertTrue(
                Files.exists(historyDirectory), "delete should ignore directory cleanup failures");

        final Path blocker = directory.resolve("blocker");
        Files.createFile(blocker);
        assertThrows(
                java.io.IOException.class,
                () -> TerminalHistory.ensureParent(blocker.resolve("child.history").toString()));
    }

    @Test
    void createsUniqueHistoryPath() throws java.io.IOException {
        final Path first = Path.of(TerminalHistory.createPath());
        final Path second = Path.of(TerminalHistory.createPath());

        assertTrue(first.startsWith(first.getParent()), "history should be under its parent");
        final Path filename = first.getFileName();
        assertNotNull(filename, "history should have a filename");
        assertTrue(
                filename.toString().endsWith(".history"),
                "history should use the history extension");
        assertFalse(first.equals(second), "history paths should be unique");
        TerminalHistory.delete(first.toString());
        TerminalHistory.delete(second.toString());
    }
}
