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
        final char[] buffer = new char[32];
        final int count = connector.get().read(buffer, 0, buffer.length);
        connector.get().resize(new TermSize(100, 30));

        assertEquals("ready", new String(buffer, 0, count), "PTY output should be readable");
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
    void reportsSuccessfulAndFailedProcessExit() throws InterruptedException {
        final var successful = new TerminalRuntime("true", TEMP_DIRECTORY, HISTORY_FILE);
        successful.start(ignored -> {}, exception -> {});
        AsyncTestSupport.await(
                () -> successful.state() == TerminalState.EXITED, "successful process should exit");

        final var failed = new TerminalRuntime("false", TEMP_DIRECTORY, HISTORY_FILE);
        failed.start(ignored -> {}, exception -> {});
        AsyncTestSupport.await(
                () -> failed.state() == TerminalState.FAILED, "failed process should exit");
        assertEquals(TerminalState.EXITED, successful.state(), "successful process should exit");
        assertEquals(TerminalState.FAILED, failed.state(), "failed process should fail");
    }
}
