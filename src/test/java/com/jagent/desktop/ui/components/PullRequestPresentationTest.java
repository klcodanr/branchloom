package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.GitHubUser;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestCheck;
import com.jagent.desktop.models.PullRequestChecks;
import com.jagent.desktop.models.PullRequestDetails;
import java.awt.Color;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

class PullRequestPresentationTest {
    @Test
    void formatsKeysAndDisplayValues() throws MalformedURLException {
        final PullRequest request = request("Fix login", "author");

        assertTrue(
                PullRequestPresentation.checksKey(request)
                        .contains(request.projectId().value().toString()),
                "checks key should include project id");
        assertTrue(
                PullRequestPresentation.bodyKey(request)
                        .contains(Integer.toString(request.number())),
                "body key should include request number");
        assertEquals(
                request.projectId() + ":" + request.number(),
                PullRequestPresentation.detailKey(request),
                "detail key should include project id and number");
        assertEquals(
                "",
                PullRequestPresentation.detailKey(null),
                "null requests should use empty detail key");
    }

    @Test
    void formatsBodyAndErrorHtml() {
        final String body = PullRequestPresentation.bodyHtml("a & b");
        assertTrue(body.contains("<html>"), "body HTML should include wrapper");
        assertTrue(
                body.contains("a &amp; b") || body.contains("a & b"),
                "body should include rendered content");
        assertTrue(
                PullRequestPresentation.bodyHtml("").contains("No description provided."),
                "blank descriptions should render placeholder");
        assertTrue(
                PullRequestPresentation.loadingBodyHtml().contains("Loading description"),
                "loading HTML should contain loading text");
        assertTrue(
                PullRequestPresentation.errorBodyHtml(new IllegalStateException("broken <tag>"))
                        .contains("&lt;tag&gt;"),
                "error HTML should escape error details");
    }

    @Test
    void formatsChangesStatusAndSelectionDisplay() throws MalformedURLException {
        Theme.applySwingDefaults();
        final PullRequestDetails details = details("draft", true);

        assertTrue(
                PullRequestPresentation.changesHtml(details).contains("+2"),
                "changes HTML should include additions");
        assertTrue(
                PullRequestPresentation.changesTooltip(details).contains("files 1"),
                "changes tooltip should include file count");
        assertNotNull(
                PullRequestPresentation.mergeStatusColor(details),
                "merge status color should resolve for details");
        assertNotNull(PullRequestPresentation.selectionColor(), "selection color should resolve");
        assertEquals(
                "Unknown", UiText.relativeTime((Date) null), "missing timestamp should be unknown");
        assertTrue(
                UiText.relativeTime(new Date()).contains("Just now")
                        || UiText.relativeTime(new Date()).contains("ago"),
                "known timestamps should format as relative times");
        assertTrue(
                PullRequestPresentation.contains("Fix Login", "fix"),
                "contains should be case-insensitive");
    }

    @Test
    void handlesNullDetailsGracefully() {
        final Color mergeStatus = PullRequestPresentation.mergeStatusColor(null);
        assertNotNull(mergeStatus, "merge status should fallback when details are missing");
        assertEquals(
                "Loading...",
                PullRequestPresentation.changesHtml(null),
                "null details should show loading");
        assertEquals(
                "Loading changes...",
                PullRequestPresentation.changesTooltip(null),
                "null details should show loading tooltip");
    }

    @Test
    void formatsPullRequestStatusAndCheckCount() {
        final PullRequestCheck passed =
                new PullRequestCheck(
                        "build",
                        PullRequestCheck.Status.COMPLETED,
                        null,
                        PullRequestCheck.Conclusion.SUCCESS,
                        null);
        final PullRequestCheck pending =
                new PullRequestCheck(
                        "test",
                        PullRequestCheck.Status.IN_PROGRESS,
                        null,
                        PullRequestCheck.Conclusion.UNKNOWN,
                        null);
        assertEquals(
                "1/2 passed",
                PullRequestPresentation.checksPassed(
                        new PullRequestChecks(List.of(passed, pending))),
                "check count should show passed checks over total checks");
    }

    private static PullRequest request(final String title, final String author)
            throws MalformedURLException {
        final ProjectId projectId = ProjectId.create();
        final Project project = new Project("Demo", "/tmp/demo", null);
        return new PullRequest(
                projectId,
                project,
                12,
                PullRequest.State.OPEN,
                title,
                "Description",
                new URL("https://example.test/12"),
                new Date(1_700_000_000_000L),
                new Date(1_700_100_000_000L),
                new GitHubUser(author, new URL("https://example.test/" + author)),
                "feature/test",
                "abc1234def5678",
                "main");
    }

    private static PullRequestDetails details(final String mergeState, final boolean draft) {
        final ProjectId projectId = ProjectId.create();
        final Project project = new Project("Demo", "/tmp/demo", null);
        return new PullRequestDetails(projectId, project, 12, draft, false, mergeState, 2, 1, 1);
    }
}
