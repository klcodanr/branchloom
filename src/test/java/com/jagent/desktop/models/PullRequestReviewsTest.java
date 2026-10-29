package com.jagent.desktop.models;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import org.junit.jupiter.api.Test;

class PullRequestReviewsTest {
    private static final String REVIEWER = "reviewer";

    @Test
    void reportsPendingForNoReviews() {
        final PullRequestReviews reviews = new PullRequestReviews(List.of());

        assertEquals(
                PullRequestReviews.Status.PENDING,
                reviews.checksStatus(),
                "an empty review collection should be pending");
        assertEquals(null, reviews.statusFor(REVIEWER), "unknown reviewers should have no status");
    }

    @Test
    void changesRequestedTakesPrecedenceOverApproval() throws MalformedURLException {
        final PullRequestReviews reviews =
                new PullRequestReviews(
                        List.of(
                                review(REVIEWER, PullRequestReview.Status.APPROVED),
                                review("author", PullRequestReview.Status.CHANGES_REQUESTED)));

        assertEquals(
                PullRequestReviews.Status.CHANGES_REQUESTED,
                reviews.checksStatus(),
                "requested changes should take precedence over approval");
    }

    @Test
    void reportsApprovalAndPendingForReviewStatuses() throws MalformedURLException {
        final PullRequestReviews approved =
                new PullRequestReviews(
                        List.of(review(REVIEWER, PullRequestReview.Status.APPROVED)));
        final PullRequestReviews pending =
                new PullRequestReviews(
                        List.of(review(REVIEWER, PullRequestReview.Status.COMMENTED)));

        assertEquals(
                PullRequestReviews.Status.APPROVED,
                approved.checksStatus(),
                "an approval should produce approved status");
        assertEquals(
                PullRequestReviews.Status.PENDING,
                pending.checksStatus(),
                "non-decision reviews should remain pending");
    }

    @Test
    void statusForUsesLatestMatchingReviewCaseInsensitively() throws MalformedURLException {
        final PullRequestReviews reviews =
                new PullRequestReviews(
                        List.of(
                                review(REVIEWER, PullRequestReview.Status.APPROVED),
                                review(REVIEWER, PullRequestReview.Status.CHANGES_REQUESTED)));

        assertEquals(
                PullRequestReview.Status.CHANGES_REQUESTED,
                reviews.statusFor("REVIEWER"),
                "statusFor should return the latest matching review");
        assertEquals(
                null,
                reviews.statusFor("missing"),
                "statusFor should return null for an unknown reviewer");
    }

    private static PullRequestReview review(
            final String login, final PullRequestReview.Status status)
            throws MalformedURLException {
        return new PullRequestReview(
                new GitHubUser(login, new URL("https://example.test/" + login)), status);
    }
}
