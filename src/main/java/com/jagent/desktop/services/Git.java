package com.jagent.desktop.services;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.git.GitParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
                + PlatformCommands.shellQuote(host)
                + " --user "
                + PlatformCommands.shellQuote(user)
                + ") "
                + command;
    }

    public static String status(final Path worktree) throws IOException, InterruptedException {
        return run(worktree, "git status --short");
    }

    public static Map<String, String> statusFiles(final Path worktree)
            throws IOException, InterruptedException {
        return GitParser.parseStatus(
                run(worktree, "git status --porcelain=v1 -z --untracked-files=all"));
    }

    public static String currentBranch(final Path worktree)
            throws IOException, InterruptedException {
        return run(worktree, "git branch --show-current");
    }

    public static String diffSummary(final Path worktree) throws IOException, InterruptedException {
        final StringBuilder summary =
                new StringBuilder(runGit(worktree, 0, "diff", "--numstat", "HEAD"));
        final String untracked =
                runGit(worktree, 0, "ls-files", "--others", "--exclude-standard", "-z");
        for (final String file : untracked.split("\u0000")) {
            if (!file.isBlank()) {
                final String nullDevice = PlatformCommands.isWindows() ? "NUL" : "/dev/null";
                final String diff =
                        runGit(
                                worktree,
                                1,
                                "diff",
                                "--no-index",
                                "--numstat",
                                "--",
                                nullDevice,
                                file);
                final String prefix = nullDevice + " => ";
                final int prefixIndex = diff.indexOf(prefix);
                summary.append(
                        prefixIndex < 0
                                ? diff
                                : diff.substring(0, prefixIndex)
                                        + diff.substring(prefixIndex + prefix.length()));
            }
        }
        return summary.toString();
    }

    private static String runGit(
            final Path worktree, final int expectedExitCode, final String... args)
            throws IOException, InterruptedException {
        final List<String> command = new ArrayList<>();
        command.add(PlatformCommands.executable("git"));
        command.addAll(List.of(args));
        final ProcessBuilder builder =
                PlatformCommands.prepare(new ProcessBuilder(command))
                        .directory(worktree.toFile())
                        .redirectErrorStream(true);
        final Process process = builder.start();
        final String output =
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != expectedExitCode) {
            PlatformCommands.logFailure(builder, process.exitValue(), output);
            throw new IOException(output.trim());
        }
        return output;
    }

    private static String run(final Path worktree, final String command)
            throws IOException, InterruptedException {
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
        return output;
    }

    public CompletableFuture<List<Branch>> listBranches(final Project project) {
        return query(
                        project,
                        "git for-each-ref --format='%(refname)|%(symref)' refs/heads refs/remotes")
                .thenApply(GitParser::parseBranches);
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
        return query(project, "git worktree list --porcelain")
                .thenApply(GitParser::parseWorktrees);
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
                        + PlatformCommands.shellQuote(worktree.path().toString())
                        + " "
                        + PlatformCommands.shellQuote(branch);
        final Path repository = Path.of(project.path());
        return runCommand(command, repository)
                .thenCompose(
                        ignored ->
                                runCommand(
                                        "git -C "
                                                + PlatformCommands.shellQuote(worktree.path().toString())
                                                + " checkout -B "
                                                + PlatformCommands.shellQuote(branch),
                                        repository))
                .thenApply(ignored -> null);
    }

    private static String branchName(final String branch) {
        return branch.startsWith("refs/heads/") ? branch.substring("refs/heads/".length()) : branch;
    }

    public CompletableFuture<Void> addWorktree(
            final Project project,
            final String ref,
            final Path worktree,
            final boolean createBranch,
            final String branchName) {
        final String command =
                "git worktree add "
                        + (createBranch ? "-b " + PlatformCommands.shellQuote(branchName) + " " : "")
                        + PlatformCommands.shellQuote(worktree.toString())
                        + " "
                        + PlatformCommands.shellQuote(ref);
        return runCommand(command, Path.of(project.path())).thenApply(ignored -> null);
    }

    public CompletableFuture<Void> fetchPullRequest(
            final Project project, final int pullRequestNumber) {
        final String branch = "pr-" + pullRequestNumber;
        final String command =
                "git fetch origin pull/" + pullRequestNumber + "/head:" + PlatformCommands.shellQuote(branch);
        return runCommand(command, Path.of(project.path())).thenApply(ignored -> null);
    }

    public CompletableFuture<String> createWorktree(
            final Project project, final String branch, final Path worktree) {
        final String command =
                "git worktree add -b " + PlatformCommands.shellQuote(branch) + " " + PlatformCommands.shellQuote(worktree.toString());
        return runCommand(command, Path.of(project.path()));
    }

    public CompletableFuture<String> createWorktree(
            final Project project, final String branch, final Path worktree, final String baseRef) {
        final String worktreeCommand =
                "git worktree add -b "
                        + PlatformCommands.shellQuote(branch)
                        + " "
                        + PlatformCommands.shellQuote(worktree.toString())
                        + " "
                        + PlatformCommands.shellQuote(baseRef);
        final Path repository = Path.of(project.path());
        return fetchRemoteRef(project, baseRef)
                .thenCompose(ignored -> runCommand(worktreeCommand, repository));
    }

    public CompletableFuture<Void> updateCurrentBranch(final Project project) {
        return updateBranch(Path.of(project.path()));
    }

    public CompletableFuture<Void> updateBranch(final Path worktree) {
        return runCommand("git pull --ff-only", worktree).thenApply(ignored -> null);
    }

    private CompletableFuture<Void> fetchRemoteRef(final Project project, final String ref) {
        if (ref == null || ref.isBlank() || !ref.contains("/")) {
            return CompletableFuture.completedFuture(null);
        }
        final int separator = ref.indexOf('/');
        final String remote = ref.substring(0, separator);
        final String branch = ref.substring(separator + 1);
        return runCommand(
                        "git fetch " + PlatformCommands.shellQuote(remote) + " " + PlatformCommands.shellQuote(branch),
                        Path.of(project.path()))
                .thenApply(ignored -> null);
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
        return GitParser.parseWorktrees(worktreeOutput.lines().toList()).stream()
                .anyMatch(
                        registered ->
                                registered.path().toAbsolutePath().normalize().equals(worktree));
    }

    public void deleteWorktree(final Project project, final Path worktree) throws IOException {
        final Path repository = Path.of(project.path()).toAbsolutePath().normalize();
        final String command = "git worktree remove --force " + PlatformCommands.shellQuote(worktree.toString());
        try {
            final Process process =
                    PlatformCommands.prepare(new ProcessBuilder(PlatformCommands.shell(command)))
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

    public static boolean isRepository(final Path path) {
        try {
            final ProcessBuilder builder =
                    PlatformCommands.prepare(
                                    new ProcessBuilder(
                                            PlatformCommands.executable("git"),
                                            "-C",
                                            path.toString(),
                                            "rev-parse",
                                            "--is-inside-work-tree"))
                            .redirectErrorStream(true);
            final Process process = builder.start();
            final String output =
                    new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            final int exitCode = process.waitFor();
            if (exitCode != 0) {
                PlatformCommands.logFailure(builder, exitCode, output);
                return false;
            }
            return "true".equals(output.trim());
        } catch (IOException exception) {
            // The caller cannot distinguish a missing Git executable from a non-repository path.
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
