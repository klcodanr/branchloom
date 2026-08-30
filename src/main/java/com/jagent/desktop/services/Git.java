package com.jagent.desktop.services;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("PMD.GodClass")
public final class Git {
    public record Branch(String name, boolean remote) {}

    public record Worktree(Path path, String branch, boolean prunable) {}

    public static String githubCommand(final Project project, final String command) {
        final String host = project.githubHost();
        final String user = project.githubUser();
        if (host == null || host.isBlank() || user == null || user.isBlank()) {
            return command;
        }
        return "GH_TOKEN=$(gh auth token --hostname "
                + shellQuote(host)
                + " --user "
                + shellQuote(user)
                + ") "
                + command;
    }

    public static String status(final Path worktree) throws IOException, InterruptedException {
        return run(worktree, "git status --short");
    }

    public static Map<String, String> statusFiles(final Path worktree)
            throws IOException, InterruptedException {
        final String output = run(worktree, "git status --porcelain=v1 -z --untracked-files=all");
        final Map<String, String> statuses = new LinkedHashMap<>();
        final String[] entries = output.split("\u0000", -1);
        int index = 0;
        while (index < entries.length) {
            final String entry = entries[index];
            if (entry.length() < 4) {
                index++;
                continue;
            }
            final String code = entry.substring(0, 2);
            final String path = entry.substring(3);
            statuses.put(path, code);
            index += code.contains("R") || code.contains("C") ? 2 : 1;
        }
        return Map.copyOf(statuses);
    }

    public static String currentBranch(final Path worktree)
            throws IOException, InterruptedException {
        return run(worktree, "git branch --show-current");
    }

    public static String diffSummary(final Path worktree) throws IOException, InterruptedException {
        return run(
                worktree,
                "head=$(git rev-parse --abbrev-ref HEAD@{upstream} 2>/dev/null || git rev-parse --verify HEAD~1)"
                        + " && git diff --numstat \"$head...HEAD\"");
    }

    private static String run(final Path worktree, final String command)
            throws IOException, InterruptedException {
        final Process process =
                new ProcessBuilder(PlatformCommands.shell(command))
                        .directory(worktree.toFile())
                        .redirectErrorStream(true)
                        .start();
        final String output =
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IOException(output.trim());
        }
        return output;
    }

    public CompletableFuture<List<Branch>> listBranches(final Project project) {
        return query(
                        project,
                        "git for-each-ref --format='%(refname)|%(symref)' refs/heads refs/remotes")
                .thenApply(
                        lines ->
                                lines.stream()
                                        .map(line -> line.split("\\|", 2))
                                        .filter(parts -> parts.length > 0 && !parts[0].isBlank())
                                        .filter(parts -> parts.length < 2 || parts[1].isBlank())
                                        .map(
                                                parts -> {
                                                    final boolean remote =
                                                            parts[0].startsWith("refs/remotes/");
                                                    final String name =
                                                            parts[0].substring(remote ? 13 : 11);
                                                    return new Branch(name, remote);
                                                })
                                        .filter(branch -> !branch.name().endsWith("/HEAD"))
                                        .distinct()
                                        .toList());
    }

    public CompletableFuture<List<Path>> listWorktrees(final Project project) {
        return listWorktreeDetails(project)
                .thenApply(worktrees -> worktrees.stream().map(Worktree::path).toList());
    }

    public CompletableFuture<List<Branch>> listAvailableBranches(final Project project) {
        return listWorktreeDetails(project)
                .thenCombine(
                        listBranches(project),
                        (worktrees, branches) -> {
                            final List<String> occupied =
                                    worktrees.stream()
                                            .map(Worktree::branch)
                                            .filter(branch -> branch != null)
                                            .toList();
                            return branches.stream()
                                    .filter(
                                            branch ->
                                                    !occupied.contains(
                                                            "refs/heads/" + branch.name()))
                                    .toList();
                        });
    }

    private CompletableFuture<List<Worktree>> listWorktreeDetails(final Project project) {
        return query(project, "git worktree list --porcelain").thenApply(Git::parseWorktrees);
    }

    public CompletableFuture<Optional<Worktree>> checkPrunableWorktree(
            final Project project, final Path target) {
        final Path normalized = target.toAbsolutePath().normalize();
        return listWorktreeDetails(project)
                .thenApply(
                        worktrees ->
                                worktrees.stream()
                                        .filter(Worktree::prunable)
                                        .filter(
                                                worktree ->
                                                        worktree.path()
                                                                .toAbsolutePath()
                                                                .normalize()
                                                                .equals(normalized))
                                        .findFirst());
    }

    public CompletableFuture<Void> pruneWorktrees(final Project project) {
        return runCommand("git worktree prune", Path.of(project.path())).thenApply(ignored -> null);
    }

    public CompletableFuture<Void> restoreWorktree(final Project project, final Worktree worktree) {
        if (worktree.branch() == null || worktree.branch().isBlank()) {
            return CompletableFuture.failedFuture(
                    new IOException("The stale worktree has no recorded branch to restore."));
        }
        final String branch = branchName(worktree.branch());
        final String command =
                "git worktree add --force "
                        + shellQuote(worktree.path().toString())
                        + " "
                        + shellQuote(branch);
        final Path repository = Path.of(project.path());
        return runCommand(command, repository)
                .thenCompose(
                        ignored ->
                                runCommand(
                                        "git -C "
                                                + shellQuote(worktree.path().toString())
                                                + " checkout -B "
                                                + shellQuote(branch),
                                        repository))
                .thenApply(ignored -> null);
    }

    private static String branchName(final String branch) {
        return branch.startsWith("refs/heads/") ? branch.substring("refs/heads/".length()) : branch;
    }

    private static List<Worktree> parseWorktrees(final List<String> lines) {
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

    public CompletableFuture<Void> addWorktree(
            final Project project,
            final String ref,
            final Path worktree,
            final boolean createBranch,
            final String branchName) {
        final String command =
                "git worktree add "
                        + (createBranch ? "-b " + shellQuote(branchName) + " " : "")
                        + shellQuote(worktree.toString())
                        + " "
                        + shellQuote(ref);
        return runCommand(command, Path.of(project.path())).thenApply(ignored -> null);
    }

    public CompletableFuture<Void> fetchPullRequest(
            final Project project, final int pullRequestNumber) {
        final String branch = "pr-" + pullRequestNumber;
        final String command =
                "git fetch origin pull/" + pullRequestNumber + "/head:" + shellQuote(branch);
        return runCommand(command, Path.of(project.path())).thenApply(ignored -> null);
    }

    public CompletableFuture<String> createWorktree(
            final Project project, final String branch, final Path worktree) {
        final String command =
                "git worktree add -b " + shellQuote(branch) + " " + shellQuote(worktree.toString());
        return runCommand(command, Path.of(project.path()));
    }

    private CompletableFuture<List<String>> query(final Project project, final String command) {
        return runCommand(command, Path.of(project.path()))
                .thenApply(
                        output ->
                                output.lines()
                                        .map(String::trim)
                                        .filter(line -> !line.isBlank())
                                        .toList());
    }

    private CompletableFuture<String> runCommand(final String command, final Path directory) {
        final CompletableFuture<String> future = new CompletableFuture<>();
        BackgroundTasks.submit(
                "Git",
                "git-command",
                () -> {
                    try {
                        future.complete(run(directory, command));
                    } catch (IOException exception) {
                        future.completeExceptionally(exception);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        future.completeExceptionally(exception);
                    }
                });
        return future;
    }

    public CompletableFuture<List<String>> listLocalBranches(final Project project) {
        return listBranches(project)
                .thenApply(
                        branches ->
                                branches.stream()
                                        .filter(branch -> !branch.remote())
                                        .map(Branch::name)
                                        .toList());
    }

    public CompletableFuture<Boolean> branchExists(final Project project, final String branch) {
        return listLocalBranches(project).thenApply(branches -> branches.contains(branch));
    }

    @SuppressWarnings("PMD.CyclomaticComplexity")
    public Path validateWorktreeDeletion(final Project project, final Session session)
            throws IOException {
        final String worktreePath = session.worktreePath();
        if (worktreePath == null || worktreePath.isBlank()) {
            throw new IOException("The session has no worktree path to clean up.");
        }
        final Path worktree = Path.of(worktreePath).toAbsolutePath().normalize();
        final Path repository = Path.of(project.path()).toAbsolutePath().normalize();
        if (worktree.equals(repository) || worktree.getNameCount() < 2) {
            throw new IOException("The session worktree path is not safe to remove: " + worktree);
        }
        if (!Files.isDirectory(worktree)) {
            throw new IOException("The worktree directory does not exist: " + worktree);
        }
        if (Files.isSymbolicLink(worktree)) {
            throw new IOException("The path is not a registered worktree: " + worktree);
        }
        if (!isRegisteredWorktree(repository, worktree)) {
            throw new IOException("The path is not a registered worktree: " + worktree);
        }
        return worktree;
    }

    private boolean isRegisteredWorktree(final Path repository, final Path worktree)
            throws IOException {
        final String worktreeOutput;
        try {
            worktreeOutput = run(repository, "git worktree list --porcelain");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while validating the session worktree.", exception);
        }
        return parseWorktrees(worktreeOutput.lines().toList()).stream()
                .anyMatch(
                        registered ->
                                registered.path().toAbsolutePath().normalize().equals(worktree));
    }

    public void deleteWorktree(final Project project, final Path worktree) throws IOException {
        final Path repository = Path.of(project.path()).toAbsolutePath().normalize();
        final String command = "git worktree remove --force " + shellQuote(worktree.toString());
        try {
            final Process process =
                    new ProcessBuilder(PlatformCommands.shell(command))
                            .directory(repository.toFile())
                            .redirectErrorStream(true)
                            .start();
            final String output =
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                            .trim();
            if (process.waitFor() != 0) {
                throw new IOException(
                        output.isBlank() ? "Git could not remove the worktree." : output);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while removing the worktree.", exception);
        }
    }

    public static String shellQuote(final String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
