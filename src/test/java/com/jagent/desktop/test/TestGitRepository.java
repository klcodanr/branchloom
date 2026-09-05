package com.jagent.desktop.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jagent.desktop.services.PlatformCommands;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Small real-Git fixture helpers shared by service tests. */
@SuppressWarnings("PMD.TestClassWithoutTestCases")
public final class TestGitRepository {
    private TestGitRepository() {}

    public static void initialize(final Path directory) throws IOException, InterruptedException {
        run(directory, "git init -q -b master");
        run(directory, "git config user.name test && git config user.email test");
        run(directory, "printf 'content' > tracked.txt");
        run(directory, "git add tracked.txt && git commit -qm initial");
    }

    public static void run(final Path directory, final String command)
            throws IOException, InterruptedException {
        output(directory, command);
    }

    public static String output(final Path directory, final String command)
            throws IOException, InterruptedException {
        final Process process =
                new ProcessBuilder(PlatformCommands.shell(command))
                        .directory(directory.toFile())
                        .redirectErrorStream(true)
                        .start();
        final String output =
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), "test Git command should succeed: " + output);
        return output;
    }
}
