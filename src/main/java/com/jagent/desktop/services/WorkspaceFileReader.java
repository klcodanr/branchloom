package com.jagent.desktop.services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class WorkspaceFileReader {
    public record FileContent(String content, String diff, boolean binary) {}

    private WorkspaceFileReader() {}

    public static CompletableFuture<FileContent> read(final Path worktree, final Path file) {
        return BackgroundTasks.submit(
                "Workspace",
                "read-file",
                () -> {
                    try {
                        return readNow(worktree, file);
                    } catch (IOException exception) {
                        throw new java.util.concurrent.CompletionException(exception);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new java.util.concurrent.CompletionException(exception);
                    }
                });
    }

    private static FileContent readNow(final Path worktree, final Path file)
            throws IOException, InterruptedException {
        final Path normalizedWorktree = worktree.toAbsolutePath().normalize();
        final Path normalizedFile = file.toAbsolutePath().normalize();
        final byte[] bytes = Files.readAllBytes(normalizedFile);
        final boolean binary = containsBinary(bytes);
        final String content = binary ? "" : new String(bytes, StandardCharsets.UTF_8);
        final String relative = normalizedWorktree.relativize(normalizedFile).toString();
        return new FileContent(content, fileDiff(normalizedWorktree, relative), binary);
    }

    private static boolean containsBinary(final byte[] bytes) {
        for (final byte value : bytes) {
            if (value == 0) {
                return true;
            }
        }
        return false;
    }

    private static String fileDiff(final Path worktree, final String relative)
            throws IOException, InterruptedException {
        if (isTracked(worktree, relative)) {
            return Git.runGit(
                    worktree, 0, "diff", "--no-ext-diff", "--no-color", "HEAD", "--", relative);
        }
        final String nullDevice = PlatformCommands.isWindows() ? "NUL" : "/dev/null";
        return Git.runGit(
                worktree, 1, "diff", "--no-index", "--no-color", "--", nullDevice, relative);
    }

    private static boolean isTracked(final Path worktree, final String relative)
            throws IOException, InterruptedException {
        try {
            Git.runGit(worktree, 0, "ls-files", "--error-unmatch", "--", relative);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
