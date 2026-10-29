package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jagent.desktop.models.GitHubUser;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestChecks;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.PullRequestReviews;
import com.jagent.desktop.test.SwingTestSupport;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.List;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class PullRequestSummaryPanelUiTest {
    @Test
    void rendersEmptySelection() {
        final var panel = GuiActionRunner.execute(PullRequestSummaryPanel::new);

        GuiActionRunner.execute(() -> panel.render(null, null));

        assertNotNull(
                SwingTestSupport.find(
                        panel,
                        JLabel.class,
                        label -> "Select a pull request to see details.".equals(label.getText())),
                "empty selection should explain what belongs in the panel");
    }

    @Test
    void rendersRequestFactsAndLoadingDetails() throws InterruptedException, MalformedURLException {
        final var request = request();
        final var panel = GuiActionRunner.execute(PullRequestSummaryPanel::new);

        GuiActionRunner.execute(() -> panel.render(request, null));

        SwingTestSupport.await(
                () -> SwingTestSupport.find(panel, JEditorPane.class) != null,
                "request body editor should be rendered");
        assertNotNull(
                SwingTestSupport.find(panel, JLabel.class, label -> "#12".equals(label.getText())),
                "request number should be rendered");
        assertNotNull(
                GuiActionRunner.execute(() -> SwingTestSupport.find(panel, JEditorPane.class)),
                "request body editor should be rendered");
        assertNotNull(
                SwingTestSupport.find(
                        panel,
                        JLabel.class,
                        label -> label.getText() != null && label.getText().contains("Open")),
                "request state should be rendered");
        assertEquals(
                2, panel.getComponentCount(), "summary should have header and content children");
    }

    @Test
    void rendersLoadedDetails() throws MalformedURLException {
        final var request = request();
        final var details =
                new PullRequestDetails(
                        request,
                        false,
                        false,
                        true,
                        "clean",
                        3,
                        1,
                        2,
                        new PullRequestChecks(List.of()),
                        new PullRequestReviews(List.of()));
        final var panel = GuiActionRunner.execute(PullRequestSummaryPanel::new);

        GuiActionRunner.execute(() -> panel.render(request, details));

        assertNotNull(
                SwingTestSupport.find(
                        panel, JLabel.class, label -> "Ready".equals(label.getText())),
                "merge status should be rendered from details");
    }

    private static PullRequest request() throws MalformedURLException {
        final var projectId = ProjectId.create();
        final var project = new Project("Project", "/tmp/project", null);
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
