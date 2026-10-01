package com.jagent.desktop.services.terminal;

import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.services.PlatformCommands;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns one terminal process and its lifecycle monitors. */
public class TerminalRuntime {
    private static final long QUIET_PERIOD_MILLIS = 4000;
    private static final Logger LOG = LoggerFactory.getLogger(TerminalRuntime.class);
    private final String command;
    private final Path directory;
    private final String historyFile;
    private final Object lifecycleLock = new Object();
    protected volatile PtyProcess process;
    protected volatile TtyConnector connector;
    private volatile TerminalState state = TerminalState.STARTING;
    private volatile Consumer<TerminalState> stateChanged = ignored -> {};
    protected volatile boolean disposed;
    protected volatile boolean started;
    private volatile long lastOutputAt;
    private volatile long startRequestedAtNanos;
    protected volatile CompletableFuture<Void> launchTask;
    protected volatile Thread launchThread;

    public TerminalRuntime(final String command, final Path directory, final String historyFile) {
        this.command = command;
        this.directory = directory;
        this.historyFile = historyFile;
    }

    public void start(final Consumer<TtyConnector> attach, final Consumer<Exception> failed) {
        synchronized (lifecycleLock) {
            if (disposed) {
                final IllegalStateException exception =
                        new IllegalStateException("Terminal runtime has already been disposed.");
                LOG.error("Cannot start terminal at {}", directory, exception);
                failed.accept(exception);
                return;
            }
            if (started) {
                return;
            }
            started = true;
            startRequestedAtNanos = System.nanoTime();
            setState(TerminalState.STARTING);
            launchTask =
                    BackgroundOperations.submit(
                            "Terminals",
                            "agent-terminal-start",
                            Executors.callable(() -> startProcess(attach, failed), null));
        }
    }

    public void onStateChanged(final Consumer<TerminalState> listener) {
        stateChanged = listener == null ? ignored -> {} : listener;
        stateChanged.accept(state);
    }

    public TerminalState state() {
        return state;
    }

    public PtyProcess process() {
        return process;
    }

    public String historyFile() {
        return historyFile;
    }

    public Path directory() {
        return directory;
    }

    public void stop() {
        stopAndWait();
    }

    public void stopAndWait() {
        TerminalRuntimeShutdown.stop(this);
    }

    protected TerminalRuntimeShutdown.StopState prepareStop() {
        synchronized (lifecycleLock) {
            disposed = true;
            setState(TerminalState.STOPPED);
            final CompletableFuture<Void> task = launchTask;
            final Thread startingThread = launchThread;
            if (connector != null) {
                connector.close();
            }
            final PtyProcess processAtStop = process;
            if (startingThread != null) {
                startingThread.interrupt();
            }
            if (processAtStop != null && processAtStop.isAlive()) {
                processAtStop.destroy();
            }
            return new TerminalRuntimeShutdown.StopState(task, startingThread, processAtStop);
        }
    }

    protected PtyProcess runningProcess(final PtyProcess processAtStop) {
        synchronized (lifecycleLock) {
            final PtyProcess runningProcess = process == null ? processAtStop : process;
            if (runningProcess != null && runningProcess.isAlive()) {
                runningProcess.destroy();
            }
            return runningProcess;
        }
    }

    private void startProcess(
            final Consumer<TtyConnector> attach, final Consumer<Exception> failed) {
        launchThread = Thread.currentThread();
        final long launchStartedAtNanos = System.nanoTime();
        try {
            final PtyProcess startedProcess = launchProcess();
            LOG.info(
                    "Terminal process started: dir={}, cmdLength={}, launchMs={}, sinceStartRequestMs={}",
                    directory,
                    command.length(),
                    (System.nanoTime() - launchStartedAtNanos) / 1_000_000,
                    (System.nanoTime() - startRequestedAtNanos) / 1_000_000);
            synchronized (lifecycleLock) {
                if (disposed || Thread.currentThread().isInterrupted()) {
                    startedProcess.destroy();
                    return;
                }
                process = startedProcess;
                lastOutputAt = System.currentTimeMillis();
                final TtyConnector tty = new Pty4jTtyConnector(startedProcess, this::setState);
                connector = tty;
                final long attachStartedAtNanos = System.nanoTime();
                attach.accept(tty);
                LOG.info(
                        "Terminal connector attached: dir={}, cmdLength={}, attachMs={}, sinceStartRequestMs={}",
                        directory,
                        command.length(),
                        (System.nanoTime() - attachStartedAtNanos) / 1_000_000,
                        (System.nanoTime() - startRequestedAtNanos) / 1_000_000);
            }
            BackgroundOperations.submit(
                    "Terminals",
                    "agent-terminal-monitor",
                    Executors.callable(() -> monitorExit(startedProcess), null));
            BackgroundOperations.submit(
                    "Terminals",
                    "agent-terminal-activity",
                    Executors.callable(() -> monitorActivity(startedProcess), null));
        } catch (Exception exception) {
            if (disposed) {
                return;
            }
            setState(TerminalState.FAILED);
            LOG.error("Terminal process at {}: {}", directory, command, exception);
            failed.accept(exception);
        } finally {
            launchThread = null;
        }
    }

    public void submitCommand() {
        final long submitStartedAtNanos = System.nanoTime();
        final PtyProcess runningProcess = process;
        if (runningProcess != null
                && runningProcess.isAlive()
                && !command.equals(PlatformCommands.userShell())) {
            writeCommand(runningProcess);
            LOG.info(
                    "Terminal command submitted: cmd={}, submitMs={}, sinceStartRequestMs={}",
                    command.length(),
                    (System.nanoTime() - submitStartedAtNanos) / 1_000_000,
                    (System.nanoTime() - startRequestedAtNanos) / 1_000_000);
        }
    }

    protected PtyProcess launchProcess() throws IOException {
        final Map<String, String> environment = new HashMap<>(System.getenv());
        environment.put("TERM", "xterm-256color");
        environment.put("COLORTERM", "truecolor");
        environment.putIfAbsent("LANG", "en_US.UTF-8");
        environment.put("HISTFILE", historyFile);
        environment.put("HISTSIZE", "10000");
        environment.put("SAVEHIST", "10000");
        return new PtyProcessBuilder(PlatformCommands.terminal())
                .setDirectory(directory.toString())
                .setEnvironment(environment)
                .setConsole(false)
                .setInitialColumns(80)
                .setInitialRows(24)
                .start();
    }

    private void monitorExit(final PtyProcess startedProcess) {
        try {
            final int exitCode = startedProcess.waitFor();
            if (!disposed) {
                setState(exitCode == 0 ? TerminalState.EXITED : TerminalState.FAILED);
            }
            if (exitCode != 0) {
                LOG.error(
                        "Terminal process at {} exited with status {}: {}",
                        directory,
                        exitCode,
                        command);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void writeCommand(final PtyProcess terminalProcess) {
        try {
            terminalProcess
                    .getOutputStream()
                    .write((command + "\r\n").getBytes(StandardCharsets.UTF_8));
            terminalProcess.getOutputStream().flush();
        } catch (IOException exception) {
            LOG.warn("Could not submit terminal command: {}", command, exception);
        }
    }

    private void monitorActivity(final PtyProcess startedProcess) {
        while (!disposed && startedProcess.isAlive()) {
            if (System.currentTimeMillis() - lastOutputAt >= QUIET_PERIOD_MILLIS) {
                setState(TerminalState.IDLE);
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    protected void setState(final TerminalState next) {
        if (state != next) {
            state = next;
            stateChanged.accept(next);
        }
    }

    private final class Pty4jTtyConnector extends ProcessTtyConnector {
        private final PtyProcess process;
        private final Consumer<TerminalState> activity;

        private Pty4jTtyConnector(
                final PtyProcess process, final Consumer<TerminalState> activity) {
            super(process, StandardCharsets.UTF_8);
            this.process = process;
            this.activity = activity;
        }

        @Override
        public int read(final char[] buffer, final int offset, final int length)
                throws IOException {
            final int count = super.read(buffer, offset, length);
            if (count > 0) {
                lastOutputAt = System.currentTimeMillis();
                activity.accept(TerminalState.WORKING);
            }
            return count;
        }

        @Override
        public void resize(final TermSize size) {
            if (isConnected()) {
                try {
                    process.setWinSize(new com.pty4j.WinSize(size.getColumns(), size.getRows()));
                } catch (RuntimeException ignored) {
                    // Some macOS PTY configurations reject a resize ioctl.
                }
            }
        }

        @Override
        public String getName() {
            return "Local";
        }
    }
}
