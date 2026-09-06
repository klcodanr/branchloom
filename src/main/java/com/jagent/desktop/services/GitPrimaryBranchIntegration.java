package com.jagent.desktop.services;

import com.jagent.desktop.models.Project;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

final class GitPrimaryBranchIntegration {
    private final Git git;

    /* default */ GitPrimaryBranchIntegration(final Git git) {
        this.git = git;
    }

    /* default */ CompletableFuture<Void> integrate(
            final Project project, final Path worktree, final GitIntegrationStrategy strategy) {
        final Path repository = Path.of(project.path());
        return git.ensureClean(worktree)
                .thenCompose(ignored -> primaryBranchRef(repository))
                .thenCompose(
                        primaryRef ->
                                fetchRef(repository, primaryRef)
                                        .thenCompose(
                                                ignored ->
                                                        integrate(worktree, primaryRef, strategy)))
                .thenApply(ignored -> null);
    }

    /* default */ CompletableFuture<Void> update(final Project project) {
        final Path repository = Path.of(project.path());
        return git.ensureClean(repository)
                .thenCompose(ignored -> primaryBranchRef(repository))
                .thenCompose(
                        primaryRef ->
                                fetchRef(repository, primaryRef)
                                        .thenCompose(
                                                ignored ->
                                                        git.runCommand(
                                                                "git merge --ff-only "
                                                                        + PlatformCommands
                                                                                .shellQuote(
                                                                                        primaryRef),
                                                                repository)))
                .thenApply(ignored -> null);
    }

    private CompletableFuture<String> primaryBranchRef(final Path repository) {
        return git.runCommand("git remote", repository)
                .thenCompose(
                        remotes -> {
                            final String remote = Git.selectRemote(remotes);
                            return Optional.of(remote)
                                    .filter(value -> !value.isBlank())
                                    .map(
                                            value ->
                                                    git.runCommand(
                                                                    "git symbolic-ref --quiet --short "
                                                                            + PlatformCommands
                                                                                    .shellQuote(
                                                                                            "refs/remotes/"
                                                                                                    + value
                                                                                                    + "/HEAD"),
                                                                    repository)
                                                            .exceptionallyCompose(
                                                                    failure ->
                                                                            CompletableFuture
                                                                                    .failedFuture(
                                                                                            new IOException(
                                                                                                    "The remote has no configured default branch. Set its remote HEAD before integrating the primary branch.")))
                                                            .thenApply(String::trim))
                                    .orElseGet(
                                            () ->
                                                    CompletableFuture.failedFuture(
                                                            new IOException(
                                                                    "No Git remote is configured. Add a remote before integrating the primary branch.")));
                        });
    }

    private CompletableFuture<Void> fetchRef(final Path repository, final String ref) {
        final int separator = ref.indexOf('/');
        return Optional.of(separator)
                .filter(index -> index >= 1 && index < ref.length() - 1)
                .map(
                        index ->
                                git.runCommand(
                                                Git.fetchCommand(
                                                        ref.substring(0, index),
                                                        ref.substring(index + 1)),
                                                repository)
                                        .<Void>thenApply(ignored -> null))
                .orElseGet(
                        () ->
                                CompletableFuture.<Void>failedFuture(
                                        new IOException(
                                                "The remote default branch reference is invalid.")));
    }

    private CompletableFuture<String> integrate(
            final Path worktree, final String primaryRef, final GitIntegrationStrategy strategy) {
        return git.runCommand(strategy.command(primaryRef), worktree)
                .exceptionallyCompose(
                        failure ->
                                CompletableFuture.failedFuture(
                                        new IOException(
                                                "Could not "
                                                        + strategy.verb()
                                                        + " the primary branch. Resolve any conflicts, then continue or abort the Git operation.",
                                                failure)));
    }
}
