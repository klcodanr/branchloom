package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PlatformCommandsTest {
    private static final String CONDITION_MESSAGE = "platform condition should hold";

    @Test
    void buildsShellAndInteractiveTerminalCommands() {
        assertArrayEquals(
                new String[] {PlatformCommands.userShell(), "-c", "printf test"},
                PlatformCommands.shell("printf test"),
                "shell command should use the configured shell");
        assertArrayEquals(
                new String[] {
                    PlatformCommands.userShell(),
                    "-ilc",
                    "exec " + PlatformCommands.userShell() + " -il"
                },
                PlatformCommands.terminal(),
                "terminal should use the configured interactive shell");
    }

    @Test
    void detectsAvailableAndUnavailableCommands() {
        assertTrue(PlatformCommands.commandAvailable("sh"), CONDITION_MESSAGE);
        assertFalse(
                PlatformCommands.commandAvailable("branchloom-command-that-does-not-exist"),
                CONDITION_MESSAGE);
        if (PlatformCommands.isMac()) {
            assertFalse(PlatformCommands.isWindows(), CONDITION_MESSAGE);
            assertTrue(PlatformCommands.terminalCommand().contains("Terminal"), CONDITION_MESSAGE);
        } else if (PlatformCommands.isWindows()) {
            assertTrue(PlatformCommands.terminalCommand().contains("cmd"), CONDITION_MESSAGE);
        } else {
            assertFalse(PlatformCommands.isMac(), CONDITION_MESSAGE);
            assertTrue(PlatformCommands.terminalCommand().contains("terminal"), CONDITION_MESSAGE);
        }
    }

    @Test
    void preparedShellUsesTheLoginEnvironmentPath() throws IOException, InterruptedException {
        final ProcessBuilder builder =
                PlatformCommands.prepare(
                        new ProcessBuilder(PlatformCommands.shell("command -v sh")));

        final Process process = builder.start();
        final String output =
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertEquals(0, process.waitFor(), "shell command should succeed");
        assertTrue(output.contains("sh"), "login shell PATH should resolve sh");
        assertFalse(builder.environment().get("PATH").isBlank(), "PATH should be populated");
    }
}
