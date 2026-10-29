package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.GitHubUser;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestChecks;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.PullRequestReviews;
import com.jagent.desktop.models.SessionSummaryInfo;
import com.jagent.desktop.services.git.GitRepository;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SessionSummaryUiTest {
    @Test
    void cleanupAlertRequiresMergedPullRequestAndCleanWorktree() throws MalformedURLException {
        final PullRequestDetails merged = details(PullRequest.State.CLOSED, true);
        final PullRequestDetails open = details(PullRequest.State.OPEN, false);
        final GitRepository.WorktreeStatus clean = status(Set.of());
        final GitRepository.WorktreeStatus dirty = status(Set.of("changed.txt"));

        assertTrue(
                SessionSummary.shouldShowCleanupAlert(new SessionSummaryInfo(null, merged, clean)),
                "a merged pull request with a clean worktree should be ready for cleanup");
        assertFalse(
                SessionSummary.shouldShowCleanupAlert(new SessionSummaryInfo(null, merged, dirty)),
                "a dirty worktree should not be eligible for cleanup");
        assertFalse(
                SessionSummary.shouldShowCleanupAlert(new SessionSummaryInfo(null, open, clean)),
                "an open pull request should not be eligible for cleanup");
        assertFalse(
                SessionSummary.shouldShowCleanupAlert(null),
                "missing session summary data should not show the cleanup alert");
    }

    private static GitRepository.WorktreeStatus status(final Set<String> changed) {
        return new GitRepository.WorktreeStatus(
                "feature/test", Set.of(), Set.of(), changed, Set.of(), Set.of(), Set.of());
    }

    private static PullRequestDetails details(final PullRequest.State state, final boolean merged)
            throws MalformedURLException {
        final Project project = Project.create("Demo", "/tmp/demo", null);
        final PullRequest request =
                new PullRequest(
                        ProjectId.create(),
                        project,
                        7,
                        state,
                        "Title",
                        "Body",
                        new URL("https://example.test/pr/7"),
                        new Date(),
                        new Date(),
                        new GitHubUser("author", new URL("https://example.test/author")),
                        "feature/test",
                        "abc123",
                        "main");
        return new PullRequestDetails(
                request,
                false,
                merged,
                true,
                "clean",
                0,
                0,
                0,
                new PullRequestChecks(List.of()),
                new PullRequestReviews(List.of()));
    }
}
