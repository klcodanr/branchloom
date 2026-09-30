package com.jagent.desktop.ui.dialogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.async.ProgressOperation;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProgressOperationTest {
    private static final String TITLE = "Test operation";
    private static final String MESSAGE = "Working";

    @Test
    void runsOperationAndInvokesSuccessCallback() throws InterruptedException, ExecutionException {
        final CountDownLatch completed = new CountDownLatch(1);

        ProgressOperation.run(
                        null,
                        TITLE,
                        MESSAGE,
                        () -> {
                            return "result";
                        })
                .thenRun(completed::countDown)
                .get();

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
                        })
                .exceptionally(
                        exception -> {
                            final Throwable cause =
                                    exception.getCause() == null ? exception : exception.getCause();
                            failure.set(cause);
                            completed.countDown();
                            return null;
                        });

        assertTrue(completed.await(5, TimeUnit.SECONDS), "failure callback should complete");
        assertEquals("failed", failure.get().getMessage(), "failure should be reported");
    }
}
