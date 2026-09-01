package com.jagent.desktop.services;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Application-scoped executor for blocking background work. */
public final class BackgroundTasks {
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static final ConcurrentMap<String, Counters> GROUPS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Thread, String> ACTIVE_TASKS = new ConcurrentHashMap<>();

    private BackgroundTasks() {}

    public static CompletableFuture<Void> submit(
            final String group, final String name, final Runnable task) {
        return submit(
                group,
                name,
                () -> {
                    task.run();
                    return null;
                });
    }

    public static <T> CompletableFuture<T> submit(
            final String group, final String name, final Supplier<T> task) {
        final Counters counters = GROUPS.computeIfAbsent(group, ignored -> new Counters());
        counters.submitted.incrementAndGet();
        return CompletableFuture.supplyAsync(
                () -> {
                    final Thread current = Thread.currentThread();
                    current.setName(group + "/" + name);
                    ACTIVE_TASKS.put(current, name);
                    counters.active.incrementAndGet();
                    try {
                        return task.get();
                    } finally {
                        ACTIVE_TASKS.remove(current);
                        counters.active.decrementAndGet();
                        counters.completed.incrementAndGet();
                    }
                },
                EXECUTOR);
    }

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

    public static void shutdown() {
        EXECUTOR.shutdownNow();
    }

    private static final class Counters {
        private final AtomicLong submitted = new AtomicLong();
        private final AtomicLong active = new AtomicLong();
        private final AtomicLong completed = new AtomicLong();
    }

    public record ThreadSummary(
            long virtualThreads,
            long platformThreads,
            boolean shutdown,
            List<GroupSummary> groups,
            List<String> activeTasks) {}

    public record GroupSummary(String group, long submitted, long active, long completed) {}
}
