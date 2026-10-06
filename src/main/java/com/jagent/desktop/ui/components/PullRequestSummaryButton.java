package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestDetails;
import java.awt.Color;
import java.util.function.Consumer;
import javax.swing.JButton;

/** A single link control that renders the pull request summary. */
public final class PullRequestSummaryButton extends JButton {
    private static final String LOADING_TITLE = "Loading pull request status...";

    private final Consumer<String> openUrl;
    private String url;

    public PullRequestSummaryButton(final Consumer<String> openUrl) {
        super();
        this.openUrl = openUrl;
        setHorizontalAlignment(LEFT);
        addActionListener(
                event -> {
                    if (url != null) {
                        this.openUrl.accept(url);
                    }
                });
        render(LOADING_TITLE, "Status: Loading...", "Checks: Loading...", Theme.Colors.muted());
    }

    public void render(final PullRequest request, final PullRequestDetails details) {
        url = request.url().toExternalForm();
        setToolTipText(url);
        render(
                "#" + request.number() + " " + request.title(),
                "Status: " + UiText.titleCase(details.status().name()),
                "Checks: " + PullRequestPresentation.checksPassed(details.checks()),
                details.indicatorColor());
    }

    public void renderUnavailable(final String message, final boolean noPullRequest) {
        url = null;
        setToolTipText(null);
        render(
                noPullRequest ? "No pull request associated with this branch" : message,
                "Status: Unavailable",
                "Checks: Unavailable",
                Theme.Colors.muted());
    }

    private void render(
            final String title, final String status, final String checks, final Color indicator) {
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
