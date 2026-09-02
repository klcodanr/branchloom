package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.jagent.desktop.models.PullRequest;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class PresentationHelpersTest {
    private static final String VALUE_MESSAGE = "presentation value should match";

    @Test
    void createsAnAppIconAtTheRequestedSize() {
        final BufferedImage image = AppIcon.image(32);

        assertEquals(32, image.getWidth(), VALUE_MESSAGE);
        assertEquals(32, image.getHeight(), VALUE_MESSAGE);
        assertNotNull(image.getRGB(16, 16), "generated icon should contain pixels");
    }

    @Test
    void wrapsContentWithTheStandardInset() {
        final JLabel content = new JLabel("Content");

        final JPanel body = TabBody.wrap(content);

        assertSame(content, body.getComponent(0), VALUE_MESSAGE);
        assertEquals(TabBody.INSET, body.getInsets().top, VALUE_MESSAGE);
        assertEquals(TabBody.INSET, body.getInsets().left, VALUE_MESSAGE);
    }

    @Test
    void rendersDefaultGithubAuthOption() {
        final var selector = GuiActionRunner.execute(GitHubAuthSelector::render);

        assertNull(selector.getItemAt(0), "default account should be the first option");
        final var rendered =
                selector.getRenderer()
                        .getListCellRendererComponent(new JList<>(), null, 0, false, false);
        assertEquals("Default (active account)", ((JLabel) rendered).getText(), VALUE_MESSAGE);
    }

    @Test
    void formatsPullRequestStatusesForDetailsAndTooltips() {
        assertEquals(
                "Can merge",
                GitFormatter.mergeStatus("CLEAN"),
                "clean pull requests should be mergeable");
        assertEquals(
                "Can merge",
                GitFormatter.mergeStatus("MERGEABLE"),
                "mergeable pull requests should be mergeable");
        assertEquals(
                "Cannot merge",
                GitFormatter.mergeStatus("CONFLICTING"),
                "conflicting pull requests should not be mergeable");
        assertEquals(
                "Mergeability unknown",
                GitFormatter.mergeStatus("UNKNOWN"),
                "unknown merge states should remain unknown");

        final PullRequest request =
                new PullRequest(
                        null,
                        12,
                        "Fix <login>",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "OPEN",
                        "CLEAN",
                        false,
                        "",
                        "",
                        2,
                        3,
                        "PASSING");
        final String details = GitFormatter.detailsHtml(request);
        assertEquals(
                "<html><b>#12</b>  Fix &lt;login&gt;<br><font color='"
                        + UiText.colorHex(
                                javax.swing.UIManager.getColor(UiConstants.DISABLED_FOREGROUND))
                        + "'>Open  ·  Review: Open  ·  Can merge  ·  2/3 checks Passing</font></html>",
                details,
                "details should format and escape pull request values");
        assertEquals(
                "<html><b>#12</b>  Fix<br><font color='"
                        + UiText.colorHex(
                                javax.swing.UIManager.getColor(UiConstants.DISABLED_FOREGROUND))
                        + "'>Draft  ·  Review pending  ·  Mergeability unknown  ·  0/0 checks Unknown</font></html>",
                GitFormatter.detailsHtml(
                        new PullRequest(
                                null, 12, "Fix", "", "", "", "", "", "", "", true, "", "", 0, 0,
                                "UNKNOWN")),
                "draft details should show pending review and unknown states");
    }

    @Test
    void formatsCompactPullRequestStatusWithCheckColor() {
        Theme.applySwingDefaults();
        javax.swing.UIManager.put("Actions.Red", Color.RED);
        final String status =
                GitFormatter.statusHtml(
                        new PullRequest(
                                null,
                                1,
                                "",
                                "",
                                "",
                                "",
                                "",
                                "",
                                "",
                                "CONFLICTING",
                                false,
                                "",
                                "",
                                1,
                                2,
                                "FAILING"));

        assertEquals(
                "PR: <font color='"
                        + UiText.colorHex(Theme.dangerColor())
                        + "'>&#9679;</font> Cannot merge  ·  Checks: 1/2 Failing",
                status,
                "compact status should include merge and check state");
    }

    @Test
    void rendersEmptyAndUnavailableDiffs() {
        final JPanel diff = new JPanel();

        GitFormatter.renderDiff(diff, "");
        assertEquals(1, diff.getComponentCount(), "empty diffs should show a message");
        assertEquals(
                "No changes in worktree",
                ((JTextArea) diff.getComponent(0)).getText(),
                "empty diff text should explain the clean state");

        GitFormatter.renderDiff(diff, "Unavailable: no worktree");
        assertEquals(1, diff.getComponentCount(), "unavailable diffs should show a message");
        assertEquals(
                "Unavailable: no worktree",
                ((JTextArea) diff.getComponent(0)).getText(),
                "unavailable diff text should explain the failure");
    }

    @Test
    void rendersValidDiffRowsAndSkipsMalformedRows() {
        final JPanel diff = new JPanel();

        GitFormatter.renderDiff(diff, "4\t2\tsrc/App.java\nmalformed\n1\t0\tREADME.md");

        assertEquals(2, diff.getComponentCount(), "only valid diff rows should render");
        final JPanel first = (JPanel) diff.getComponent(0);
        assertEquals(
                "+4", ((JLabel) first.getComponent(0)).getText(), "additions should be rendered");
        assertEquals(
                "-2", ((JLabel) first.getComponent(1)).getText(), "deletions should be rendered");
        assertEquals(
                "src/App.java",
                ((JLabel) first.getComponent(2)).getText(),
                "path should be rendered");
    }
}
