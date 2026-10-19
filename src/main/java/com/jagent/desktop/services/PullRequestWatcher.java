package com.jagent.desktop.services;

import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestChecks;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.services.github.GitHub;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Polls the pull requests associated with agent sessions and reports meaningful transitions. */
public final class PullRequestWatcher implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(PullRequestWatcher.class);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(30);

    private final Supplier<Map<SessionId, Session>> sessions;
    private final Function<Session, PullRequestStatus> loader;
    private final BiConsumer<Session, PullRequestStatus> statusListener;
    private final ScheduledExecutorService scheduler;
    private final Map<SessionId, PullRequestStatus> previous = new java.util.HashMap<>();

    public PullRequestWatcher(
            final AppState state, final BiConsumer<Session, PullRequestStatus> statusListener) {
        this(
                state::sessions,
                session -> load(state, session),
                statusListener,
                Executors.newSingleThreadScheduledExecutor());
    }

    /* package */ PullRequestWatcher(
            final Supplier<Map<SessionId, Session>> sessions,
            final Function<Session, PullRequestStatus> loader,
            final BiConsumer<Session, PullRequestStatus> statusListener,
            final ScheduledExecutorService scheduler) {
        this.sessions = Objects.requireNonNull(sessions);
        this.loader = Objects.requireNonNull(loader);
        this.statusListener = Objects.requireNonNull(statusListener);
        this.scheduler = Objects.requireNonNull(scheduler);
    }

    public void start() {
        scheduler.scheduleWithFixedDelay(
                this::pollSafely, 0, POLL_INTERVAL.toSeconds(), TimeUnit.SECONDS);
    }

    /* package */ void poll() {
        final Map<SessionId, Session> current = sessions.get();
        previous.keySet().removeIf(id -> !current.containsKey(id));
        current.forEach(this::pollSession);
    }

    private void pollSafely() {
        try {
            poll();
        } catch (RuntimeException exception) {
            LOG.warn("Pull request polling failed", exception);
        }
    }

    private void pollSession(final SessionId id, final Session session) {
        final String worktreePath = session.worktreePath();
        if (worktreePath == null || worktreePath.isBlank()) {
            return;
        }
        try {
            final PullRequestStatus current = loader.apply(session);
            final PullRequestStatus old = previous.put(id, current);
            if (old != null) {
                alertTransitions(old, current);
            }
            SwingUtilities.invokeLater(() -> statusListener.accept(session, current));
        } catch (RuntimeException exception) {
            LOG.debug("Could not poll pull request for session {}", session.name(), exception);
        }
    }

    private void alertTransitions(final PullRequestStatus old, final PullRequestStatus current) {
        if (old.details().status() != PullRequestDetails.Status.CONFLICTED
                && current.details().status() == PullRequestDetails.Status.CONFLICTED) {
            notify(current.request(), "Pull request is conflicted");
        }
        final PullRequestChecks.Status oldChecks = old.details().checks().checksStatus();
        final PullRequestChecks.Status newChecks = current.details().checks().checksStatus();
        if (oldChecks != newChecks && newChecks == PullRequestChecks.Status.FAILING) {
            notify(current.request(), "Pull request checks failed");
        } else if (oldChecks != newChecks && newChecks == PullRequestChecks.Status.PASSING) {
            notify(current.request(), "Pull request checks passed");
        }
        if (old.details().status() != PullRequestDetails.Status.READY
                && current.details().status() == PullRequestDetails.Status.READY) {
            notify(current.request(), "Pull request is ready to merge");
        }
    }

    private void notify(final PullRequest request, final String message) {
        PlatformCommands.notify("Branchloom PR #" + request.number(), message);
    }

    private static PullRequestStatus load(final AppState state, final Session session) {
        try {
            final GitHub github = GitHub.forProject(state, session.projectId());
            final PullRequest request = github.getPullRequest(Path.of(session.worktreePath()));
            return new PullRequestStatus(request, github.getPullRequestDetails(request));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Could not load session pull request", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load session pull request", exception);
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }

    public record PullRequestStatus(PullRequest request, PullRequestDetails details) {}
}
