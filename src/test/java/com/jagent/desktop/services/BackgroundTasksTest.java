package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.async.BackgroundOperations;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class BackgroundTasksTest {
    @Test
    void tracksCompletedTasksAndCompletesFailuresExceptionally() {
        final String group = "coverage-tests";

        BackgroundOperations.submit(group, "success", () -> null).join();
        final var failure =
                BackgroundOperations.submit(
                        group,
                        "failure",
                        () -> {
                            throw new IllegalStateException("expected");
                        });
        assertThrows(CompletionException.class, failure::join);

        final var summary = BackgroundOperations.summary();
        final var groupSummary =
                summary.groups().stream()
                        .filter(entry -> entry.group().equals(group))
                        .findFirst()
                        .orElseThrow();
        assertEquals(2, groupSummary.submitted(), "both tasks should be submitted");
        assertEquals(2, groupSummary.completed(), "both tasks should complete");
        assertEquals(0, groupSummary.active(), "no tasks should remain active");
        assertTrue(summary.platformThreads() > 0, "platform thread count should be positive");
    }
}
