package com.jagent.desktop.ui.dialogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProgressOperationTest {
    private static final String TITLE = "Test operation";
    private static final String MESSAGE = "Working";

    @Test
    void startsAndClosesInHeadlessMode() {
        final ProgressOperation operation = ProgressOperation.start(null, TITLE, MESSAGE);

        assertNotNull(operation, "headless operation should be created");
        operation.close();
    }

    @Test
    void runsOperationAndInvokesSuccessCallback() throws InterruptedException {
        final CountDownLatch completed = new CountDownLatch(1);

        ProgressOperation.run(
                null,
                TITLE,
                MESSAGE,
                () -> {
                    return "result";
                },
                completed::countDown,
                failure -> {});

        assertTrue(completed.await(5, TimeUnit.SECONDS), "success callback should complete");
    }

    @Test
    void reportsOperationFailureOnCallback() throws InterruptedException {
        final CountDownLatch completed = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        ProgressOperation.run(
                null,
                TITLE,
                MESSAGE,
                () -> {
                    throw new IllegalStateException("failed");
                },
                () -> {},
                exception -> {
                    failure.set(exception);
                    completed.countDown();
                });

        assertTrue(completed.await(5, TimeUnit.SECONDS), "failure callback should complete");
        assertEquals("failed", failure.get().getMessage(), "failure should be reported");
    }
}
