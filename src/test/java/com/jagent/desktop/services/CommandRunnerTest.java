package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandRunnerTest {
    @Test
    void reportsOutputAndSuccessOnTheEventDispatchThread(@TempDir final Path directory)
            throws InterruptedException {
        final CountDownLatch completed = new CountDownLatch(1);
        final List<String> output = new java.util.concurrent.CopyOnWriteArrayList<>();
        final AtomicReference<Boolean> successOnEdt = new AtomicReference<>();

        CommandRunner.run(
                "printf 'first\\nsecond\\n'",
                directory,
                output::add,
                () -> {
                    successOnEdt.set(javax.swing.SwingUtilities.isEventDispatchThread());
                    completed.countDown();
                },
                failure -> completed.countDown());

        assertTrue(completed.await(5, TimeUnit.SECONDS), "command should complete");
        assertEquals(List.of("first", "second"), output, "command output should be reported");
        assertEquals(Boolean.TRUE, successOnEdt.get(), "success callback should run on EDT");
    }

    @Test
    void reportsTrimmedFailureOutput(@TempDir final Path directory) throws InterruptedException {
        final CountDownLatch completed = new CountDownLatch(1);
        final AtomicReference<String> failure = new AtomicReference<>();

        CommandRunner.run(
                "printf 'failed\\n'; exit 7",
                directory,
                completed::countDown,
                message -> {
                    failure.set(message);
                    completed.countDown();
                });

        assertTrue(completed.await(5, TimeUnit.SECONDS), "failed command should complete");
        assertEquals("failed", failure.get(), "failure output should be trimmed");
    }

    @Test
    void reportsEmptyOutputForSuccessfulCommand(@TempDir final Path directory)
            throws InterruptedException {
        final CountDownLatch completed = new CountDownLatch(1);
        final AtomicReference<Boolean> success = new AtomicReference<>(false);

        CommandRunner.run(
                "true",
                directory,
                ignored -> {},
                () -> {
                    success.set(true);
                    completed.countDown();
                },
                ignored -> {});

        assertTrue(completed.await(5, TimeUnit.SECONDS), "empty command should complete");
        assertEquals(Boolean.TRUE, success.get(), "successful empty output should still complete");
    }

    @Test
    void reportsEmptyFailureOutputAndPreservesCallbackOrder(@TempDir final Path directory)
            throws InterruptedException {
        final CountDownLatch completed = new CountDownLatch(1);
        final List<String> callbacks = new java.util.concurrent.CopyOnWriteArrayList<>();
        final AtomicReference<String> failure = new AtomicReference<>("not-empty");

        CommandRunner.run(
                "exit 7",
                directory,
                line -> callbacks.add("output:" + line),
                () -> callbacks.add("success"),
                message -> {
                    callbacks.add("failure");
                    failure.set(message);
                    completed.countDown();
                });

        assertTrue(completed.await(5, TimeUnit.SECONDS), "failed command should complete");
        assertEquals("", failure.get(), "empty failure output should be reported as empty");
        assertEquals(
                List.of("failure"), callbacks, "failure should be the only completion callback");
    }
}
