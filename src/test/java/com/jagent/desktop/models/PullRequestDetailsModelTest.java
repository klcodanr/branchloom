package com.jagent.desktop.models;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jagent.desktop.ui.components.Theme;
import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;

class PullRequestDetailsModelTest {
    private static final Project PROJECT = new Project("Demo", "/tmp/demo", null);
    private static final ProjectId PROJECT_ID = ProjectId.create();

    @Test
    void statusAndColorReflectDraftAndMergeability() {
        assertStatus("draft", true, true, PullRequestDetails.Status.DRAFT, Theme.Colors.muted());
        assertStatus("clean", false, true, PullRequestDetails.Status.READY, Theme.Colors.success());
        assertStatus(
                "blocked", false, false, PullRequestDetails.Status.BLOCKED, Theme.Colors.warning());
        assertStatus(
                "dirty", false, false, PullRequestDetails.Status.CONFLICTED, Theme.Colors.danger());
        assertStatus(
                "unstable",
                false,
                false,
                PullRequestDetails.Status.CHECKS_FAILING,
                Theme.Colors.danger());
        assertStatus(
                "unknown", false, false, PullRequestDetails.Status.OTHER, Theme.Colors.muted());
    }

    @Test
    void failingChecksTakePriorityOverUnknownOrBlockedMergeState() {
        final PullRequestCheck failure =
                new PullRequestCheck(
                        "ci/failure",
                        PullRequestCheck.Status.COMPLETED,
                        null,
                        PullRequestCheck.Conclusion.FAILURE,
                        null);
        final PullRequestChecks checks = new PullRequestChecks(List.of(failure));

        final PullRequestDetails details =
                new PullRequestDetails(
                        PROJECT_ID, PROJECT, 1, false, true, "blocked", 1, 1, 1, checks);

        assertEquals(
                PullRequestDetails.Status.CHECKS_FAILING,
                details.status(),
                "failing checks should override the blocked merge state");
        assertEquals(
                Theme.Colors.danger(),
                details.indicatorColor(),
                "failing checks should use the danger indicator color");
    }

    @Test
    void failuresAndConflictsTakePriorityOverDraftState() {
        final PullRequestCheck failure =
                new PullRequestCheck(
                        "ci/failure",
                        PullRequestCheck.Status.COMPLETED,
                        null,
                        PullRequestCheck.Conclusion.FAILURE,
                        null);
        final PullRequestChecks checks = new PullRequestChecks(List.of(failure));

        final PullRequestDetails failingDraft =
                new PullRequestDetails(
                        PROJECT_ID, PROJECT, 1, true, true, "clean", 1, 1, 1, checks);
        final PullRequestDetails conflictedDraft =
                new PullRequestDetails(PROJECT_ID, PROJECT, 1, true, false, "dirty", 1, 1, 1);

        assertEquals(
                PullRequestDetails.Status.CHECKS_FAILING,
                failingDraft.status(),
                "failing checks should override draft state");
        assertEquals(
                Theme.Colors.danger(),
                failingDraft.indicatorColor(),
                "failing draft checks should use the danger indicator color");
        assertEquals(
                PullRequestDetails.Status.CONFLICTED,
                conflictedDraft.status(),
                "conflicts should override draft state");
        assertEquals(
                Theme.Colors.danger(),
                conflictedDraft.indicatorColor(),
                "conflicted drafts should use the danger indicator color");
    }

    private static void assertStatus(
            final String mergeableState,
            final boolean draft,
            final boolean mergeable,
            final PullRequestDetails.Status expectedStatus,
            final Color expectedColor) {
        final PullRequestDetails details =
                new PullRequestDetails(
                        PROJECT_ID, PROJECT, 1, draft, mergeable, mergeableState, 1, 1, 1);
        assertEquals(expectedStatus, details.status(), "status should match mergeability flags");
        assertEquals(expectedColor, details.indicatorColor(), "indicator color should match");
    }
}
