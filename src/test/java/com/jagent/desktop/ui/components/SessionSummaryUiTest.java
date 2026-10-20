package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.GitHubUser;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionSummaryInfo;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.test.TestAppState;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
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

    @Test
    void constructsWithCurrentSessionStateApi() throws java.io.InvalidObjectException {
        final AppState state = TestAppState.empty();
        final ProjectId projectId = state.addProject(new Project("Demo", "/tmp/demo", null));
        final var sessionId =
                state.addSession(projectId, new Session(projectId, "Feature", null, null, null));
        assertNotNull(sessionId, "session should be added to the application state");
    }

    private static GitRepository.WorktreeStatus status(final Set<String> changed) {
        return new GitRepository.WorktreeStatus(
                "feature/test", Set.of(), Set.of(), changed, Set.of(), Set.of(), Set.of());
    }

    private static PullRequestDetails details(final PullRequest.State state, final boolean merged)
            throws MalformedURLException {
        final Project project = new Project("Demo", "/tmp/demo", null);
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
                        "main",
                        merged);
        return new PullRequestDetails(request, false, true, "clean", 0, 0, 0);
    }
}
