package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestCheck;
import com.jagent.desktop.models.PullRequestChecks;
import com.jagent.desktop.services.PlatformCommands;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Renders checks summary and optional per-check details for a pull request. */
public final class PullRequestChecksPanel extends JPanel {
    private transient PullRequest request;
    private transient PullRequestChecks checks;
    private boolean loading;
    private boolean expanded;

    public PullRequestChecksPanel() {
        super();
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(UiBorders.contentArea());
    }

    public void render(
            final PullRequest request, final PullRequestChecks checks, final boolean loading) {
        this.request = request;
        this.checks = checks == null ? new PullRequestChecks(List.of()) : checks;
        this.loading = loading;
        removeAll();
        if (request == null) {
            return;
        }
        add(summaryLine(request));
        if (expanded && !this.checks.checks().isEmpty()) {
            add(Box.createVerticalStrut(UiConstants.SPACING_XS));
            for (final PullRequestCheck check : this.checks.checks()) {
                add(checkLine(check, request.url().toExternalForm()));
            }
        }
    }

    private JPanel summaryLine(final PullRequest request) {
        final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, UiConstants.SPACING_SM, 0));
        row.setOpaque(false);
        final JLabel summary =
                new JLabel(
                        UiText.titleCase(request.state().toString())
                                + " "
                                + checks.passed()
                                + " / "
                                + checks.total());
        summary.setFont(Theme.font(Theme.FontSize.SM));
        summary.setForeground(Theme.Colors.muted());
        row.add(summary);
        if (loading) {
            final JLabel loadingLabel = new JLabel("Loading checks...");
            loadingLabel.setFont(Theme.font(Theme.FontSize.SM));
            loadingLabel.setForeground(Theme.Colors.muted());
            row.add(loadingLabel);
            return row;
        }
        if (checks.checks().isEmpty()) {
            final JLabel none = new JLabel("(no check details)");
            none.setFont(Theme.font(Theme.FontSize.SM));
            none.setForeground(Theme.Colors.muted());
            row.add(none);
            return row;
        }
        final JButton toggle =
                new LinkButton(expanded ? "Hide details" : "Show details", this::toggle);
        row.add(toggle);
        return row;
    }

    private JPanel checkLine(final PullRequestCheck check, final String fallbackUrl) {
        final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, UiConstants.SPACING_SM, 0));
        row.setOpaque(false);
        final String status = displayStatus(check);
        final JLabel statusLabel = new JLabel(status);
        statusLabel.setFont(Theme.font(Theme.FontSize.SM));
        statusLabel.setForeground(check.indicatorColor());
        row.add(statusLabel);
        final String linkUrl =
                check.detailsUrl() == null ? fallbackUrl : check.detailsUrl().toExternalForm();
        final JButton link = new LinkButton(check.name(), () -> PlatformCommands.openUrl(linkUrl));

        row.add(link);
        return row;
    }

    private void toggle() {
        expanded = !expanded;
        render(request, checks, loading);
        revalidate();
        repaint();
    }

    private static String displayStatus(final PullRequestCheck check) {
        if (check.conclusion() != null) {
            return UiText.titleCase(check.conclusion().toString());
        }
        if (check.status() != null) {
            return UiText.titleCase(check.status().toString());
        }
        return "Unknown";
    }
}
