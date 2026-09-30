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
        assertStatus("draft", true, true, PullRequestDetails.Status.DRAFT, Theme.mutedColor());
        assertStatus("clean", false, true, PullRequestDetails.Status.READY, Theme.successColor());
        assertStatus(
                "blocked", false, false, PullRequestDetails.Status.BLOCKED, Theme.warningColor());
        assertStatus(
                "dirty", false, false, PullRequestDetails.Status.CONFLICTED, Theme.dangerColor());
        assertStatus(
                "unstable",
                false,
                false,
                PullRequestDetails.Status.CHECKS_FAILING,
                Theme.dangerColor());
        assertStatus("unknown", false, false, PullRequestDetails.Status.OTHER, Theme.mutedColor());
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
