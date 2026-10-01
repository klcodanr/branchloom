package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.GitHubUser;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestDetails;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import org.junit.jupiter.api.Test;

class PresentationHelpersTest {
    @Test
    void createsAnAppIconAtTheRequestedSize() {
        final BufferedImage image = AppIcon.image(32);
        assertEquals(32, image.getWidth(), "icon width should match requested size");
        assertEquals(32, image.getHeight(), "icon height should match requested size");
    }

    @Test
    void wrapsContentWithTheStandardInset() {
        final JLabel content = new JLabel("Content");
        final JPanel body = TabBody.wrap(content);
        assertSame(content, body.getComponent(0), "wrapped panel should contain content");
        assertEquals(TabBody.INSET, body.getInsets().top, "top inset should match standard inset");
    }

    @Test
    void detailsHtmlEscapesTitleAndRendersLifecycle() throws MalformedURLException {
        final PullRequest request = request("Fix <login>");
        final PullRequestDetails details = details(true, false, "clean");
        final String html = GitFormatter.detailsHtml(request, details);

        assertTrue(html.contains("Fix &lt;login&gt;"), "title should be escaped in HTML");
        assertTrue(html.contains("Draft"), "status text should reflect draft lifecycle");
    }

    @Test
    void statusHtmlUsesPendingColor() {
        Theme.applySwingDefaults();
        final PullRequestDetails details = details(true, false, "draft");
        final String status = GitFormatter.statusHtml(details);
        assertTrue(status.contains("Draft"), "draft status should be displayed");
    }

    @Test
    void rendersEmptyAndUnavailableDiffs() {
        final JPanel diff = new JPanel();
        GitFormatter.renderDiff(diff, "");
        assertEquals(1, diff.getComponentCount(), "diff panel should contain one component");
        assertEquals(
                "No changes in worktree",
                ((JTextArea) diff.getComponent(0)).getText(),
                "empty diffs should render the empty-worktree message");

        GitFormatter.renderDiff(diff, "Unavailable: no worktree");
        assertEquals(
                "Unavailable: no worktree",
                ((JTextArea) diff.getComponent(0)).getText(),
                "unavailable diffs should render source message");
    }

    @Test
    void displayTextHelpersFormatValues() {
        assertEquals(
                "a&amp;b&lt;c&gt;",
                UiText.escapeHtml("a&b<c>"),
                "escapeHtml should encode special characters");
        assertEquals(
                "#0a14ff",
                UiText.colorHex(new Color(10, 20, 255)),
                "colorHex should convert RGB values");
        assertEquals(
                "Ready For Review",
                UiText.titleCase("READY_FOR_REVIEW"),
                "titleCase should format check status tokens");
        assertEquals(
                "Ready Review",
                UiText.titleCase("READY__REVIEW"),
                "titleCase should collapse repeated separators");
        assertEquals("", UiText.titleCase("___"), "titleCase should handle separator-only tokens");
    }

    private static PullRequest request(final String title) throws MalformedURLException {
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
                new Date(),
                new Date(),
                new GitHubUser("author", new URL("https://example.test/author")),
                "feature/test",
                "abc1234def5678",
                "main");
    }

    private static PullRequestDetails details(
            final boolean draft, final boolean mergeable, final String mergeableState) {
        final ProjectId projectId = ProjectId.create();
        final Project project = new Project("Demo", "/tmp/demo", null);
        return new PullRequestDetails(
                projectId, project, 12, draft, mergeable, mergeableState, 2, 1, 1);
    }
}
