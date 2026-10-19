package com.jagent.desktop.models;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jagent.desktop.ui.components.Theme;
import java.awt.Color;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
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

        final PullRequestDetails details = details(false, true, "blocked", checks);

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
    void pendingChecksTakePriorityOverBlockedMergeState() {
        final PullRequestCheck pending =
                new PullRequestCheck(
                        "ci/pending",
                        PullRequestCheck.Status.IN_PROGRESS,
                        null,
                        PullRequestCheck.Conclusion.UNKNOWN,
                        null);
        final PullRequestDetails details =
                details(false, false, "blocked", new PullRequestChecks(List.of(pending)));

        assertEquals(
                PullRequestDetails.Status.PENDING,
                details.status(),
                "pending checks should take priority");
        assertEquals(
                Theme.Colors.warning(),
                details.indicatorColor(),
                "pending checks should use the warning color");
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

        final PullRequestDetails failingDraft = details(true, true, "clean", checks);
        final PullRequestDetails conflictedDraft =
                details(true, false, "dirty", new PullRequestChecks(List.of()));

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
                details(draft, mergeable, mergeableState, new PullRequestChecks(List.of()));
        assertEquals(expectedStatus, details.status(), "status should match mergeability flags");
        assertEquals(expectedColor, details.indicatorColor(), "indicator color should match");
    }

    private static PullRequestDetails details(
            final boolean draft,
            final boolean mergeable,
            final String mergeableState,
            final PullRequestChecks checks) {
        try {
            final PullRequest request =
                    new PullRequest(
                            PROJECT_ID,
                            PROJECT,
                            1,
                            PullRequest.State.OPEN,
                            "Test",
                            "",
                            new URL("https://example.test/1"),
                            new Date(),
                            new Date(),
                            new GitHubUser("author", new URL("https://example.test/author")),
                            "head",
                            "sha",
                            "main");
            return new PullRequestDetails(
                    request, draft, mergeable, mergeableState, 1, 1, 1, checks);
        } catch (MalformedURLException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
