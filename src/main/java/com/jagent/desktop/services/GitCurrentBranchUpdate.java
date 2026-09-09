package com.jagent.desktop.services;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

final class GitCurrentBranchUpdate {
    private final Git git;

    /* default */ GitCurrentBranchUpdate(final Git git) {
        this.git = git;
    }

    /* default */ CompletableFuture<Void> update(final Path worktree) {
        return git.ensureClean(worktree)
                .thenCompose(
                        ignored ->
                                git.runCommand(
                                                "git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'",
                                                worktree)
                                        .exceptionallyCompose(
                                                failure ->
                                                        CompletableFuture.failedFuture(
                                                                new IOException(
                                                                        "No upstream is configured for this branch. Configure an upstream before updating it."))))
                .thenCompose(
                        upstream -> {
                            final String ref = upstream.trim();
                            final int separator = ref.indexOf('/');
                            return git.runCommand(
                                            Git.fetchCommand(
                                                    ref.substring(0, separator),
                                                    ref.substring(separator + 1)),
                                            worktree)
                                    .thenCompose(
                                            ignoredFetch ->
                                                    git.runCommand(
                                                            "git merge --no-edit "
                                                                    + PlatformCommands.shellQuote(
                                                                            ref),
                                                            worktree));
                        })
                .thenApply(ignored -> null);
    }
}
