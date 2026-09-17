package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestCheck;
import com.jagent.desktop.services.PlatformCommands;
import java.awt.FlowLayout;
import java.util.Locale;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

/** Renders checks summary and optional per-check details for a pull request. */
public final class PullRequestChecksPanel extends JPanel {
    private transient PullRequest request;
    private boolean expanded;

    public PullRequestChecksPanel() {
        super();
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(UiFactory.contentAreaBorder());
    }

    public void render(final PullRequest request) {
        this.request = request;
        removeAll();
        if (request == null) {
            return;
        }
        add(summaryLine(request));
        if (expanded && !request.checks().isEmpty()) {
            add(Box.createVerticalStrut(UiConstants.SPACING_XS));
            for (final PullRequestCheck check : request.checks()) {
                add(checkLine(check, request.url()));
            }
        }
    }

    private JPanel summaryLine(final PullRequest request) {
        final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, UiConstants.SPACING_SM, 0));
        row.setOpaque(false);
        final JLabel summary =
                UiFactory.label(
                        UiText.titleCase(request.checksStatus())
                                + " "
                                + request.checksPassed()
                                + " / "
                                + request.checksTotal(),
                        Theme.FontSize.SM);
        summary.setForeground(UiText.checksColor(request.checksStatus()));
        row.add(summary);
        if (request.checks().isEmpty()) {
            final JLabel none = UiFactory.label("(no check details)", Theme.FontSize.SM);
            none.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
            row.add(none);
            return row;
        }
        final JButton toggle =
                UiFactory.link(expanded ? "Hide details" : "Show details", this::toggle);
        row.add(toggle);
        return row;
    }

    private JPanel checkLine(final PullRequestCheck check, final String fallbackUrl) {
        final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, UiConstants.SPACING_SM, 0));
        row.setOpaque(false);
        final String status = displayStatus(check);
        final JLabel statusLabel = UiFactory.label(status, Theme.FontSize.SM);
        statusLabel.setForeground(UiText.checksColor(status.toUpperCase(Locale.ROOT)));
        row.add(statusLabel);
        final String linkUrl = check.detailsUrl().isBlank() ? fallbackUrl : check.detailsUrl();
        final JButton link = UiFactory.link(check.name(), () -> PlatformCommands.openUrl(linkUrl));
        if (!check.details().isBlank()) {
            link.setToolTipText(check.details());
        }
        row.add(link);
        return row;
    }

    private void toggle() {
        expanded = !expanded;
        render(request);
        revalidate();
        repaint();
    }

    private static String displayStatus(final PullRequestCheck check) {
        if (!check.conclusion().isBlank()) {
            return UiText.titleCase(check.conclusion());
        }
        if (!check.status().isBlank()) {
            return UiText.titleCase(check.status());
        }
        return "Unknown";
    }
}
