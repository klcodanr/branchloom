package com.jagent.desktop.ui.views;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import org.junit.jupiter.api.Test;

class ReviewQueueViewTest {
    @Test
    void readyRequestsAreFirstAndReceiveGeneralFocus() {
        final PullRequest request = request(false, "MERGEABLE", "PASSING");

        assertEquals(
                0, ReviewQueueView.bucketFor(request), "ready requests belong in the first bucket");
        assertEquals(
                "Review requested, ready to inspect, and not blocked by checks or conflicts.",
                ReviewQueueView.reasonFor(request),
                "ready requests should explain that they are unblocked");
        assertEquals(
                "Review the change, checks, and recent comments.",
                ReviewQueueView.focusFor(request),
                "ready requests should receive general review focus");
    }

    @Test
    void draftRequestsExplainTheirReadinessState() {
        final PullRequest request = request(true, "MERGEABLE", "PASSING");

        assertEquals(
                1,
                ReviewQueueView.bucketFor(request),
                "draft requests belong in the waiting bucket");
        assertEquals(
                "Review requested, but the request is waiting on author, checks, or mergeability.",
                ReviewQueueView.reasonFor(request),
                "draft requests should explain their waiting state");
        assertEquals(
                "Confirm whether the draft is ready for review.",
                ReviewQueueView.focusFor(request),
                "draft requests should focus on readiness");
    }

    @Test
    void failingAndConflictingRequestsPrioritizeTheSpecificBlocker() {
        final PullRequest failing = request(false, "MERGEABLE", "FAILING");
        final PullRequest conflicting = request(false, "CONFLICTING", "PASSING");

        assertEquals(1, ReviewQueueView.bucketFor(failing), "failing requests should be blocked");
        assertEquals(
                "Check failing CI before spending time on implementation details.",
                ReviewQueueView.focusFor(failing),
                "failing requests should focus on CI");
        assertEquals(
                1,
                ReviewQueueView.bucketFor(conflicting),
                "conflicting requests should be blocked");
        assertEquals(
                "Confirm the conflict scope and whether a useful review is possible.",
                ReviewQueueView.focusFor(conflicting),
                "conflicting requests should focus on conflict scope");
    }

    private static PullRequest request(
            final boolean draft, final String mergeable, final String checksStatus) {
        return new PullRequest(
                ProjectId.create(),
                1,
                "Title",
                "Description",
                "Comments",
                "https://example.test/pull/1",
                "created",
                "updated",
                "APPROVED",
                mergeable,
                draft,
                "author",
                "feature",
                1,
                1,
                checksStatus);
    }
}
