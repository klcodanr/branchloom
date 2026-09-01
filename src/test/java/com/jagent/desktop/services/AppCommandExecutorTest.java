package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppCommandExecutorTest {
    @Test
    void expandsContextAndInvokesSuccessCallback(@TempDir final Path directory)
            throws InterruptedException {
        final CountDownLatch completed = new CountDownLatch(1);
        final AtomicReference<String> output = new AtomicReference<>();
        final Project project = new Project("Demo Project", directory.toString(), null);
        final Session session = new Session(null, "Feature", "agent", "prompt", null);

        new AppCommandExecutor(() -> project, () -> session, (title, message) -> {})
                .run("printf '{projectName}'", directory, completed::countDown);

        assertTrue(completed.await(5, TimeUnit.SECONDS), "success callback should complete");

        final CountDownLatch outputCompleted = new CountDownLatch(1);
        CommandRunner.run(
                "printf 'separate'",
                directory,
                output::set,
                outputCompleted::countDown,
                ignored -> {});

        assertTrue(outputCompleted.await(5, TimeUnit.SECONDS), "command should complete");
        assertEquals("separate", output.get(), "command output should be observable");
    }

    @Test
    void invokesFailureCallbackWithCommandOutput(@TempDir final Path directory)
            throws InterruptedException {
        final CountDownLatch failed = new CountDownLatch(1);
        final AtomicReference<String> title = new AtomicReference<>();
        final AtomicReference<String> message = new AtomicReference<>();
        final Project project = new Project("Demo", directory.toString(), null);
        final Session session = new Session(null, "Feature", "agent", "prompt", null);

        new AppCommandExecutor(
                        () -> project,
                        () -> session,
                        (failureTitle, failureMessage) -> {
                            title.set(failureTitle);
                            message.set(failureMessage);
                        })
                .run("printf 'failed'; exit 3", directory, null, failed::countDown);

        assertTrue(failed.await(5, TimeUnit.SECONDS), "failure callback should complete");
        assertEquals("Command failed", title.get(), "failure title should be reported");
        assertEquals("failed", message.get(), "failure output should be reported");
    }
}
