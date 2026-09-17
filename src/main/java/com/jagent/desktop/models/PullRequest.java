package com.jagent.desktop.models;

import com.jagent.desktop.api.PullRequestInfo;
import java.util.List;
import java.util.Objects;

public record PullRequest(
        ProjectId projectId,
        int number,
        String title,
        String description,
        String commentSummary,
        String url,
        String createdAt,
        String updatedAt,
        String reviewDecision,
        String mergeable,
        boolean draft,
        String author,
        String headBranch,
        int additions,
        int deletions,
        int changedFiles,
        int checksPassed,
        int checksTotal,
        String checksStatus,
        List<PullRequestCheck> checks)
        implements PullRequestInfo {

    public PullRequest {
        checks = checks == null ? List.of() : List.copyOf(checks);
    }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public PullRequest(
            final ProjectId projectId,
            final int number,
            final String title,
            final String description,
            final String commentSummary,
            final String url,
            final String createdAt,
            final String updatedAt,
            final String reviewDecision,
            final String mergeable,
            final boolean draft,
            final String author,
            final String headBranch,
            final int additions,
            final int deletions,
            final int changedFiles,
            final int checksPassed,
            final int checksTotal,
            final String checksStatus) {
        this(
                projectId,
                number,
                title,
                description,
                commentSummary,
                url,
                createdAt,
                updatedAt,
                reviewDecision,
                mergeable,
                draft,
                author,
                headBranch,
                additions,
                deletions,
                changedFiles,
                checksPassed,
                checksTotal,
                checksStatus,
                List.of());
    }

    public int totalChanges() {
        return additions + deletions;
    }

    @Override
    public String mergeState() {
        return mergeable;
    }

    public String relevanceGroup() {
        if (draft || hasBlockingChecks() || hasBlockingMergeabilityForBoardGrouping()) {
            return PullRequestGroup.NOT_READY.label();
        }
        return switch (reviewDecision) {
            case "CHANGES_REQUESTED" -> PullRequestGroup.WAITING_FOR_CHANGES.label();
            case "APPROVED" -> PullRequestGroup.APPROVED.label();
            default -> PullRequestGroup.READY_FOR_REVIEW.label();
        };
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof PullRequest request
                && number == request.number
                && Objects.equals(url, request.url());
    }

    @Override
    public int hashCode() {
        return Objects.hash(number, url);
    }
}
