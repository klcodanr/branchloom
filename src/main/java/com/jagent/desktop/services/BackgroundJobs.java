package com.jagent.desktop.services;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/** Tracks background work that should be visible to the user. */
public final class BackgroundJobs {
    private final ConcurrentMap<UUID, Job> jobs = new ConcurrentHashMap<>();
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
            final String output =
                    current.output().isBlank()
                            ? line
                            : current.output() + System.lineSeparator() + line;
            updateJob(
                    new Job(
                            id,
                            current.title(),
                            current.project(),
                            current.session(),
                            current.status(),
                            current.message(),
                            output));
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
        jobs.put(id, new Job(id, title, project, session, Status.RUNNING, "Starting...", ""));
        notifyListeners();
        return new Handle(id);
    }

    public List<Job> jobs() {
        return jobs.values().stream().toList();
    }

    public void listen(final Consumer<List<Job>> listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
        listener.accept(jobs());
    }

    private Job job(final UUID id) {
        final Job job = jobs.get(id);
        if (job == null) {
            throw new IllegalStateException("Background job not found: " + id);
        }
        return job;
    }

    private void updateJob(final Job job) {
        jobs.put(job.id(), job);
        notifyListeners();
    }

    private void notifyListeners() {
        final List<Job> snapshot = jobs();
        synchronized (listeners) {
            listeners.forEach(listener -> listener.accept(snapshot));
        }
    }
}
