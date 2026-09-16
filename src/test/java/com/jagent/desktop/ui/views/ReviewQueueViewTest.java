package com.jagent.desktop.ui.views;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import org.junit.jupiter.api.Test;

class ReviewQueueViewTest {
    @Test
    void contextIncludesDiffChecksMergeabilityAndDraftState() {
        final PullRequest request = request(false, "MERGEABLE", "PASSING", 24, 8, 5, 3, 4);

        assertEquals(
                "Change size: +24 / -8 across 5 files  ·  Checks: 3/4 PASSING  ·  Mergeability: MERGEABLE  ·  Draft: false",
                ReviewQueueView.contextFor(request),
                "context should include rich pull request details for agent planning");
    }

    @Test
    void commentSummaryFallsBackWhenMissing() {
        final PullRequest blank = request(false, "MERGEABLE", "PASSING", 10, 5, 1, 1, 1, "");

        assertEquals(
                "No recent comments captured.",
                ReviewQueueView.commentSummaryFor(blank),
                "blank comment summaries should use fallback text");
    }

    @Test
    void commentSummaryReturnsRecentCommentsWhenPresent() {
        final PullRequest withComments =
                request(
                        false,
                        "MERGEABLE",
                        "PASSING",
                        10,
                        5,
                        1,
                        1,
                        1,
                        "alice: can we trim this? | bob: fixed in latest push");

        assertEquals(
                "alice: can we trim this? | bob: fixed in latest push",
                ReviewQueueView.commentSummaryFor(withComments),
                "captured comments should be returned unchanged");
    }

    private static PullRequest request(
            final boolean draft,
            final String mergeable,
            final String checksStatus,
            final int additions,
            final int deletions,
            final int changedFiles,
            final int checksPassed,
            final int checksTotal) {
        return request(
                draft,
                mergeable,
                checksStatus,
                additions,
                deletions,
                changedFiles,
                checksPassed,
                checksTotal,
                "Comments");
    }

    private static PullRequest request(
            final boolean draft,
            final String mergeable,
            final String checksStatus,
            final int additions,
            final int deletions,
            final int changedFiles,
            final int checksPassed,
            final int checksTotal,
            final String comments) {
        return new PullRequest(
                ProjectId.create(),
                1,
                "Title",
                "Description",
                comments,
                "https://example.test/pull/1",
                "created",
                "updated",
                "APPROVED",
                mergeable,
                draft,
                "author",
                "feature",
                additions,
                deletions,
                changedFiles,
                checksPassed,
                checksTotal,
                checksStatus);
    }
}
