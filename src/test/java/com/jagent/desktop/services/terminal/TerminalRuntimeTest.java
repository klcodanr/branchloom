package com.jagent.desktop.services.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.test.AsyncTestSupport;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TerminalRuntimeTest {
    private static final Path TEMP_DIRECTORY = Path.of(System.getProperty("java.io.tmpdir"));
    private static final String HISTORY_FILE = TEMP_DIRECTORY.resolve("history").toString();

    @Test
    void exposesConfigurationAndNotifiesInitialState() {
        final var runtime = new TerminalRuntime("sh", TEMP_DIRECTORY, HISTORY_FILE);
        final var notified = new AtomicReference<TerminalState>();

        runtime.onStateChanged(notified::set);

        assertEquals(
                TerminalState.STARTING, runtime.state(), "runtime should start in starting state");
        assertEquals(
                TerminalState.STARTING, notified.get(), "listener should receive current state");
        assertEquals(HISTORY_FILE, runtime.historyFile(), "history path should be retained");
        assertNull(runtime.process(), "process should not exist before start");
    }

    @Test
    void disposedRuntimeRejectsStartAndCanBeStoppedRepeatedly() {
        final var runtime = new TerminalRuntime("sh", TEMP_DIRECTORY, HISTORY_FILE);
        final var failure = new AtomicReference<Exception>();
        runtime.stop();

        runtime.start(ignored -> {}, failure::set);
        runtime.stop();

        assertEquals(TerminalState.STOPPED, runtime.state(), "stopping should update state");
        assertTrue(failure.get() instanceof IllegalStateException, "start should report disposal");
    }

    @Test
    void startsProcessReadsOutputAndIgnoresDuplicateStart()
            throws IOException, InterruptedException {
        final var runtime =
                new TerminalRuntime("printf ready; sleep 1", TEMP_DIRECTORY, HISTORY_FILE);
        final var attached = new CountDownLatch(1);
        final var connector = new AtomicReference<TtyConnector>();
        final var failure = new AtomicReference<Exception>();
        final var states = new AtomicReference<TerminalState>();
        runtime.onStateChanged(states::set);

        runtime.start(
                tty -> {
                    connector.set(tty);
                    attached.countDown();
                },
                failure::set);
        runtime.start(ignored -> {}, failure::set);

        assertTrue(attached.await(5, java.util.concurrent.TimeUnit.SECONDS), "PTY should attach");
        runtime.submitCommand();
        final char[] buffer = new char[32];
        final StringBuilder output = new StringBuilder();
        int count;
        do {
            count = connector.get().read(buffer, 0, buffer.length);
            output.append(buffer, 0, count);
        } while (!output.toString().contains("ready"));
        connector.get().resize(new TermSize(100, 30));

        assertTrue(output.toString().contains("ready"), "PTY output should be readable");
        assertEquals(TerminalState.WORKING, states.get(), "output should mark terminal working");
        assertNull(failure.get(), "starting a valid command should not fail");
        connector.get().close();
        runtime.stop();
    }

    @Test
    void reportsLaunchFailureAndStopsRunningProcess() throws InterruptedException {
        final var failed = new TerminalRuntime("true", null, HISTORY_FILE);
        final var failure = new AtomicReference<Exception>();
        failed.start(ignored -> {}, failure::set);

        AsyncTestSupport.await(
                () -> failed.state() == TerminalState.FAILED, "runtime should reach failed state");
        AsyncTestSupport.await(() -> failure.get() != null, "runtime should report launch failure");
        assertEquals(TerminalState.FAILED, failed.state(), "failed process should fail");
        assertTrue(failure.get() != null, "invalid directory should report launch failure");

        final var running = new TerminalRuntime("sleep 10", TEMP_DIRECTORY, HISTORY_FILE);
        final var attached = new CountDownLatch(1);
        running.start(ignored -> attached.countDown(), failure::set);
        assertTrue(attached.await(5, java.util.concurrent.TimeUnit.SECONDS), "PTY should attach");
        running.stop();

        assertEquals(TerminalState.STOPPED, running.state(), "stop should update state");
    }

    @Test
    void nullStateListenerIsSafeAndEnumLabelsAreExposed() {
        final var runtime = new TerminalRuntime("true", TEMP_DIRECTORY, HISTORY_FILE);

        runtime.onStateChanged(null);

        assertEquals("Starting", TerminalState.STARTING.label(), "starting label should match");
        assertEquals("Working", TerminalState.WORKING.label(), "working label should match");
        assertEquals("No recent output", TerminalState.IDLE.label(), "idle label should match");
        assertEquals("Exited", TerminalState.EXITED.label(), "exited label should match");
        assertEquals("Failed", TerminalState.FAILED.label(), "failed label should match");
        assertEquals("Stopped", TerminalState.STOPPED.label(), "stopped label should match");
    }

    @Test
    void keepsInteractiveShellAfterCommandsComplete() throws IOException, InterruptedException {
        final var successful = new TerminalRuntime("printf done", TEMP_DIRECTORY, HISTORY_FILE);
        final var successfulAttached = new CountDownLatch(1);
        final var successfulConnector = new AtomicReference<TtyConnector>();
        successful.start(
                connector -> {
                    successfulConnector.set(connector);
                    successfulAttached.countDown();
                },
                exception -> {});
        assertTrue(
                successfulAttached.await(5, java.util.concurrent.TimeUnit.SECONDS),
                "successful terminal should attach");
        successful.submitCommand();
        successfulConnector.get().read(new char[32], 0, 32);

        final var failed = new TerminalRuntime("false", TEMP_DIRECTORY, HISTORY_FILE);
        final var failedAttached = new CountDownLatch(1);
        final var failedConnector = new AtomicReference<TtyConnector>();
        failed.start(
                connector -> {
                    failedConnector.set(connector);
                    failedAttached.countDown();
                },
                exception -> {});
        assertTrue(
                failedAttached.await(5, java.util.concurrent.TimeUnit.SECONDS),
                "failed terminal should attach");
        failed.submitCommand();
        failedConnector.get().read(new char[32], 0, 32);

        assertTrue(
                successful.process().isAlive(),
                "successful command should leave shell alive after completion");
        assertTrue(
                failed.process().isAlive(),
                "failed command should leave shell alive after completion");
        successful.stop();
        failed.stop();
    }

    @Test
    void marksQuietRunningProcessIdle() throws InterruptedException {
        final var runtime = new TerminalRuntime("sleep 10", TEMP_DIRECTORY, HISTORY_FILE);
        final var attached = new CountDownLatch(1);
        try {
            runtime.start(ignored -> attached.countDown(), ignored -> {});

            assertTrue(
                    attached.await(5, java.util.concurrent.TimeUnit.SECONDS), "PTY should attach");
            AsyncTestSupport.await(
                    () -> runtime.state() == TerminalState.IDLE,
                    "quiet terminal should become idle");
        } finally {
            runtime.stop();
        }
        assertEquals(TerminalState.STOPPED, runtime.state(), "cleanup should stop the runtime");
    }
}
