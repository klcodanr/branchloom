package com.jagent.desktop.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

/** Tracks background work that should be visible to the user. */
public final class BackgroundJobs {
    private static final int MAX_COMPLETED_JOBS = 100;
    private static final int MAX_OUTPUT_LINES = 5_000;
    private final Map<UUID, Job> jobs = new LinkedHashMap<>();
    private final List<Consumer<List<Job>>> listeners = new ArrayList<>();

    public enum Status {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    public record Job(
            UUID id,
            String title,
            String project,
            String session,
            Status status,
            String message,
            String output) {}

    public final class Handle {
        private final UUID id;
        private final BlockingQueue<String> outputLines =
                new ArrayBlockingQueue<>(MAX_OUTPUT_LINES);

        private Handle(final UUID id) {
            this.id = id;
        }

        public void update(final String message) {
            final Job current = job(id);
            updateJob(
                    new Job(
                            id,
                            current.title(),
                            current.project(),
                            current.session(),
                            Status.RUNNING,
                            message,
                            current.output()));
        }

        public void output(final String line) {
            final Job current = job(id);
            final String retainedOutput;
            synchronized (this) {
                if (!outputLines.offer(line)) {
                    outputLines.poll();
                    if (!outputLines.offer(line)) {
                        throw new IllegalStateException("Unable to retain background job output");
                    }
                }
                retainedOutput = String.join(System.lineSeparator(), outputLines);
            }
            updateJob(
                    new Job(
                            id,
                            current.title(),
                            current.project(),
                            current.session(),
                            current.status(),
                            current.message(),
                            retainedOutput));
        }

        public void complete() {
            final Job current = job(id);
            updateJob(
                    new Job(
                            id,
                            current.title(),
                            current.project(),
                            current.session(),
                            Status.SUCCEEDED,
                            "Complete",
                            current.output()));
        }

        public void fail(final String message) {
            final Job current = job(id);
            updateJob(
                    new Job(
                            id,
                            current.title(),
                            current.project(),
                            current.session(),
                            Status.FAILED,
                            message,
                            current.output()));
        }
    }

    public Handle start(final String title) {
        return start(title, "", "");
    }

    public Handle start(final String title, final String project, final String session) {
        final UUID id = UUID.randomUUID();
        synchronized (jobs) {
            jobs.put(id, new Job(id, title, project, session, Status.RUNNING, "Starting...", ""));
            trimCompletedJobs();
        }
        notifyListeners();
        return new Handle(id);
    }

    public List<Job> jobs() {
        synchronized (jobs) {
            return jobs.values().stream().toList();
        }
    }

    public void listen(final Consumer<List<Job>> listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
        listener.accept(jobs());
    }

    private Job job(final UUID id) {
        synchronized (jobs) {
            final Job job = jobs.get(id);
            if (job == null) {
                throw new IllegalStateException("Background job not found: " + id);
            }
            return job;
        }
    }

    private void updateJob(final Job job) {
        synchronized (jobs) {
            jobs.put(job.id(), job);
            trimCompletedJobs();
        }
        notifyListeners();
    }

    private void notifyListeners() {
        final List<Job> snapshot = jobs();
        synchronized (listeners) {
            listeners.forEach(listener -> listener.accept(snapshot));
        }
    }

    private void trimCompletedJobs() {
        long completed =
                jobs.values().stream().filter(job -> job.status() != Status.RUNNING).count();
        if (completed <= MAX_COMPLETED_JOBS) {
            return;
        }
        final var iterator = jobs.entrySet().iterator();
        while (iterator.hasNext()) {
            final Job job = iterator.next().getValue();
            if (job.status() != Status.RUNNING) {
                iterator.remove();
                completed--;
                if (completed <= MAX_COMPLETED_JOBS) {
                    return;
                }
            }
        }
    }
}
