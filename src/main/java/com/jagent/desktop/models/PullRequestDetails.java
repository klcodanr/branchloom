package com.jagent.desktop.models;

import com.jagent.desktop.ui.components.Theme;
import java.awt.Color;
import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.github.GHPullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record PullRequestDetails(
        @NotNull ProjectId projectId,
        @NotNull Project project,
        @NotNull int number,
        @NotNull boolean draft,
        @NotNull boolean mergeable,
        @NotNull String mergableState,
        @NotNull int additions,
        @NotNull int deletions,
        @NotNull int changedFiles) {
    private static final Logger LOG = LoggerFactory.getLogger(PullRequestDetails.class);

    public static PullRequestDetails fromPullRequest(
            @NotNull final ProjectId projectId,
            @NotNull final Project project,
            @NotNull final GHPullRequest request) {
        try {
            return new PullRequestDetails(
                    projectId,
                    project,
                    request.getNumber(),
                    request.isDraft(),
                    Boolean.TRUE.equals(request.getMergeable()),
                    request.getMergeableState() == null ? "" : request.getMergeableState(),
                    request.getAdditions(),
                    request.getDeletions(),
                    request.getChangedFiles());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not load pull request details " + request.getNumber(), exception);
        }
    }

    public enum Status {
        READY,
        CONFLICTED,
        DRAFT,
        BLOCKED,
        PENDING,
        CHECKS_FAILING,
        OTHER
    }

    public Status status() {
        LOG.debug(
                "Getting PR status: number={}, draft={}, mergeable={}, mergableState={}",
                number(),
                draft(),
                mergeable(),
                mergableState());
        if (draft()) {
            return Status.DRAFT;
        } else if (mergeable && List.of("clean", "behind").contains(mergableState())) {
            return Status.READY;
        } else if ("dirty".equals(mergableState())) {
            return Status.CONFLICTED;
        } else if ("blocked".equals(mergableState())) {
            return Status.BLOCKED;
        } else if (List.of("draft", "has_hooks").contains(mergableState())) {
            return Status.PENDING;
        } else if ("unstable".equals(mergableState())) {
            return Status.CHECKS_FAILING;
        }
        return Status.OTHER;
    }

    public Color indicatorColor() {
        final Status stat = status();
        if (draft()) {
            return Theme.Colors.muted();
        } else if (stat == Status.READY) {
            return Theme.Colors.success();
        } else if (stat == Status.PENDING || stat == Status.BLOCKED) {
            return Theme.Colors.warning();
        } else if (stat == Status.CHECKS_FAILING || stat == Status.CONFLICTED) {
            return Theme.Colors.danger();
        }
        return Theme.Colors.muted();
    }
}
