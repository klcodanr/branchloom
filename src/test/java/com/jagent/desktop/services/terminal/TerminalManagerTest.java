package com.jagent.desktop.services.terminal;

import static org.junit.jupiter.api.Assertions.*;

import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.test.AsyncTestSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TerminalManagerTest {
    private static final Path TEMP_DIRECTORY = Path.of(System.getProperty("java.io.tmpdir"));
    private static final String TRUE_COMMAND = "true";
    private static final String RESOURCE = "resource";

    @Test
    void createsRetainsReportsAndDisposesRuntimes() {
        final TerminalManager manager = TerminalManager.get();
        final TerminalId id = TerminalId.create();
        final Terminal terminal = new Terminal(null, "Shell", TRUE_COMMAND);

        final TerminalRuntime created = manager.create(TRUE_COMMAND, TEMP_DIRECTORY, RESOURCE);
        final TerminalRuntime retained =
                manager.retained(id, terminal, TEMP_DIRECTORY, "retained-resource");

        assertNotSame(created, retained, "create should return a new runtime");
        assertSame(
                retained,
                manager.retained(id, terminal, TEMP_DIRECTORY, "updated"),
                "retained runtime should be reused");
        assertEquals(TerminalState.STARTING, manager.state(id), "new runtime should be starting");
        assertTrue(manager.activeProcesses().isEmpty(), "unstarted runtimes have no processes");

        manager.setResourceName(created, "renamed");
        manager.dispose(id, retained, true);
        manager.dispose(created, true);

        assertNull(manager.state(id), "disposed retained runtime should be removed");
    }

    @Test
    void disposingUnknownRuntimeIsHarmless() {
        final TerminalManager manager = TerminalManager.get();
        final var runtime =
                new TerminalRuntime(
                        TRUE_COMMAND, TEMP_DIRECTORY, TEMP_DIRECTORY.resolve("history").toString());

        manager.dispose(runtime, false);

        assertTrue(manager.activeProcesses().isEmpty(), "unknown runtimes should not be active");
    }

    @Test
    void reportsActiveProcessAndCanDisposeWithoutDeletingHistory() throws InterruptedException {
        final TerminalManager manager = TerminalManager.get();
        final TerminalRuntime runtime = manager.create("sleep 1", TEMP_DIRECTORY, RESOURCE);
        runtime.start(ignored -> {}, exception -> {});

        AsyncTestSupport.await(
                () -> runtime.process() != null, "runtime should expose its started process");

        assertTrue(runtime.process() != null, "runtime should expose its started process");
        assertEquals(1, manager.activeProcesses().size(), "active process should be reported");
        assertEquals(
                RESOURCE,
                manager.activeProcesses().get(0).name(),
                "active process command should match");
        manager.dispose(runtime, false);
        assertTrue(manager.activeProcesses().isEmpty(), "disposed process should be removed");
    }

    @Test
    void doesNotReportExitedProcessAsActive() throws InterruptedException {
        final TerminalManager manager = TerminalManager.get();
        final TerminalRuntime runtime = manager.create(TRUE_COMMAND, TEMP_DIRECTORY, RESOURCE);
        runtime.start(ignored -> {}, exception -> {});

        AsyncTestSupport.await(
                () -> runtime.process() != null && !runtime.process().isAlive(),
                "runtime process should exit");

        assertTrue(manager.activeProcesses().isEmpty(), "exited process should not be reported");
        manager.dispose(runtime, false);
    }

    @Test
    void disposingWithHistoryDeletionRemovesTheHistoryFile(@TempDir final Path directory)
            throws IOException {
        final Path history = directory.resolve("terminal.history");
        Files.writeString(history, "history");
        final TerminalRuntime runtime =
                new TerminalRuntime(TRUE_COMMAND, TEMP_DIRECTORY, history.toString());

        TerminalManager.get().dispose(runtime, true);

        assertFalse(Files.exists(history), "disposing with deletion should remove history");
    }
}
