package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
