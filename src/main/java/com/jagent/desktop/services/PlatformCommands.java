package com.jagent.desktop.services;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Platform-specific command construction shared by process and UI services. */
@SuppressWarnings("PMD.GodClass")
public final class PlatformCommands {
    private static final Logger LOG = Logger.getLogger(PlatformCommands.class.getName());
    private static volatile String discoveredPath;

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
                shell,
                "-ilc",
                "cd " + shellQuote(directory.toString()) + " && exec " + shell + " -il"
            };
        }
        return new String[] {
            shell, "-ilc", "cd " + shellQuote(directory.toString()) + " && " + command
        };
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
            final Process process = prepare(new ProcessBuilder(shell(lookup))).start();
            return process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static ProcessBuilder prepare(final ProcessBuilder builder) {
        builder.environment().put("PATH", toolPath());
        return builder;
    }

    public static void logFailure(
            final ProcessBuilder builder, final int exitCode, final String output) {
        LOG.warning(
                "Command failed (exit code "
                        + exitCode
                        + "): "
                        + describe(builder)
                        + System.lineSeparator()
                        + "Output: "
                        + (output == null || output.isBlank() ? "<none>" : output.trim())
                        + System.lineSeparator()
                        + "Reproduce: "
                        + reproduce(builder));
    }

    public static void logStartFailure(final ProcessBuilder builder, final IOException exception) {
        LOG.log(
                Level.WARNING,
                "Could not start command: "
                        + describe(builder)
                        + System.lineSeparator()
                        + "Reason: "
                        + exception.getMessage()
                        + System.lineSeparator()
                        + "Reproduce: "
                        + reproduce(builder),
                exception);
    }

    public static String executable(final String name) {
        final String path = toolPath();
        for (final String directory :
                path.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            final Path candidate = Path.of(directory, name);
            if (Files.isExecutable(candidate)) {
                return candidate.toString();
            }
        }
        return name;
    }

    private static String toolPath() {
        final String cached = discoveredPath;
        if (cached != null) {
            return cached;
        }
        final List<String> directories = new ArrayList<>();
        final String current = System.getenv("PATH");
        if (current != null && !current.isBlank()) {
            directories.addAll(
                    List.of(
                            current.split(
                                    java.util.regex.Pattern.quote(java.io.File.pathSeparator))));
        }
        if (isMac()) {
            directories.add("/usr/bin");
            directories.add("/bin");
            directories.add("/usr/sbin");
            directories.add("/sbin");
            directories.add("/opt/homebrew/bin");
            directories.add("/usr/local/bin");
            directories.add("/opt/local/bin");
        }
        final String fallback =
                String.join(java.io.File.pathSeparator, directories.stream().distinct().toList());
        final String shellPath = discoverShellPath(fallback);
        discoveredPath = shellPath == null || shellPath.isBlank() ? fallback : shellPath;
        return discoveredPath;
    }

    private static String discoverShellPath(final String fallback) {
        if (isWindows()) {
            return null;
        }
        final String shell = userShell();
        final String marker = "__BRANCHLOOM_PATH__";
        final ProcessBuilder builder =
                new ProcessBuilder(shell, "-ilc", "printf '" + marker + "%s' \"$PATH\"");
        builder.environment().put("PATH", fallback);
        try {
            final Process process = builder.redirectErrorStream(true).start();
            final String output =
                    new String(
                            process.getInputStream().readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8);
            final int exitCode = process.waitFor();
            final int markerIndex = output.lastIndexOf(marker);
            if (exitCode == 0 && markerIndex >= 0) {
                final String path = output.substring(markerIndex + marker.length()).trim();
                LOG.info("Shell environment discovered using login shell " + shell);
                return path;
            }
            LOG.warning(
                    "Shell environment discovery failed for "
                            + shell
                            + " (exit code "
                            + exitCode
                            + "); using fallback PATH. Output: "
                            + output.trim());
        } catch (IOException exception) {
            LOG.log(
                    Level.WARNING,
                    "Shell environment discovery could not start for "
                            + shell
                            + "; using fallback PATH",
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOG.log(Level.WARNING, "Shell environment discovery was interrupted", exception);
        }
        return null;
    }

    private static String describe(final ProcessBuilder builder) {
        return commandLine(builder.command())
                + " (directory: "
                + (builder.directory() == null
                        ? Path.of(".").toAbsolutePath()
                        : builder.directory())
                + ")";
    }

    private static String reproduce(final ProcessBuilder builder) {
        final String directory =
                builder.directory() == null
                        ? Path.of(".").toAbsolutePath().toString()
                        : builder.directory().toString();
        return "cd " + shellQuote(directory) + " && " + commandLine(builder.command());
    }

    private static String commandLine(final List<String> command) {
        return command.stream()
                .map(PlatformCommands::shellQuote)
                .collect(java.util.stream.Collectors.joining(" "));
    }

    public static String shellQuote(final String value) {
        if (isWindows()) {
            return windowsShellQuote(value);
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /* package */
    static String windowsShellQuote(final String value) {
        return "\""
                + value.replace("^", "^^")
                        .replace("&", "^&")
                        .replace("|", "^|")
                        .replace("<", "^<")
                        .replace(">", "^>")
                        .replace("(", "^(")
                        .replace(")", "^)")
                        .replace("%", "^%")
                        .replace("!", "^!")
                        .replace("\"", "\\\"")
                + "\"";
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
}
