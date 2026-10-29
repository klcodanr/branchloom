package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.async.BackgroundOperations;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandRunnerTest {
    @Test
    void runCommandReportsOutputAndCompletes(@TempDir final Path directory)
            throws InterruptedException, ExecutionException {
        final CountDownLatch completed = new CountDownLatch(1);
        final List<String> output = new java.util.concurrent.CopyOnWriteArrayList<>();

        BackgroundOperations.runCommand(
                        "Commands",
                        "run-command-test-success",
                        "printf 'first\\nsecond\\n'",
                        directory,
                        output::add)
                .thenRun(
                        () -> {
                            completed.countDown();
                        });

        assertTrue(completed.await(5, TimeUnit.SECONDS), "command should complete");
        assertEquals(List.of("first", "second"), output, "command output should be reported");
    }

    @Test
    void runCommandFailsFutureForNonZeroExitCode(@TempDir final Path directory) {
        final var future =
                BackgroundOperations.runCommand(
                        "Commands",
                        "run-command-test-failure",
                        "printf 'failed\\n'; exit 7",
                        directory,
                        ignored -> {});
        final ExecutionException exception =
                assertThrows(ExecutionException.class, future::get, "command should fail");
        assertEquals(
                "failed", exception.getCause().getMessage(), "failure output should be trimmed");
    }

    @Test
    void runCommandSupportsSuccessfulCommandsWithNoOutput(@TempDir final Path directory)
            throws ExecutionException, InterruptedException {
        final Void output =
                BackgroundOperations.runCommand(
                                "Commands",
                                "run-command-test-empty-success",
                                "true",
                                directory,
                                ignored -> {})
                        .get();
        assertNull(output, "successful command without output should return empty output");
    }
}
