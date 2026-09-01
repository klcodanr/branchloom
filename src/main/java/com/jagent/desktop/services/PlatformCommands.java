package com.jagent.desktop.services;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Platform-specific command construction shared by process and UI services. */
public final class PlatformCommands {
    private static final Logger LOG = Logger.getLogger(PlatformCommands.class.getName());

    private PlatformCommands() {}

    public static String[] shell(final String command) {
        return isWindows()
                ? new String[] {userShell(), "/c", command}
                : new String[] {userShell(), "-c", command};
    }

    public static String[] terminal(final String command, final Path directory) {
        if (isWindows()) {
            return new String[] {userShell(), "/d", "/s", "/c", command};
        }
        final String shell = userShell();
        if (command.equals(shell)) {
            return new String[] {
                shell, "-ilc", "cd " + quote(directory.toString()) + " && exec " + shell + " -il"
            };
        }
        return new String[] {shell, "-ilc", "cd " + quote(directory.toString()) + " && " + command};
    }

    public static String userShell() {
        if (isWindows()) {
            final String commandShell = System.getenv("ComSpec");
            return commandShell == null || commandShell.isBlank() ? "cmd" : commandShell;
        }
        final String shell = System.getenv("SHELL");
        return shell == null || shell.isBlank() ? "/bin/sh" : shell;
    }

    public static String terminalCommand() {
        if (isMac()) {
            return "open -a Terminal .";
        }
        if (isWindows()) {
            return "start cmd";
        }
        return "x-terminal-emulator .";
    }

    public static void openUrl(final String url) {
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to open URL: " + url, e);
        }
    }

    public static boolean commandAvailable(final String executable) {
        final String lookup = isWindows() ? "where " + executable : "command -v " + executable;
        try {
            final Process process = new ProcessBuilder(shell(lookup)).start();
            return process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static boolean isWindows() {
        return osName().contains("win");
    }

    public static boolean isMac() {
        return osName().contains("mac");
    }

    private static String osName() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT);
    }

    private static String quote(final String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
