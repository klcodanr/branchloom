package com.jagent.desktop.async;

import com.jagent.desktop.services.PlatformCommands;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.swing.SwingUtilities;

/**
 * Application-scoped executor for blocking background work.
 *
 * <p>Use this class when an operation may block (process execution, network access, disk I/O) and
 * should not run on the Swing event dispatch thread (EDT). Tasks are executed on virtual threads,
 * and completion is marshalled back onto the EDT so UI callers can update state safely.
 *
 * <p>For user-facing operations that should also show a loading indicator, use {@link
 * ProgressOperation}.
 */
public final class BackgroundOperations {
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static final ConcurrentMap<String, Counters> GROUPS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Thread, String> ACTIVE_TASKS = new ConcurrentHashMap<>();

    private BackgroundOperations() {}

    /**
     * Runs a task on a virtual background thread and completes the returned future on the EDT.
     *
     * <p>Use this as the default entry point for asynchronous work started by views and
     * coordinators. The {@code group} and {@code name} values are used for thread naming and
     * runtime diagnostics.
     *
     * @param <T> result type produced by the task
     * @param group logical task group for observability
     * @param name task name for observability
     * @param task blocking or long-running task body
     * @return future completed on the EDT with task result or failure
     */
    public static <T> CompletableFuture<T> submit(
            final String group, final String name, final Callable<T> task) {
        final Counters counters = GROUPS.computeIfAbsent(group, ignored -> new Counters());
        counters.submitted.incrementAndGet();
        final CompletableFuture<T> future = new CompletableFuture<>();

        EXECUTOR.execute(
                () -> {
                    final Thread current = Thread.currentThread();
                    try {
                        current.setName(group + "/" + name);
                        ACTIVE_TASKS.put(current, name);
                        counters.active.incrementAndGet();

                        final T result = task.call();
                        SwingUtilities.invokeLater(() -> future.complete(result));
                    } catch (Exception e) {
                        SwingUtilities.invokeLater(() -> future.completeExceptionally(e));
                    } finally {
                        ACTIVE_TASKS.remove(current);
                        counters.active.decrementAndGet();
                        counters.completed.incrementAndGet();
                    }
                });

        return future;
    }

    /**
     * Runs a shell command asynchronously and streams line output to an optional callback.
     *
     * <p>Use this helper when command output should be surfaced incrementally to the UI while the
     * command is still running. Command output callbacks are dispatched on the EDT.
     *
     * @param group logical task group for observability
     * @param name task name for observability
     * @param command shell command string
     * @param directory working directory for command execution
     * @param onOutput optional callback for each output line; may be {@code null}
     * @return future completed on the EDT with full combined command output
     */
    public static CompletableFuture<String> runCommand(
            final String group,
            final String name,
            final String command,
            final Path directory,
            final Consumer<String> onOutput) {
        return submit(
                group,
                name,
                () -> {
                    final ProcessBuilder builder =
                            PlatformCommands.prepare(
                                            new ProcessBuilder(PlatformCommands.shell(command)))
                                    .directory(directory.toFile())
                                    .redirectErrorStream(true);
                    try {
                        final Process process = builder.start();
                        final String output = readOutput(process, onOutput);
                        final int exitCode = process.waitFor();
                        if (exitCode != 0) {
                            PlatformCommands.logFailure(builder, exitCode, output);
                            throw new IllegalStateException(output.trim());
                        }
                        return output;
                    } catch (IOException exception) {
                        PlatformCommands.logStartFailure(builder, exception);
                        throw exception;
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw exception;
                    }
                });
    }

    private static String readOutput(final Process process, final Consumer<String> onOutput)
            throws IOException {
        final StringBuilder output = new StringBuilder();
        try (var reader = process.inputReader()) {
            while (true) {
                final String line = reader.readLine();
                if (line == null) {
                    break;
                }
                output.append(line).append(System.lineSeparator());
                if (onOutput != null) {
                    final String text = line;
                    SwingUtilities.invokeLater(() -> onOutput.accept(text));
                }
            }
        }
        return output.toString();
    }

    /**
     * Returns executor and task counters intended for diagnostics and tests.
     *
     * @return snapshot of thread counts, group counters, and active task names
     */
    public static ThreadSummary summary() {
        long virtual = 0;
        long platform = 0;
        for (final Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isVirtual()) {
                virtual++;
            } else {
                platform++;
            }
        }
        final Stream<GroupSummary> groups =
                GROUPS.entrySet().stream()
                        .map(
                                entry ->
                                        new GroupSummary(
                                                entry.getKey(),
                                                entry.getValue().submitted.get(),
                                                entry.getValue().active.get(),
                                                entry.getValue().completed.get()))
                        .sorted(Comparator.comparing(GroupSummary::group));
        return new ThreadSummary(
                virtual,
                platform,
                EXECUTOR.isShutdown(),
                groups.toList(),
                ACTIVE_TASKS.values().stream().sorted().toList());
    }

    /**
     * Stops all running tasks and shuts down the shared executor.
     *
     * <p>Intended for controlled shutdown and tests.
     */
    public static void shutdown() {
        EXECUTOR.shutdownNow();
    }

    private static final class Counters {
        private final AtomicLong submitted = new AtomicLong();
        private final AtomicLong active = new AtomicLong();
        private final AtomicLong completed = new AtomicLong();
    }

    /** Aggregate runtime task metrics across all groups. */
    public record ThreadSummary(
            long virtualThreads,
            long platformThreads,
            boolean shutdown,
            List<GroupSummary> groups,
            List<String> activeTasks) {}

    /** Per-group task counters used by {@link ThreadSummary}. */
    public record GroupSummary(String group, long submitted, long active, long completed) {}
}
