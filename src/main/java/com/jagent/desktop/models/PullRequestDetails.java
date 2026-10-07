package com.jagent.desktop.models;

import com.jagent.desktop.ui.components.Theme;
import java.awt.Color;
import java.util.List;
import org.jetbrains.annotations.NotNull;
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
        @NotNull int changedFiles,
        @NotNull PullRequestChecks checks) {
    private static final Logger LOG = LoggerFactory.getLogger(PullRequestDetails.class);

    public PullRequestDetails(
            final ProjectId projectId,
            final Project project,
            final int number,
            final boolean draft,
            final boolean mergeable,
            final String mergableState,
            final int additions,
            final int deletions,
            final int changedFiles) {
        this(
                projectId,
                project,
                number,
                draft,
                mergeable,
                mergableState,
                additions,
                deletions,
                changedFiles,
                new PullRequestChecks(List.of()));
    }

    public enum Status {
        READY,
        CONFLICTED,
        DRAFT,
        IN_PROGRESS,
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
        if (checks().checksStatus() == PullRequestChecks.Status.FAILING) {
            return Status.CHECKS_FAILING;
        } else if ("dirty".equals(mergableState())) {
            return Status.CONFLICTED;
        } else if (checks().checksStatus() == PullRequestChecks.Status.PENDING) {
            return Status.IN_PROGRESS;
        } else if (draft()) {
            return Status.DRAFT;
        } else if (mergeable && List.of("clean", "behind").contains(mergableState())) {
            return Status.READY;
        } else if (List.of("blocked", "draft", "has_hooks").contains(mergableState())) {
            return Status.IN_PROGRESS;
        } else if ("unstable".equals(mergableState())) {
            return Status.CHECKS_FAILING;
        }
        return Status.OTHER;
    }

    public Color indicatorColor() {
        final Status stat = status();
        if (stat == Status.READY) {
            return Theme.Colors.success();
        } else if (stat == Status.IN_PROGRESS) {
            return Theme.Colors.warning();
        } else if (stat == Status.CHECKS_FAILING || stat == Status.CONFLICTED) {
            return Theme.Colors.danger();
        }
        return Theme.Colors.muted();
    }
}
