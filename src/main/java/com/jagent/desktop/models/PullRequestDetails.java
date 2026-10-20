package com.jagent.desktop.models;

import com.jagent.desktop.ui.components.Theme;
import java.awt.Color;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PullRequestDetails extends PullRequest {
    private static final Logger LOG = LoggerFactory.getLogger(PullRequestDetails.class);
    private final boolean draft;
    private final boolean merged;
    private final boolean mergeable;
    private final String mergableState;
    private final int additions;
    private final int deletions;
    private final int changedFiles;
    private final PullRequestChecks checks;
    private final Status status;

    public PullRequestDetails(
            final PullRequest pullRequest,
            final boolean draft,
            final boolean merged,
            final boolean mergeable,
            final String mergableState,
            final int additions,
            final int deletions,
            final int changedFiles) {
        this(
                pullRequest,
                draft,
                merged,
                mergeable,
                mergableState,
                additions,
                deletions,
                changedFiles,
                new PullRequestChecks(List.of()));
    }

    public PullRequestDetails(
            final PullRequest pullRequest,
            final boolean draft,
            final boolean merged,
            final boolean mergeable,
            final String mergableState,
            final int additions,
            final int deletions,
            final int changedFiles,
            final PullRequestChecks checks) {
        super(
                pullRequest.projectId(),
                pullRequest.project(),
                pullRequest.number(),
                pullRequest.state(),
                pullRequest.title(),
                pullRequest.description(),
                Objects.requireNonNull(pullRequest.url(), "Pull request URL is required"),
                pullRequest.createdAt(),
                pullRequest.updatedAt(),
                pullRequest.author(),
                pullRequest.headBranch(),
                pullRequest.headSha(),
                pullRequest.baseBranch());
        this.draft = draft;
        this.merged = merged;
        this.mergeable = mergeable;
        this.mergableState = mergableState;
        this.additions = additions;
        this.deletions = deletions;
        this.changedFiles = changedFiles;
        this.checks = checks == null ? new PullRequestChecks(List.of()) : checks;
        this.status = calculateStatus();
    }

    public PullRequestDetails(
            final PullRequest pullRequest,
            final boolean draft,
            final boolean mergeable,
            final String mergableState,
            final int additions,
            final int deletions,
            final int changedFiles) {
        this(
                pullRequest,
                draft,
                false,
                mergeable,
                mergableState,
                additions,
                deletions,
                changedFiles);
    }

    public PullRequestDetails(
            final PullRequest pullRequest,
            final boolean draft,
            final boolean mergeable,
            final String mergableState,
            final int additions,
            final int deletions,
            final int changedFiles,
            final PullRequestChecks checks) {
        this(
                pullRequest,
                draft,
                false,
                mergeable,
                mergableState,
                additions,
                deletions,
                changedFiles,
                checks);
    }

    public boolean draft() {
        return draft;
    }

    public boolean mergeable() {
        return mergeable;
    }

    public String mergableState() {
        return mergableState;
    }

    public int additions() {
        return additions;
    }

    public int deletions() {
        return deletions;
    }

    public int changedFiles() {
        return changedFiles;
    }

    public PullRequestChecks checks() {
        return checks;
    }

    public enum Status {
        MERGED,
        READY,
        CONFLICTED,
        DRAFT,
        BLOCKED,
        PENDING,
        CHECKS_FAILING,
        OTHER
    }

    public Status status() {
        return status;
    }

    private Status calculateStatus() {
        LOG.debug(
                "Getting PR status: number={}, draft={}, mergeable={}, mergableState={}",
                number(),
                draft,
                mergeable,
                mergableState);
        if (merged) {
            return Status.MERGED;
        }
        final Status checkStatus = checkStatus();
        if (checkStatus != null) {
            return checkStatus;
        }
        if ("dirty".equals(mergableState)) {
            return Status.CONFLICTED;
        }
        if (draft) {
            return Status.DRAFT;
        }
        if (mergeable && List.of("clean", "behind").contains(mergableState)) {
            return Status.READY;
        }
        return mergeStateStatus();
    }

    private Status checkStatus() {
        return switch (checks.checksStatus()) {
            case FAILING -> Status.CHECKS_FAILING;
            case PENDING -> Status.PENDING;
            default -> null;
        };
    }

    private Status mergeStateStatus() {
        if ("blocked".equals(mergableState)) {
            return Status.BLOCKED;
        }
        if (List.of("draft", "has_hooks").contains(mergableState)) {
            return Status.PENDING;
        }
        if ("unstable".equals(mergableState)) {
            return Status.CHECKS_FAILING;
        }
        return Status.OTHER;
    }

    public Color indicatorColor() {
        final Status stat = status();
        if (stat == Status.READY || stat == Status.MERGED) {
            return Theme.Colors.success();
        } else if (stat == Status.PENDING || stat == Status.BLOCKED) {
            return Theme.Colors.warning();
        } else if (stat == Status.CHECKS_FAILING || stat == Status.CONFLICTED) {
            return Theme.Colors.danger();
        }
        return Theme.Colors.muted();
    }
}
