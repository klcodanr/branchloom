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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TerminalRuntimeTest {
    private static final Path TEMP_DIRECTORY = Path.of(System.getProperty("java.io.tmpdir"));
    private static final String HISTORY_FILE = TEMP_DIRECTORY.resolve("history").toString();

    @Test
    void exposesConfigurationAndNotifiesInitialState() {
        final var runtime =
                new StubTerminalRuntime(
                        "sh", TEMP_DIRECTORY, HISTORY_FILE, new FakePtyProcess(101));
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
        final var runtime =
                new StubTerminalRuntime(
                        "sh", TEMP_DIRECTORY, HISTORY_FILE, new FakePtyProcess(102));
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
        final FakePtyProcess process = new FakePtyProcess(103);
        process.emit("ready");
        final var runtime =
                new StubTerminalRuntime(
                        "printf ready; sleep 1", TEMP_DIRECTORY, HISTORY_FILE, process);
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

        assertTrue(attached.await(2, TimeUnit.SECONDS), "PTY should attach");
        runtime.submitCommand();
        assertEquals(
                "printf ready; sleep 1\r\n",
                process.submittedInput(),
                "command should submit immediately after attach");
        final char[] buffer = new char[32];
        final StringBuilder output = new StringBuilder();
        int count;
        do {
            count = connector.get().read(buffer, 0, buffer.length);
            if (count > 0) {
                output.append(buffer, 0, count);
            }
        } while (count > 0 && !output.toString().contains("ready"));
        connector.get().resize(new TermSize(100, 30));

        assertTrue(output.toString().contains("ready"), "PTY output should be readable");
        assertEquals(TerminalState.WORKING, states.get(), "output should mark terminal working");
        assertNull(failure.get(), "starting a valid command should not fail");
        assertEquals("printf ready; sleep 1\r\n", process.submittedInput(), "command submitted");
        connector.get().close();
        runtime.stop();
    }

    @Test
    void reportsLaunchFailureAndStopsRunningProcess() throws InterruptedException {
        final var failed =
                new StubTerminalRuntime(
                        "true",
                        TEMP_DIRECTORY,
                        HISTORY_FILE,
                        new FakePtyProcess(104),
                        new IOException("boom"));
        final var failure = new AtomicReference<Exception>();
        failed.start(ignored -> {}, failure::set);

        AsyncTestSupport.await(
                () -> failed.state() == TerminalState.FAILED, "runtime should reach failed state");
        AsyncTestSupport.await(() -> failure.get() != null, "runtime should report launch failure");
        assertEquals(TerminalState.FAILED, failed.state(), "failed process should fail");
        assertTrue(failure.get() != null, "launch failure should be reported");

        final FakePtyProcess runningProcess = new FakePtyProcess(105);
        final var running =
                new StubTerminalRuntime("sleep 10", TEMP_DIRECTORY, HISTORY_FILE, runningProcess);
        final var attached = new CountDownLatch(1);
        running.start(ignored -> attached.countDown(), failure::set);
        assertTrue(attached.await(2, TimeUnit.SECONDS), "PTY should attach");
        running.stop();

        assertEquals(TerminalState.STOPPED, running.state(), "stop should update state");
        assertTrue(!runningProcess.isAlive(), "stop should terminate the running process");
    }

    @Test
    void nullStateListenerIsSafeAndEnumLabelsAreExposed() {
        final var runtime =
                new StubTerminalRuntime(
                        "true", TEMP_DIRECTORY, HISTORY_FILE, new FakePtyProcess(106));

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
        final FakePtyProcess successfulProcess = new FakePtyProcess(107);
        successfulProcess.emit("done");
        final var successful =
                new StubTerminalRuntime(
                        "printf done", TEMP_DIRECTORY, HISTORY_FILE, successfulProcess);
        final var successfulAttached = new CountDownLatch(1);
        final var successfulConnector = new AtomicReference<TtyConnector>();
        successful.start(
                connector -> {
                    successfulConnector.set(connector);
                    successfulAttached.countDown();
                },
                exception -> {});
        assertTrue(successfulAttached.await(2, TimeUnit.SECONDS), "terminal should attach");
        successful.submitCommand();
        successfulConnector.get().read(new char[32], 0, 32);

        final FakePtyProcess failedProcess = new FakePtyProcess(108);
        failedProcess.emit("failed");
        final var failed =
                new StubTerminalRuntime("false", TEMP_DIRECTORY, HISTORY_FILE, failedProcess);
        final var failedAttached = new CountDownLatch(1);
        final var failedConnector = new AtomicReference<TtyConnector>();
        failed.start(
                connector -> {
                    failedConnector.set(connector);
                    failedAttached.countDown();
                },
                exception -> {});
        assertTrue(failedAttached.await(2, TimeUnit.SECONDS), "terminal should attach");
        failed.submitCommand();
        failedConnector.get().read(new char[32], 0, 32);

        assertTrue(successful.process().isAlive(), "successful command should keep shell alive");
        assertTrue(failed.process().isAlive(), "failing command should keep shell alive");
        successful.stop();
        failed.stop();
    }

    @Test
    void marksQuietRunningProcessIdle() throws InterruptedException {
        final var runtime =
                new StubTerminalRuntime(
                        "sleep 10", TEMP_DIRECTORY, HISTORY_FILE, new FakePtyProcess(109));
        final var attached = new CountDownLatch(1);
        try {
            runtime.start(ignored -> attached.countDown(), ignored -> {});

            assertTrue(attached.await(2, TimeUnit.SECONDS), "PTY should attach");
            AsyncTestSupport.await(
                    () -> runtime.state() == TerminalState.IDLE,
                    "quiet terminal should become idle");
        } finally {
            runtime.stop();
        }
        assertEquals(TerminalState.STOPPED, runtime.state(), "cleanup should stop the runtime");
    }
}
