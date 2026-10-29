package com.jagent.desktop.ui.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundJobs;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.test.AsyncTestSupport;
import com.jagent.desktop.test.TestAppState;
import com.jagent.desktop.test.TestGitRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RemoveSessionActionTest {
    private static final String PROJECT_NAME = "Demo";
    @TempDir private Path tempDirectory;

    @Test
    void removesWorktreeInBackground() throws IOException, InterruptedException {
        final Path repository = tempDirectory.resolve("repository");
        final Path worktree = tempDirectory.resolve("worktree");
        Files.createDirectories(repository);
        TestGitRepository.initialize(repository);
        TestGitRepository.run(
                repository,
                "git worktree add -qb feature " + PlatformCommands.shellQuote(worktree.toString()));
        final Path realRepository = repository.toRealPath();
        final Path realWorktree = worktree.toRealPath();
        final AppState state = TestAppState.empty();
        final var projectId =
                state.addProject(new Project(PROJECT_NAME, realRepository.toString(), null));
        final var sessionId =
                state.addSession(
                        projectId,
                        new Session(projectId, "Feature", null, null, realWorktree.toString()));
        final var coordinator = TestAppState.controller(state);
        coordinator.selectSession(projectId, sessionId);

        new ActionDouble(new ActionContext(coordinator, state, null, null, null)).execute();
        awaitFinished(coordinator);
        assertEquals(
                BackgroundJobs.Status.SUCCEEDED,
                coordinator.backgroundJobs().jobs().getFirst().status(),
                "removal should succeed");
        assertTrue(state.sessions().isEmpty(), "removed session should not remain");
        assertTrue(Files.notExists(worktree), "worktree should be deleted");
        try (GitRepository git = GitRepository.open(realRepository)) {
            assertTrue(
                    git.listWorktrees().stream()
                            .noneMatch(candidate -> candidate.path().equals(realWorktree)),
                    "Git should unregister the worktree");
        }
    }

    @Test
    void cancellationLeavesDirtyWorktree() throws IOException, InterruptedException {
        final Path repository = tempDirectory.resolve("repository");
        final Path worktree = tempDirectory.resolve("worktree");
        Files.createDirectories(repository);
        TestGitRepository.initialize(repository);
        TestGitRepository.run(
                repository,
                "git worktree add -qb feature " + PlatformCommands.shellQuote(worktree.toString()));
        Files.writeString(worktree.resolve("uncommitted.txt"), "changes");
        final AppState state = TestAppState.empty();
        final var projectId =
                state.addProject(
                        new Project(PROJECT_NAME, repository.toRealPath().toString(), null));
        final var sessionId =
                state.addSession(
                        projectId,
                        new Session(
                                projectId,
                                "Feature",
                                null,
                                null,
                                worktree.toRealPath().toString()));
        final var coordinator = TestAppState.controller(state);
        coordinator.selectSession(projectId, sessionId);

        new ActionDouble(new ActionContext(coordinator, state, null, null, null), false).execute();
        awaitFinished(coordinator);
        assertTrue(state.sessions().containsKey(sessionId), "cancelled session should remain");
        assertTrue(Files.exists(worktree), "cancelled worktree should remain");
    }

    @Test
    void removingUnselectedSessionDoesNotNavigate() throws IOException {
        final AppState state = TestAppState.empty();
        final var projectId =
                state.addProject(new Project(PROJECT_NAME, tempDirectory.toString(), null));
        final var removed =
                state.addSession(projectId, new Session(projectId, "Removed", null, null, null));
        final var selected =
                state.addSession(projectId, new Session(projectId, "Selected", null, null, null));
        final var coordinator = TestAppState.controller(state);
        coordinator.updateView(
                ViewId.SESSION, ViewController.ViewState.session(projectId, selected));
        new ActionDouble(new ActionContext(coordinator, state, null, null, null))
                .removeSessionForTest(removed, projectId);
        assertEquals(
                ViewId.SESSION,
                coordinator.currentViewId(),
                "view should remain on selected session");
        assertEquals(
                selected,
                coordinator.currentSessionId(),
                "selected session should remain selected");
    }

    @Test
    void missingWorktreeReportsFailure() throws IOException, InterruptedException {
        final AppState state = TestAppState.empty();
        final Path repository = tempDirectory.resolve("repository");
        Files.createDirectories(repository);
        final var projectId =
                state.addProject(new Project(PROJECT_NAME, repository.toString(), null));
        final var sessionId =
                state.addSession(
                        projectId,
                        new Session(
                                projectId,
                                "Feature",
                                null,
                                null,
                                tempDirectory.resolve("missing-worktree").toString()));
        final var coordinator = TestAppState.controller(state);
        coordinator.selectSession(projectId, sessionId);
        new ActionDouble(new ActionContext(coordinator, state, null, null, null)).execute();
        AsyncTestSupport.await(
                () ->
                        coordinator.backgroundJobs().jobs().stream()
                                .anyMatch(job -> job.status() == BackgroundJobs.Status.FAILED),
                "invalid worktree should fail the background job");
        assertTrue(
                state.sessions().containsKey(sessionId),
                "failed removal should preserve the session");
    }

    private static void awaitFinished(final ViewController coordinator)
            throws InterruptedException {
        AsyncTestSupport.await(
                () ->
                        !coordinator.backgroundJobs().jobs().isEmpty()
                                && coordinator.backgroundJobs().jobs().getFirst().status()
                                        != BackgroundJobs.Status.RUNNING,
                "worktree operation should complete");
    }

    private static final class ActionDouble extends RemoveSessionAction {
        private final boolean confirmDeletion;

        private ActionDouble(final ActionContext context) {
            this(context, true);
        }

        private ActionDouble(final ActionContext context, final boolean confirmDeletion) {
            super(context);
            this.confirmDeletion = confirmDeletion;
        }

        @Override
        protected int removalChoice(final Session session) {
            return 2;
        }

        @Override
        protected boolean confirmWorktreeDeletion(final Session session) {
            return confirmDeletion;
        }

        @Override
        protected void failRemoval(final BackgroundJobs.Handle job, final Throwable failure) {
            job.fail(failure.getMessage());
        }

        private void removeSessionForTest(
                final SessionId sessionId, final com.jagent.desktop.models.ProjectId projectId) {
            removeSession(sessionId, projectId);
        }
    }
}
