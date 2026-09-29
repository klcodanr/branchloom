package com.jagent.desktop.models;

import java.util.List;

/** A collection of reviews reported for a pull request. */
public record PullRequestReviews(List<PullRequestReview> reviews) {

    public enum Status {
        PENDING,

        /** The approved. */
        APPROVED,

        /** The changes requested. */
        CHANGES_REQUESTED,
    }

    public Status checksStatus() {
        if (reviews.isEmpty()) {
            return Status.PENDING;
        }
        if (reviews.stream()
                .anyMatch(
                        review -> review.status() == PullRequestReview.Status.CHANGES_REQUESTED)) {
            return Status.CHANGES_REQUESTED;
        }
        if (reviews.stream()
                .anyMatch(review -> review.status() == PullRequestReview.Status.APPROVED)) {
            return Status.APPROVED;
        }
        return Status.PENDING;
    }
}
