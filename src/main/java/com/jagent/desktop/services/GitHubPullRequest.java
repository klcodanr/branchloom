package com.jagent.desktop.services;

import com.jagent.desktop.models.Project;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Reads pull-request metadata needed by workspace comparisons. */
public final class GitHubPullRequest {
    private GitHubPullRequest() {}

    public static void markReady(final Project project, final int number)
            throws IOException, InterruptedException {
        runPullRequestCommand(project, number, "ready");
    }

    public static void convertToDraft(final Project project, final int number)
            throws IOException, InterruptedException {
        runPullRequestCommand(project, number, "ready --undo");
    }

    public static void close(final Project project, final int number)
            throws IOException, InterruptedException {
        runPullRequestCommand(project, number, "close");
    }

    public static void merge(final Project project, final int number)
            throws IOException, InterruptedException {
        runPullRequestCommand(project, number, "merge --merge --delete-branch=false");
    }

    public static void approve(final Project project, final int number)
            throws IOException, InterruptedException {
        runPullRequestCommand(project, number, "review --approve");
    }

    public static String baseBranch(final Project project, final Path worktree)
            throws IOException, InterruptedException {
        final String command =
                Git.githubCommand(project, "gh pr view --json baseRefName --jq .baseRefName");
        final ProcessBuilder builder =
                PlatformCommands.prepare(new ProcessBuilder(PlatformCommands.shell(command)))
                        .directory(worktree.toFile())
                        .redirectErrorStream(true);
        final Process process = builder.start();
        final String output =
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            PlatformCommands.logFailure(builder, process.exitValue(), output);
            throw new IOException(output.trim());
        }
        return output.trim();
    }

    private static void runPullRequestCommand(
            final Project project, final int number, final String arguments)
            throws IOException, InterruptedException {
        final String command = Git.githubCommand(project, "gh pr " + arguments + " " + number);
        final ProcessBuilder builder =
                PlatformCommands.prepare(new ProcessBuilder(PlatformCommands.shell(command)))
                        .directory(Path.of(project.path()).toFile())
                        .redirectErrorStream(true);
        final Process process = builder.start();
        final String output =
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            PlatformCommands.logFailure(builder, process.exitValue(), output);
            throw new IOException(output.trim());
        }
    }
}
