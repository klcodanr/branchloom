package com.jagent.desktop.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Date;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHCommitState;

class PullRequestCheckModelTest {
    @Test
    void fromCommitStatusHandlesNullStateAndInvalidUrl() {
        final Date updatedAt = new Date(1_000);

        final PullRequestCheck check =
                PullRequestCheck.fromCommitStatus("ci/build", null, updatedAt, "::::invalid::::");

        assertEquals(
                PullRequestCheck.Status.UNKNOWN,
                check.status(),
                "null state should map to UNKNOWN");
        assertEquals(
                PullRequestCheck.Conclusion.UNKNOWN,
                check.conclusion(),
                "null state should map to UNKNOWN conclusion");
        assertNull(check.detailsUrl(), "invalid status target URL should be ignored");
        assertEquals(updatedAt, check.completedAt(), "updated timestamp should be preserved");
    }

    @Test
    void fromCommitStatusMapsCommitStatesConsistently() {
        final PullRequestCheck pending =
                PullRequestCheck.fromCommitStatus("ci/pending", GHCommitState.PENDING, null, null);
        final PullRequestCheck success =
                PullRequestCheck.fromCommitStatus("ci/success", GHCommitState.SUCCESS, null, null);
        final PullRequestCheck failure =
                PullRequestCheck.fromCommitStatus("ci/failure", GHCommitState.FAILURE, null, null);

        assertEquals(
                PullRequestCheck.Status.IN_PROGRESS,
                pending.status(),
                "pending commit status should map to IN_PROGRESS");
        assertEquals(
                PullRequestCheck.Conclusion.UNKNOWN,
                pending.conclusion(),
                "pending commit status should keep UNKNOWN conclusion");
        assertEquals(
                PullRequestCheck.Status.COMPLETED,
                success.status(),
                "success commit status should map to COMPLETED");
        assertEquals(
                PullRequestCheck.Conclusion.SUCCESS,
                success.conclusion(),
                "success commit status should map to SUCCESS conclusion");
        assertEquals(
                PullRequestCheck.Status.COMPLETED,
                failure.status(),
                "failure commit status should map to COMPLETED");
        assertEquals(
                PullRequestCheck.Conclusion.FAILURE,
                failure.conclusion(),
                "failure commit status should map to FAILURE conclusion");
    }
}
