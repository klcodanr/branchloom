package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.PullRequestDetails;
import java.awt.Color;
import javax.swing.JButton;
import org.eclipse.jgit.annotations.Nullable;

/** A single link control that renders the pull request summary. */
public final class PullRequestSummaryButton extends JButton {
    private static final String LOADING_TITLE = "Loading pull request...";

    public PullRequestSummaryButton() {
        super();
        setHorizontalAlignment(LEFT);
        showLoading();
    }

    public void setPullRequest(@Nullable final PullRequestDetails pullRequest) {
        if (pullRequest == null) {
            setText("Could not load pull request");
        } else {
            render(
                    "#" + pullRequest.number() + " " + pullRequest.title(),
                    "Status: " + UiText.titleCase(pullRequest.status().name()),
                    "Checks: " + PullRequestPresentation.checksPassed(pullRequest.checks()),
                    pullRequest.indicatorColor());
        }
    }

    public void showLoading() {
        render(LOADING_TITLE, "Status: Loading...", "Checks: Loading...", Theme.Colors.muted());
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
