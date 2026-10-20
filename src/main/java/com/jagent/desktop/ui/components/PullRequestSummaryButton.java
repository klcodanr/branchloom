package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.services.PlatformCommands;
import java.awt.Color;
import java.net.URL;
import javax.swing.JButton;
import org.eclipse.jgit.annotations.Nullable;

/** A single link control that renders the pull request summary. */
public final class PullRequestSummaryButton extends JButton {
    private static final String LOADING_TITLE = "Loading pull request...";
    private URL pullRequestUrl;

    public PullRequestSummaryButton() {
        super();
        setHorizontalAlignment(LEFT);
        addActionListener(event -> openPullRequest());
        showLoading();
    }

    public void setPullRequest(@Nullable final PullRequestDetails pullRequest) {
        pullRequestUrl = pullRequest == null ? null : pullRequest.url();
        if (pullRequest == null) {
            setText("Could not load pull request");
            setEnabled(false);
        } else {
            render(
                    "#" + pullRequest.number() + " " + pullRequest.title(),
                    "Status: " + UiText.titleCase(pullRequest.status().name()),
                    "Checks: " + PullRequestPresentation.checksPassed(pullRequest.checks()),
                    pullRequest.indicatorColor());
            setEnabled(true);
        }
    }

    public void showLoading() {
        pullRequestUrl = null;
        setEnabled(false);
        render(LOADING_TITLE, "Status: Loading...", "Checks: Loading...", Theme.Colors.muted());
    }

    private void openPullRequest() {
        if (pullRequestUrl != null) {
            PlatformCommands.openUrl(pullRequestUrl.toExternalForm());
        }
    }

    private void render(
            final String title, final String status, final String checks, final Color indicator) {
        setForeground(Theme.Colors.foreground());
        putClientProperty("JComponent.outline", indicator);
        setText(
                "<html><span color=\""
                        + UiText.colorHex(indicator)
                        + "\">&#9679;</span> "
                        + UiText.escapeHtml(title)
                        + "<br><font size=\"-1\">"
                        + UiText.escapeHtml(status)
                        + "  "
                        + UiText.escapeHtml(checks)
                        + "</font></html>");
    }
}
