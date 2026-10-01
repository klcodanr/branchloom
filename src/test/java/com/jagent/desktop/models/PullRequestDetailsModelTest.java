package com.jagent.desktop.models;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jagent.desktop.ui.components.Theme;
import java.awt.Color;
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
