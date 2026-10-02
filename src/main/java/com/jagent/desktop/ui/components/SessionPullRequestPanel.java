package com.jagent.desktop.ui.components;

import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.github.GitHub;
import com.jagent.desktop.ui.utils.ErrorMessages;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Pull-request link and status presentation for a session summary. */
public final class SessionPullRequestPanel extends JPanel {
    private static final String UNAVAILABLE = "Unavailable";

    private final JButton link = new LinkButton("Loading pull request status...", this::open);
    private final StatusDot statusDot = new StatusDot(Theme.Colors.muted());
    private final JLabel status = new JLabel("Status: Loading...");
    private final JLabel checks = new JLabel("Checks: Loading...");
    private final transient Session session;
    private final transient GitHub gitHub;
    private final Consumer<Boolean> closedChanged;
    private String url;

    public SessionPullRequestPanel(
            final Session session, final GitHub gitHub, final Consumer<Boolean> closedChanged) {
        super();
        this.session = session;
        this.gitHub = gitHub;
        this.closedChanged = closedChanged;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        final JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        header.setOpaque(false);
        header.add(statusDot);
        header.add(link);
        final JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        meta.setOpaque(false);
        status.setFont(Theme.font(Theme.FontSize.SM));
        checks.setFont(Theme.font(Theme.FontSize.SM));
        meta.add(status);
        meta.add(checks);
        add(header);
        add(meta);
    }

    public void load() {
        BackgroundOperations.submit(
                        "Session summary",
                        "session-pull-request-status",
                        () -> {
                            final String worktreePath = session.worktreePath();
                            if (worktreePath == null || worktreePath.isBlank()) {
                                throw new IOException("The session has no worktree path.");
                            }
                            final PullRequest request =
                                    gitHub.getPullRequest(Path.of(worktreePath));
                            return new PullRequestStatus(
                                    request, gitHub.getPullRequestDetails(request));
                        })
                .thenAccept(
                        result -> {
                            closedChanged.accept(
                                    result.request().state() == PullRequest.State.CLOSED);
                            showStatus(result.request(), result.details());
                        })
                .exceptionally(
                        failure -> {
                            final String message = ErrorMessages.deepestCause(failure, "");
                            if (message.toLowerCase(Locale.ROOT).contains("no pull request")) {
                                closedChanged.accept(false);
                                showNoPullRequest();
                            } else {
                                showUnavailable(message);
                            }
                            return null;
                        });
    }

    public void showStatus(final PullRequest request, final PullRequestDetails details) {
        url = request.url().toExternalForm();
        link.setText("#" + request.number() + " " + request.title());
        link.setToolTipText(url);
        status.setText("Status: " + UiText.titleCase(details.status().name()));
        checks.setText("Checks: " + PullRequestPresentation.checksPassed(details.checks()));
        statusDot.update(details.indicatorColor(), null);
        refresh();
    }

    public void showUnavailable(final String message) {
        link.setText(UNAVAILABLE + ": " + message);
        link.setToolTipText(null);
        status.setText("Status: Unavailable");
        checks.setText("Checks: Unavailable");
        statusDot.update(Theme.Colors.muted(), null);
        refresh();
    }

    public void showNoPullRequest() {
        url = null;
        link.setText("No pull request associated with this branch");
        link.setToolTipText(null);
        status.setText("Status: Unavailable");
        checks.setText("Checks: Unavailable");
        statusDot.update(Theme.Colors.muted(), null);
        refresh();
    }

    private void open() {
        if (url != null) {
            PlatformCommands.openUrl(url);
        }
    }

    private void refresh() {
        revalidate();
        repaint();
    }

    private record PullRequestStatus(PullRequest request, PullRequestDetails details) {}
}
