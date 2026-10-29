package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class BackgroundJobsTest {
    @Test
    void reportsJobLifecycleAndUpdates() {
        final BackgroundJobs jobs = new BackgroundJobs();
        final var updates = new ArrayList<java.util.List<BackgroundJobs.Job>>();
        jobs.listen(updates::add);

        final var handle = jobs.start("Session setup");
        handle.update("Running startup command 1 of 2");
        handle.complete();

        final var job = jobs.jobs().getFirst();
        assertEquals("Session setup", job.title(), "job title should be retained");
        assertEquals(
                BackgroundJobs.Status.SUCCEEDED, job.status(), "job should complete successfully");
        assertEquals("Complete", job.message(), "completion message should be reported");
        assertEquals(4, updates.size(), "listeners should receive each lifecycle update");
    }

    @Test
    void retainsJobContextAndConsoleOutput() {
        final BackgroundJobs jobs = new BackgroundJobs();
        final var handle = jobs.start("Session setup", "Branchloom", "Fix login");

        handle.output("npm install");
        handle.output("npm test");

        final var job = jobs.jobs().getFirst();
        assertEquals("Branchloom", job.project(), "project context should be retained");
        assertEquals("Fix login", job.session(), "session context should be retained");
        assertEquals(
                "npm install" + System.lineSeparator() + "npm test",
                job.output(),
                "console output should be retained");
    }

    @Test
    void reportsFailedAndCancelledJobsAndIgnoresUpdatesAfterCompletion() {
        final BackgroundJobs jobs = new BackgroundJobs();
        final var failed = jobs.start("Failed job");
        failed.fail("Setup failed");
        failed.update("Late update");

        assertEquals(
                BackgroundJobs.Status.FAILED,
                jobs.jobs().stream()
                        .filter(job -> "Failed job".equals(job.title()))
                        .findFirst()
                        .orElseThrow()
                        .status(),
                "failed jobs should retain their terminal status");
        assertEquals(
                "Setup failed",
                jobs.jobs().stream()
                        .filter(job -> "Failed job".equals(job.title()))
                        .findFirst()
                        .orElseThrow()
                        .message(),
                "late updates should not replace a failure message");

        final var cancelled = jobs.start("Cancelled job");
        cancelled.cancel();
        cancelled.output("Late output");

        assertEquals(
                BackgroundJobs.Status.CANCELLED,
                jobs.jobs().stream()
                        .filter(job -> "Cancelled job".equals(job.title()))
                        .findFirst()
                        .orElseThrow()
                        .status(),
                "cancelled jobs should retain their terminal status");
        assertEquals(
                "Cancelled",
                jobs.jobs().stream()
                        .filter(job -> "Cancelled job".equals(job.title()))
                        .findFirst()
                        .orElseThrow()
                        .message(),
                "late output should not replace a cancellation message");
    }

    @Test
    void boundsRetainedOutputAndCompletedJobs() {
        final BackgroundJobs jobs = new BackgroundJobs();
        final var handle = jobs.start("Large output");

        for (int index = 0; index < 5_001; index++) {
            handle.output("line " + index);
        }
        assertEquals(
                "line 1",
                jobs.jobs().getFirst().output().lines().findFirst().orElseThrow(),
                "oldest output lines should be evicted");
        assertEquals(
                5_000,
                jobs.jobs().getFirst().output().lines().count(),
                "retained output lines should be capped");

        handle.complete();
        for (int index = 0; index < 101; index++) {
            final var completed = jobs.start("Job " + index);
            completed.complete();
        }

        assertTrue(jobs.jobs().size() <= 100, "completed job history should be bounded");
    }
}
