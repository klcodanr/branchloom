package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jagent.desktop.models.GitHubUser;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestCheck;
import com.jagent.desktop.models.PullRequestChecks;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executors;
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
        final Queue<PullRequestWatcher.PullRequestStatus> statuses =
                new ArrayDeque<>(
                        List.of(
                                status(request, false, "clean", PullRequestChecks.Status.PENDING),
                                status(request, false, "dirty", PullRequestChecks.Status.PENDING),
                                status(request, false, "dirty", PullRequestChecks.Status.FAILING),
                                status(request, true, "clean", PullRequestChecks.Status.PASSING)));
        final List<PullRequestDetails.Status> rendered = new ArrayList<>();
        final Map<SessionId, Session> sessions = Map.of(sessionId, session);
        try (PullRequestWatcher watcher =
                new PullRequestWatcher(
                        () -> sessions,
                        ignored -> statuses.remove(),
                        (ignored, status) -> rendered.add(status.details().status()),
                        Executors.newSingleThreadScheduledExecutor())) {
            watcher.poll();
            watcher.poll();
            watcher.poll();
            watcher.poll();
            SwingUtilities.invokeAndWait(() -> {});
        }

        assertEquals(4, rendered.size(), "each poll should update the PR status");
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

    private static PullRequestWatcher.PullRequestStatus status(
            final PullRequest request,
            final boolean mergeable,
            final String mergeableState,
            final PullRequestChecks.Status checksStatus) {
        return new PullRequestWatcher.PullRequestStatus(
                request,
                new PullRequestDetails(
                        request, false, mergeable, mergeableState, 1, 1, 1, checks(checksStatus)));
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
