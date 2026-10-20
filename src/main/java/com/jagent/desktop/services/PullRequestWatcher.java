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
    private final Function<Session, PullRequestDetails> loader;
    private final BiConsumer<SessionId, PullRequestDetails> statusListener;
    private final BackgroundJobs backgroundJobs;
    private final ScheduledExecutorService scheduler;
    private final Map<SessionId, PullRequestDetails> previous = new java.util.HashMap<>();

    public PullRequestWatcher(
            final AppState state,
            final BackgroundJobs backgroundJobs,
            final BiConsumer<SessionId, PullRequestDetails> statusListener) {
        this(
                state::sessions,
                session -> load(state, session),
                statusListener,
                backgroundJobs,
                Executors.newSingleThreadScheduledExecutor());
    }

    /* package */ PullRequestWatcher(
            final Supplier<Map<SessionId, Session>> sessions,
            final Function<Session, PullRequestDetails> loader,
            final BiConsumer<SessionId, PullRequestDetails> statusListener,
            final BackgroundJobs backgroundJobs,
            final ScheduledExecutorService scheduler) {
        this.sessions = Objects.requireNonNull(sessions);
        this.loader = Objects.requireNonNull(loader);
        this.statusListener = Objects.requireNonNull(statusListener);
        this.backgroundJobs = Objects.requireNonNull(backgroundJobs);
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
        final BackgroundJobs.Handle job = backgroundJobs.start("Pull request monitoring");
        try {
            pollWithJob(job);
            job.complete();
        } catch (RuntimeException exception) {
            job.fail(exception.getMessage() == null ? "Monitoring failed" : exception.getMessage());
            LOG.warn("Pull request polling failed", exception);
        }
    }

    private void pollWithJob(final BackgroundJobs.Handle job) {
        final Map<SessionId, Session> current = sessions.get();
        previous.keySet().removeIf(id -> !current.containsKey(id));
        final int[] failures = {0};
        current.forEach(
                (id, session) -> {
                    job.update("Checking session " + session.name() + "...");
                    if (!pollSession(id, session, job)) {
                        failures[0]++;
                    }
                });
        if (current.isEmpty()) {
            job.update("No sessions to check");
        } else if (failures[0] > 0) {
            throw new IllegalStateException(
                    failures[0] + " of " + current.size() + " session pull requests failed");
        }
    }

    private void pollSession(final SessionId id, final Session session) {
        pollSession(id, session, null);
    }

    private boolean pollSession(
            final SessionId id, final Session session, final BackgroundJobs.Handle job) {
        final String worktreePath = session.worktreePath();
        if (worktreePath == null || worktreePath.isBlank()) {
            return false;
        }
        try {
            final PullRequestDetails current = loader.apply(session);
            final PullRequestDetails old = previous.put(id, current);
            if (old != null) {
                alertTransitions(old, current);
            }
            SwingUtilities.invokeLater(() -> statusListener.accept(id, current));
            return true;
        } catch (RuntimeException exception) {
            if ("No pull request found".equals(exception.getMessage())) {
                previous.remove(id);
                SwingUtilities.invokeLater(() -> statusListener.accept(id, null));
                return true;
            }
            if (job != null) {
                job.output("Failed to check " + session.name() + ": " + exception.getMessage());
            }
            LOG.debug("Could not poll pull request for session {}", session.name(), exception);
            return false;
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

    private static PullRequestDetails load(final AppState state, final Session session) {
        try {
            final GitHub github = GitHub.forProject(state, session.projectId());
            final PullRequest request = github.getPullRequest(Path.of(session.worktreePath()));
            return github.getPullRequestDetails(request);
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
}
