package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.GitHubUser;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestChecks;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.PullRequestReviews;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class PullRequestSummaryButtonTest {
    @Test
    void rendersLoadingAndUnavailableStates() {
        final PullRequestSummaryButton button =
                GuiActionRunner.execute(PullRequestSummaryButton::new);

        assertTrue(
                button.getText().contains("Loading pull request"),
                "button should show loading state");

        GuiActionRunner.execute(() -> button.setPullRequest(null));

        assertTrue(
                button.getText().contains("Could not load pull request"),
                "button should show unavailable state");
        assertTrue(!button.isEnabled(), "button should be disabled when no pull request is loaded");
    }

    @Test
    void enablesLoadedPullRequestForOpening() throws MalformedURLException {
        final PullRequestSummaryButton button =
                GuiActionRunner.execute(PullRequestSummaryButton::new);
        final ProjectId projectId = ProjectId.create();
        final Project project = Project.create("Demo", "/tmp/demo", null);
        final PullRequest request =
                new PullRequest(
                        projectId,
                        project,
                        12,
                        PullRequest.State.OPEN,
                        "Fix login",
                        "Description",
                        new URL("https://example.test/12"),
                        new Date(),
                        new Date(),
                        new GitHubUser("author", new URL("https://example.test/author")),
                        "feature/login-fix",
                        "abc123",
                        "main");
        final PullRequestDetails details =
                new PullRequestDetails(
                        request,
                        false,
                        false,
                        true,
                        "clean",
                        1,
                        0,
                        1,
                        new PullRequestChecks(List.of()),
                        new PullRequestReviews(List.of()));

        GuiActionRunner.execute(() -> button.setPullRequest(details));

        assertTrue(button.isEnabled(), "button should be enabled when a pull request is loaded");
        assertTrue(button.getText().contains("#12 Fix login"), "button should show PR identity");
    }
}
