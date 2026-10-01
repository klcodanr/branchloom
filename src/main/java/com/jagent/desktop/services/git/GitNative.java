package com.jagent.desktop.services.git;

import com.jagent.desktop.models.git.Branch;
import com.jagent.desktop.models.git.Worktree;
import com.jagent.desktop.services.PlatformCommands;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.annotations.NonNull;

/**
 * Low-level adapter for invoking native git commands.
 *
 * <p>Use this class only from git service internals when behavior requires the git CLI (for
 * example, worktree porcelain operations). UI and higher-level services should use {@link
 * GitRepository} instead of calling this class directly.
 *
 * <p>All methods execute in the supplied repository directory and throw {@link IOException} on
 * command failures or interruption.
 */
final class GitNative {

    private static final String WORKTREE = "worktree";

    private GitNative() {}

    /* default */
    static String run(@NonNull final Path directory, @NonNull final String... args)
            throws IOException {
        final List<String> command = new ArrayList<>();
        command.add(PlatformCommands.executable("git"));
        command.addAll(List.of(args));
        final ProcessBuilder builder =
                PlatformCommands.prepare(new ProcessBuilder(command))
                        .directory(directory.toFile())
                        .redirectErrorStream(true);
        final Process process = builder.start();
        final String output =
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            final int exitCode = process.waitFor();
            if (exitCode != 0) {
                PlatformCommands.logFailure(builder, exitCode, output);
                throw new IOException(output.isBlank() ? "Git command failed." : output.trim());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for git process", exception);
        }
        return output;
    }

    /* default */
    static void createWorktree(
            @NonNull final Path mainPath,
            @NonNull final Path worktreePath,
            @NonNull final Branch branch)
            throws IOException {
        final List<String> args = new ArrayList<>();
        args.add(WORKTREE);
        args.add("add");
        if (branch.remote()) {
            args.add("-b");
            args.add(branch.localName());
        }
        args.add(worktreePath.toString());
        args.add(branch.name());
        run(mainPath.toAbsolutePath(), args.toArray(new String[0]));
    }

    /* default */
    static void deleteWorktree(@NonNull final Path mainPath, @NonNull final Path worktreePath)
            throws IOException {
        run(mainPath, WORKTREE, "remove", "--force", worktreePath.toString());
    }

    /* default */
    static List<Worktree> listWorktrees(@NonNull final Path path) throws IOException {
        final String worktreeResponse = run(path, WORKTREE, "list", "--porcelain");
        final List<String> lines =
                worktreeResponse.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
        return parseWorktrees(lines);
    }

    /* default */
    static void pruneWorktrees(@NonNull final Path path) throws IOException {
        run(path, WORKTREE, "prune");
    }

    /* default */
    static void restoreWorktree(@NonNull final Path repository, @NonNull final Worktree worktree)
            throws IOException {
        final String shortBranch = new Branch(worktree.branch()).withoutHeadsPrefix();
        run(repository, WORKTREE, "add", "--force", worktree.path().toString(), shortBranch);
        run(repository, "-C", worktree.path().toString(), "checkout", "-B", shortBranch);
    }

    /* default */
    static List<Worktree> parseWorktrees(final List<String> lines) {
        final List<Worktree> worktrees = new ArrayList<>();
        Path path = null;
        String branch = null;
        boolean prunable = false;
        for (final String line : lines) {
            if (line.startsWith("worktree ")) {
                if (path != null) {
                    worktrees.add(new Worktree(path, branch, prunable));
                }
                path = Path.of(line.substring("worktree ".length()));
                branch = null;
                prunable = false;
            } else if (line.startsWith("branch ")) {
                branch = line.substring("branch ".length());
            } else if (line.startsWith("prunable ")) {
                prunable = true;
            }
        }
        if (path != null) {
            worktrees.add(new Worktree(path, branch, prunable));
        }
        return worktrees;
    }
}
