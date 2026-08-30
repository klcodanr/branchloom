package com.jagent.desktop.models;

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
        int checksPassed,
        int checksTotal,
        String checksStatus) {

    public String relevanceGroup() {
        if (draft || "CONFLICTING".equals(mergeable)) {
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
