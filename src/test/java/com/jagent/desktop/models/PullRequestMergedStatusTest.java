package com.jagent.desktop.models;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jagent.desktop.ui.components.Theme;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

class PullRequestMergedStatusTest {
    @Test
    void mergedPullRequestHasMergedDetailsStatus() throws MalformedURLException {
        final PullRequest request = request();
        final PullRequestDetails details =
                new PullRequestDetails(
                        request,
                        false,
                        true,
                        true,
                        "clean",
                        1,
                        1,
                        1,
                        new PullRequestChecks(List.of()),
                        new PullRequestReviews(List.of()));

        assertEquals(
                PullRequest.State.CLOSED,
                request.state(),
                "a merged pull request retains GitHub's closed lifecycle state");
        assertEquals(
                PullRequestDetails.Status.MERGED,
                details.status(),
                "the details status should expose the merge state");
        assertEquals(
                Theme.Colors.success(),
                details.indicatorColor(),
                "merged pull requests should use the success indicator");
    }

    private static PullRequest request() throws MalformedURLException {
        final Project project = Project.create("Demo", "/tmp/demo", null);
        return new PullRequest(
                ProjectId.create(),
                project,
                1,
                PullRequest.State.CLOSED,
                "Test",
                "",
                new URL("https://example.test/1"),
                new Date(),
                new Date(),
                new GitHubUser("author", new URL("https://example.test/author")),
                "head",
                "sha",
                "main");
    }
}
