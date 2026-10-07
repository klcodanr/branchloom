package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.GitHubUser;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestCheck;
import com.jagent.desktop.models.PullRequestChecks;
import com.jagent.desktop.models.PullRequestDetails;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.assertj.swing.edt.GuiActionRunnable;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class PullRequestSummaryButtonTest {
    private static final String URL = "https://example.test/pull/12";

    @Test
    void rendersLoadingStateAndDoesNotOpenWithoutUrl() {
        final List<String> opened = new ArrayList<>();
        final PullRequestSummaryButton button =
                GuiActionRunner.execute(() -> new PullRequestSummaryButton(opened::add));

        assertTrue(
                button.getText().contains("Loading pull request status..."),
                "loading title should be rendered");
        assertTrue(
                button.getText().contains("Status: Loading..."),
                "loading status should be rendered");
        assertTrue(
                button.getText().contains("Checks: Loading..."),
                "loading checks should be rendered");

        GuiActionRunner.execute((GuiActionRunnable) button::doClick);

        assertTrue(opened.isEmpty(), "loading state should not open a URL");
    }

    @Test
    void rendersPullRequestAndOpensItsUrl() throws MalformedURLException {
        final List<String> opened = new ArrayList<>();
        final PullRequestSummaryButton button =
                GuiActionRunner.execute(() -> new PullRequestSummaryButton(opened::add));
        final PullRequest request = request();
        final PullRequestDetails details = details();

        GuiActionRunner.execute(() -> button.render(request, details));

        assertTrue(button.getText().contains("#12 Fix login"), "PR title should be rendered");
        assertTrue(
                button.getText().contains("Status: In Progress"), "PR status should be rendered");
        assertTrue(button.getText().contains("Checks: 1/2 passed"), "PR checks should be rendered");
        assertEquals(
                Theme.Colors.foreground(),
                button.getForeground(),
                "button text should retain the normal foreground color");
        assertEquals(
                Theme.Colors.warning(),
                button.getClientProperty("JComponent.outline"),
                "button border should match the pull request indicator color");
        assertEquals(URL, button.getToolTipText(), "PR URL should be the tooltip");

        GuiActionRunner.execute((GuiActionRunnable) button::doClick);

        assertEquals(List.of(URL), opened, "clicking the rendered PR should open its URL");
    }

    @Test
    void rendersNoPullRequestStateAndClearsUrl() throws MalformedURLException {
        final List<String> opened = new ArrayList<>();
        final PullRequestSummaryButton button =
                GuiActionRunner.execute(() -> new PullRequestSummaryButton(opened::add));

        GuiActionRunner.execute(() -> button.render(request(), details()));
        GuiActionRunner.execute(() -> button.renderUnavailable("ignored", true));

        assertTrue(
                button.getText().contains("No pull request associated with this branch"),
                "no-PR message should be rendered");
        assertTrue(
                button.getText().contains("Status: Unavailable"),
                "unavailable status should be rendered");
        assertTrue(
                button.getText().contains("Checks: Unavailable"),
                "unavailable checks should be rendered");
        assertNull(button.getToolTipText(), "unavailable state should not have a tooltip");

        GuiActionRunner.execute((GuiActionRunnable) button::doClick);

        assertTrue(opened.isEmpty(), "no-PR state should not open the previous URL");
    }

    @Test
    void rendersUnavailableMessage() {
        final PullRequestSummaryButton button =
                GuiActionRunner.execute(() -> new PullRequestSummaryButton(ignored -> {}));

        GuiActionRunner.execute(
                () -> button.renderUnavailable("Unavailable: GitHub is offline", false));

        assertTrue(
                button.getText().contains("Unavailable: GitHub is offline"),
                "failure message should be rendered");
        assertFalse(button.getText().contains("ignored"), "no-PR placeholder should not render");
        assertNull(button.getToolTipText(), "failure state should not have a tooltip");
    }

    private static PullRequest request() throws MalformedURLException {
        final ProjectId projectId = ProjectId.create();
        final Project project = new Project("Demo", "/tmp/demo", null);
        return new PullRequest(
                projectId,
                project,
                12,
                PullRequest.State.OPEN,
                "Fix login",
                "Description",
                new URL(URL),
                new Date(1_700_000_000_000L),
                new Date(1_700_100_000_000L),
                new GitHubUser("author", new URL("https://example.test/author")),
                "feature/test",
                "abc1234def5678",
                "main");
    }

    private static PullRequestDetails details() {
        final ProjectId projectId = ProjectId.create();
        final Project project = new Project("Demo", "/tmp/demo", null);
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
        return new PullRequestDetails(
                projectId,
                project,
                12,
                false,
                true,
                "clean",
                2,
                1,
                1,
                new PullRequestChecks(List.of(passed, pending)));
    }
}
