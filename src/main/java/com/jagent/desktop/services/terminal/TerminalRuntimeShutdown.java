package com.jagent.desktop.services.terminal;

import com.pty4j.PtyProcess;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Performs bounded, synchronous cleanup for a terminal runtime. */
final class TerminalRuntimeShutdown {
    private static final long TIMEOUT_MILLIS = 2000;

    private TerminalRuntimeShutdown() {}

    public static void stop(final TerminalRuntime runtime) {
        final CompletableFuture<Void> task;
        final Thread startingThread;
        final PtyProcess processAtStop;
        final StopState stopState = runtime.prepareStop();
        task = stopState.task();
        startingThread = stopState.startingThread();
        processAtStop = stopState.process();
        if (task != null) {
            task.cancel(true);
            await(task);
        }
        await(startingThread);
        final PtyProcess runningProcess = runtime.runningProcess(processAtStop);
        if (runningProcess != null) {
            await(runningProcess);
            if (runningProcess.isAlive()) {
                runningProcess.destroyForcibly();
                await(runningProcess);
            }
        }
    }

    public record StopState(
            CompletableFuture<Void> task,
            Thread startingThread,
            PtyProcess process) {} // default access

    private static void await(final CompletableFuture<Void> task) {
        try {
            task.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (CancellationException
                | java.util.concurrent.ExecutionException
                | java.util.concurrent.TimeoutException ignored) {
            // Shutdown must continue when a native PTY launch does not respond.
        }
    }

    private static void await(final PtyProcess process) {
        try {
            process.waitFor(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(final Thread thread) {
        if (thread == null) {
            return;
        }
        try {
            thread.join(TIMEOUT_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
