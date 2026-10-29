package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jagent.desktop.models.GitHubUser;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestCheck;
import com.jagent.desktop.models.PullRequestChecks;
import com.jagent.desktop.test.SwingTestSupport;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import javax.swing.JLabel;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class PullRequestChecksPanelUiTest {
    @Test
    void rendersEmptyAndLoadingStates() throws MalformedURLException {
        final var request = request();
        final var panel = GuiActionRunner.execute(PullRequestChecksPanel::new);

        GuiActionRunner.execute(() -> panel.render(null, null, false));
        assertEquals(0, panel.getComponentCount(), "null requests should render no content");

        GuiActionRunner.execute(() -> panel.render(request, null, true));
        assertNotNull(
                SwingTestSupport.find(
                        panel, JLabel.class, label -> "Loading checks...".equals(label.getText())),
                "loading state should be visible");
    }

    @Test
    void togglesCheckDetailsAndUsesConclusionOrStatus() throws MalformedURLException {
        final var request = request();
        final var checks =
                new PullRequestChecks(
                        List.of(
                                new PullRequestCheck(
                                        "Build",
                                        PullRequestCheck.Status.COMPLETED,
                                        new Date(),
                                        PullRequestCheck.Conclusion.SUCCESS,
                                        null),
                                new PullRequestCheck(
                                        "Lint",
                                        PullRequestCheck.Status.IN_PROGRESS,
                                        null,
                                        null,
                                        new URL("https://example.test/lint"))));
        final var panel = GuiActionRunner.execute(PullRequestChecksPanel::new);

        GuiActionRunner.execute(() -> panel.render(request, checks, false));
        assertNotNull(
                SwingTestSupport.findButton(panel, "Show details"),
                "non-empty checks should offer details");
        GuiActionRunner.execute(() -> SwingTestSupport.findButton(panel, "Show details").doClick());

        assertNotNull(SwingTestSupport.findButton(panel, "Hide details"), "details should expand");
        assertNotNull(
                SwingTestSupport.find(
                        panel, JLabel.class, label -> "Success".equals(label.getText())),
                "conclusion should be rendered when available");
        assertNotNull(
                SwingTestSupport.find(
                        panel,
                        JLabel.class,
                        label -> label.getText() != null && label.getText().contains("Progress")),
                "status should be rendered when conclusion is absent");
        assertNotNull(
                SwingTestSupport.findButton(panel, "Build"),
                "check name should be rendered as a link");
    }

    private static PullRequest request() throws MalformedURLException {
        final var projectId = ProjectId.create();
        final var project = Project.create("Project", "/tmp/project", null);
        return new PullRequest(
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
                "feature",
                "abc123",
                "main");
    }
}
