package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.GitHubUser;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestCheck;
import com.jagent.desktop.models.PullRequestChecks;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.PullRequestReviews;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.services.github.GitHub;
import com.jagent.desktop.test.TestAppState;
import com.jagent.desktop.ui.Defaults;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class PullRequestWatcherTest {
    @Test
    void notifiesForConflictCheckCompletionAndReadyTransitions()
            throws MalformedURLException,
                    java.lang.reflect.InvocationTargetException,
                    InterruptedException {
        final ProjectId projectId = ProjectId.create();
        final SessionId sessionId = SessionId.create();
        final Session session = new Session(projectId, "agent", "agent", "prompt", "/tmp/worktree");
        final PullRequest request = request(projectId);
        final Queue<PullRequestDetails> statuses =
                new ArrayDeque<>(
                        List.of(
                                status(request, false, "clean", PullRequestChecks.Status.PENDING),
                                status(request, false, "dirty", PullRequestChecks.Status.PENDING),
                                status(request, false, "dirty", PullRequestChecks.Status.FAILING),
                                status(request, true, "clean", PullRequestChecks.Status.PASSING)));
        final Project project = request.project();
        final var state =
                new AppState(
                        Defaults.appSettings(),
                        Map.of(projectId.value().toString(), project),
                        Map.of(sessionId.value().toString(), session),
                        Map.of());
        final GitHub github =
                new GitHub(state, projectId) {
                    @Override
                    public PullRequest getPullRequest(final java.nio.file.Path worktree)
                            throws IOException {
                        return request;
                    }

                    @Override
                    public PullRequestDetails getPullRequestDetails(final PullRequest ignored) {
                        return statuses.remove();
                    }
                };
        final var context =
                new ActionContext(
                        TestAppState.controller(state), state, null, null, null, ignored -> github);
        final PullRequestWatcher watcher = new PullRequestWatcher(context);
        watcher.poll();
        watcher.poll();
        watcher.poll();
        watcher.poll();
        SwingUtilities.invokeAndWait(() -> {});

        assertEquals(
                PullRequestDetails.Status.READY,
                state.monitoringStatuses().get(sessionId).status(),
                "each poll should update the PR status");
    }

    @Test
    void ignoresSessionsWithoutWorktreesAndRemovesDeletedSessions()
            throws java.io.InvalidObjectException,
                    MalformedURLException,
                    java.lang.reflect.InvocationTargetException,
                    InterruptedException {
        final ProjectId projectId = ProjectId.create();
        final Project project = new Project("Project", "/tmp/project", null);
        final var state =
                new AppState(
                        Defaults.appSettings(),
                        Map.of(projectId.value().toString(), project),
                        Map.of(),
                        Map.of());
        final SessionId noWorktreeId =
                state.addSession(projectId, new Session(projectId, "idle", null, null, null));
        final SessionId worktreeId =
                state.addSession(
                        projectId, new Session(projectId, "active", null, null, "/tmp/worktree"));
        final PullRequest request = request(projectId);
        final GitHub github =
                new GitHub(state, projectId) {
                    @Override
                    public PullRequest getPullRequest(final java.nio.file.Path worktree) {
                        return request;
                    }

                    @Override
                    public PullRequestDetails getPullRequestDetails(final PullRequest ignored) {
                        return status(request, false, "clean", PullRequestChecks.Status.PENDING);
                    }
                };
        final var context =
                new ActionContext(
                        TestAppState.controller(state), state, null, null, null, ignored -> github);
        final PullRequestWatcher watcher = new PullRequestWatcher(context);

        watcher.poll();
        SwingUtilities.invokeAndWait(() -> {});
        assertEquals(
                "",
                state.monitoringStatuses().get(noWorktreeId) == null ? "" : "present",
                "sessions without worktrees should not be monitored");
        assertEquals(
                PullRequestDetails.Status.PENDING,
                state.monitoringStatuses().get(worktreeId).status(),
                "sessions with worktrees should be monitored");

        state.removeSession(worktreeId);
        watcher.poll();
        assertFalse(
                state.monitoringStatuses().containsKey(worktreeId),
                "deleted sessions should no longer have monitoring status");
    }

    @Test
    void handlesMissingPullRequestsAndLoadFailures()
            throws java.io.InvalidObjectException,
                    java.lang.reflect.InvocationTargetException,
                    InterruptedException {
        final ProjectId projectId = ProjectId.create();
        final Project project = new Project("Project", "/tmp/project", null);
        final Session session = new Session(projectId, "active", null, null, "/tmp/worktree");
        final var state =
                new AppState(
                        Defaults.appSettings(),
                        Map.of(projectId.value().toString(), project),
                        Map.of(),
                        Map.of());
        final SessionId sessionId = state.addSession(projectId, session);
        final GitHub github =
                new GitHub(state, projectId) {
                    @Override
                    public PullRequest getPullRequest(final java.nio.file.Path worktree) {
                        throw new IllegalStateException("No pull request found");
                    }
                };
        final var context =
                new ActionContext(
                        TestAppState.controller(state), state, null, null, null, ignored -> github);
        final PullRequestWatcher watcher = new PullRequestWatcher(context);

        watcher.poll();
        SwingUtilities.invokeAndWait(() -> {});
        assertFalse(
                state.monitoringStatuses().containsKey(sessionId),
                "missing pull requests should clear monitoring status");

        final GitHub failingGithub =
                new GitHub(state, projectId) {
                    @Override
                    public PullRequest getPullRequest(final java.nio.file.Path worktree)
                            throws IOException {
                        throw new IOException("GitHub unavailable");
                    }
                };
        final PullRequestWatcher failingWatcher =
                new PullRequestWatcher(
                        new ActionContext(
                                TestAppState.controller(state),
                                state,
                                null,
                                null,
                                null,
                                ignored -> failingGithub));
        failingWatcher.poll();
        assertFalse(
                state.monitoringStatuses().containsKey(sessionId),
                "load failures should not publish monitoring status");
    }

    private static PullRequest request(final ProjectId projectId) throws MalformedURLException {
        final Project project = new Project("Project", "/tmp/project", null);
        return new PullRequest(
                projectId,
                project,
                12,
                PullRequest.State.OPEN,
                "Title",
                "Description",
                new URL("https://github.com/example/project/pull/12"),
                new Date(),
                new Date(),
                new GitHubUser("user", new URL("https://github.com/user")),
                "feature",
                "sha",
                "main");
    }

    private static PullRequestDetails status(
            final PullRequest request,
            final boolean mergeable,
            final String mergeableState,
            final PullRequestChecks.Status checksStatus) {
        return new PullRequestDetails(
                request,
                false,
                false,
                mergeable,
                mergeableState,
                1,
                1,
                1,
                checks(checksStatus),
                new PullRequestReviews(List.of()));
    }

    private static PullRequestChecks checks(final PullRequestChecks.Status status) {
        return switch (status) {
            case FAILING ->
                    new PullRequestChecks(
                            List.of(
                                    new PullRequestCheck(
                                            "check",
                                            PullRequestCheck.Status.COMPLETED,
                                            null,
                                            PullRequestCheck.Conclusion.FAILURE,
                                            null)));
            case PENDING ->
                    new PullRequestChecks(
                            List.of(
                                    new PullRequestCheck(
                                            "check",
                                            PullRequestCheck.Status.IN_PROGRESS,
                                            null,
                                            PullRequestCheck.Conclusion.UNKNOWN,
                                            null)));
            case PASSING ->
                    new PullRequestChecks(
                            List.of(
                                    new PullRequestCheck(
                                            "check",
                                            PullRequestCheck.Status.COMPLETED,
                                            null,
                                            PullRequestCheck.Conclusion.SUCCESS,
                                            null)));
            case UNKNOWN -> new PullRequestChecks(List.of());
        };
    }
}
