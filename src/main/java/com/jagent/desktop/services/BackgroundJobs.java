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

    public record Job(UUID id, String title, Status status, String message) {}

    public final class Handle {
        private final UUID id;

        private Handle(final UUID id) {
            this.id = id;
        }

        public void update(final String message) {
            updateJob(new Job(id, job(id).title(), Status.RUNNING, message));
        }

        public void complete() {
            updateJob(new Job(id, job(id).title(), Status.SUCCEEDED, "Complete"));
        }

        public void fail(final String message) {
            updateJob(new Job(id, job(id).title(), Status.FAILED, message));
        }
    }

    public Handle start(final String title) {
        final UUID id = UUID.randomUUID();
        jobs.put(id, new Job(id, title, Status.RUNNING, "Starting..."));
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
