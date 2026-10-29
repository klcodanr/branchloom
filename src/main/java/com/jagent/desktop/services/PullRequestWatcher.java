package com.jagent.desktop.services;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestChecks;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.services.github.GitHubProvider;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loads pull requests associated with agent sessions and reports meaningful transitions. */
@Singleton
public final class PullRequestWatcher {
    private static final Logger LOG = LoggerFactory.getLogger(PullRequestWatcher.class);

    private final Supplier<Map<SessionId, Session>> sessions;
    private final GitHubProvider githubProvider;
    private final BiConsumer<SessionId, PullRequestDetails> statusListener;
    private final Map<SessionId, PullRequestDetails> previous = new java.util.HashMap<>();

    @Inject
    public PullRequestWatcher(final AppState appState, final GitHubProvider githubProvider) {
        this.githubProvider = githubProvider;
        this.sessions = appState::sessions;
        this.statusListener =
                (sessionId, status) -> {
                    if (status == null) {
                        appState.removeMonitoringStatus(sessionId);
                    } else {
                        appState.updateMonitoringStatus(sessionId, status);
                    }
                };
    }

    public CompletableFuture<Void> refresh() {
        return BackgroundOperations.submit(
                "Pull request monitoring",
                "Refresh pull request status",
                () -> {
                    poll();
                    return null;
                });
    }

    /* package */ void poll() {
        final Map<SessionId, Session> current = sessions.get();
        previous.keySet().removeIf(id -> !current.containsKey(id));
        current.forEach(this::pollSession);
    }

    private void pollSession(final SessionId id, final Session session) {
        final String worktreePath = session.worktreePath();
        if (worktreePath == null || worktreePath.isBlank()) {
            return;
        }
        try {
            final PullRequestDetails current = load(session);
            final PullRequestDetails old = previous.put(id, current);
            if (old != null) {
                alertTransitions(old, current);
            }
            SwingUtilities.invokeLater(
                    () -> {
                        statusListener.accept(id, current);
                    });
        } catch (RuntimeException exception) {
            if ("No pull request found".equals(exception.getMessage())) {
                previous.remove(id);
                SwingUtilities.invokeLater(() -> statusListener.accept(id, null));
                return;
            }
            LOG.debug("Could not poll pull request for session {}", session.name(), exception);
        }
    }

    private void alertTransitions(final PullRequestDetails old, final PullRequestDetails current) {
        if (old.status() != PullRequestDetails.Status.CONFLICTED
                && current.status() == PullRequestDetails.Status.CONFLICTED) {
            notify(current, "Pull request is conflicted");
        }
        final PullRequestChecks.Status oldChecks = old.checks().checksStatus();
        final PullRequestChecks.Status newChecks = current.checks().checksStatus();
        if (oldChecks != newChecks && newChecks == PullRequestChecks.Status.FAILING) {
            notify(current, "Pull request checks failed");
        } else if (oldChecks != newChecks && newChecks == PullRequestChecks.Status.PASSING) {
            notify(current, "Pull request checks passed");
        }
        if (old.status() != PullRequestDetails.Status.READY
                && current.status() == PullRequestDetails.Status.READY) {
            notify(current, "Pull request is ready to merge");
        }
    }

    private void notify(final PullRequest request, final String message) {
        PlatformCommands.notify("Branchloom PR #" + request.number(), message);
    }

    private PullRequestDetails load(final Session session) {
        try {
            final var github = githubProvider.forProject(session.projectId());
            final PullRequest request =
                    github.getPullRequest(java.nio.file.Path.of(session.worktreePath()));
            return github.getPullRequestDetails(request);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Could not load session pull request", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load session pull request", exception);
        }
    }
}
