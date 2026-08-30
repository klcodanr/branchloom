package com.jagent.desktop.services;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

public final class CommandRunner {
    private CommandRunner() {}

    public static void run(
            final String command,
            final Path directory,
            final Runnable onSuccess,
            final Consumer<String> onFailure) {
        run(command, directory, null, onSuccess, onFailure);
    }

    public static void run(
            final String command,
            final Path directory,
            final Consumer<String> onOutput,
            final Runnable onSuccess,
            final Consumer<String> onFailure) {
        BackgroundTasks.submit(
                "Commands",
                "command-runner",
                () -> {
                    try {
                        final Process process = startProcess(command, directory);
                        final String output = readOutput(process, onOutput);
                        final int exitCode = process.waitFor();
                        notifyCompletion(exitCode, output, onSuccess, onFailure);
                    } catch (IOException exception) {
                        SwingUtilities.invokeLater(() -> onFailure.accept(exception.getMessage()));
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        SwingUtilities.invokeLater(() -> onFailure.accept(exception.getMessage()));
                    }
                });
    }

    private static Process startProcess(final String command, final Path directory)
            throws IOException {
        return new ProcessBuilder(PlatformCommands.shell(command))
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start();
    }

    private static String readOutput(final Process process, final Consumer<String> onOutput)
            throws IOException {
        final StringBuilder output = new StringBuilder();
        try (var reader = process.inputReader()) {
            while (true) {
                final String line = reader.readLine();
                if (line == null) {
                    break;
                }
                output.append(line).append(System.lineSeparator());
                if (onOutput != null) {
                    final String text = line;
                    SwingUtilities.invokeLater(() -> onOutput.accept(text));
                }
            }
        }
        return output.toString();
    }

    private static void notifyCompletion(
            final int exitCode,
            final String output,
            final Runnable onSuccess,
            final Consumer<String> onFailure) {
        SwingUtilities.invokeLater(
                () -> {
                    if (exitCode != 0) {
                        onFailure.accept(output.trim());
                        return;
                    }
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                });
    }
}
