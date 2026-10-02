package com.jagent.desktop.ui.components;

import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.github.GitHub;
import com.jagent.desktop.ui.utils.ErrorMessages;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.JPanel;

/** Pull-request link and status presentation for a session summary. */
public final class SessionPullRequestPanel extends JPanel {
    private static final String UNAVAILABLE = "Unavailable";

    private final PullRequestSummaryButton summary =
            new PullRequestSummaryButton(PlatformCommands::openUrl);
    private final transient Session session;
    private final transient GitHub gitHub;
    private final Consumer<Boolean> closedChanged;

    public SessionPullRequestPanel(
            final Session session, final GitHub gitHub, final Consumer<Boolean> closedChanged) {
        super();
        this.session = session;
        this.gitHub = gitHub;
        this.closedChanged = closedChanged;
        setOpaque(false);
        add(summary);
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
        summary.render(request, details);
        refresh();
    }

    public void showUnavailable(final String message) {
        summary.renderUnavailable(UNAVAILABLE + ": " + message, false);
        refresh();
    }

    public void showNoPullRequest() {
        summary.renderUnavailable("No pull request associated with this branch", true);
        refresh();
    }

    private void refresh() {
        revalidate();
        repaint();
    }

    private record PullRequestStatus(PullRequest request, PullRequestDetails details) {}
}
