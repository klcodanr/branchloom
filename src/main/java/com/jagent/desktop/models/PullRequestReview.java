package com.jagent.desktop.models;

import java.io.IOException;
import org.kohsuke.github.GHPullRequestReview;
import org.kohsuke.github.GHPullRequestReviewState;

/** A collection of status check entries reported for a pull request. */
public record PullRequestReview(GitHubUser user, Status status) {

    public enum Status {
        PENDING,

        /** The approved. */
        APPROVED,

        /** The changes requested. */
        CHANGES_REQUESTED,

        /** The commented. */
        COMMENTED,

        /** The dismissed. */
        DISMISSED;

        public static Status from(final GHPullRequestReviewState state) {
            if (state == GHPullRequestReviewState.REQUEST_CHANGES) {
                return CHANGES_REQUESTED;
            }
            return Status.valueOf(state.name());
        }
    }

    public static PullRequestReview from(final GHPullRequestReview review) throws IOException {
        return new PullRequestReview(
                GitHubUser.from(review.getUser()), Status.from(review.getState()));
    }
}
