package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.test.TestAppState;
import com.jagent.desktop.test.TestGitRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionCreationServiceTest {
    private static final String PROJECT_NAME = "Demo";
    private static final String AGENT_NAME = "Test";
    private static final String AGENT_COMMAND = "agent";
    private static final String SESSION_NAME = "Fix login";
    private static final String PROMPT = "prompt";
    private static final String WORKTREE_TEMPLATE = "worktrees/{sessionSlug}";

    @Test
    void createsConfiguredWorktreeSessionAndTerminal(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        final AppState state = TestAppState.empty();
        final Project project =
                new Project(
                        PROJECT_NAME,
                        directory.toString(),
                        null,
                        null,
                        null,
                        WORKTREE_TEMPLATE,
                        null,
                        java.util.List.of(),
                        java.util.List.of());
        final ProjectId projectId = state.addProject(project);
        final Agent agent = new Agent(AGENT_NAME, AGENT_COMMAND + " --prompt {prompt}");

        final SessionCreationService.CreatedSession created =
                new SessionCreationService(state, new Git())
                        .create(projectId, project, agent, SESSION_NAME, "Investigate login", null);

        final Path worktree = Path.of(created.worktreePath());
        assertEquals(
                directory.resolve("worktrees/fix-login").normalize(),
                worktree,
                "created worktree path should use the configured template");
        assertEquals(
                "fix-login\n",
                TestGitRepository.output(worktree, "git branch --show-current"),
                "created worktree should be on the new branch");
        assertTrue(Files.isDirectory(worktree), "created worktree should exist");
        assertEquals(
                created.session().worktreePath(),
                state.sessions().get(created.sessionId()).worktreePath(),
                "state should retain the created worktree path");
        assertEquals(
                created.session().name(),
                state.sessions().get(created.sessionId()).name(),
                "state should retain the created session name");
        assertEquals(
                created.terminalId(),
                state.sessions().get(created.sessionId()).terminalIds().getFirst(),
                "state should retain the created terminal");
        assertTrue(
                state.terminals().get(created.terminalId()).command().contains("Investigate login"),
                "terminal command should include the session prompt");
        assertFalse(
                state.projects().get(projectId).sessionIds().isEmpty(),
                "project should reference the created session");
    }

    @Test
    void rejectsExistingBranchBeforeCreatingWorktree(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        TestGitRepository.run(directory, "git branch fix-login");
        final AppState state = TestAppState.empty();
        final Project project =
                new Project(
                        PROJECT_NAME,
                        directory.toString(),
                        null,
                        null,
                        null,
                        WORKTREE_TEMPLATE,
                        null,
                        java.util.List.of(),
                        java.util.List.of());
        final ProjectId projectId = state.addProject(project);

        final IOException exception =
                assertThrows(
                        IOException.class,
                        () ->
                                new SessionCreationService(state, new Git())
                                        .create(
                                                projectId,
                                                project,
                                                new Agent(AGENT_NAME, AGENT_COMMAND),
                                                SESSION_NAME,
                                                PROMPT,
                                                null));

        assertTrue(
                exception.getMessage().contains("fix-login"),
                "error should identify the conflicting branch");
        assertTrue(state.sessions().isEmpty(), "failed creation should not add a session");
    }

    @Test
    void createsWorktreeFromExplicitBaseBranch(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        final AppState state = TestAppState.empty();
        final Project project =
                new Project(
                        PROJECT_NAME,
                        directory.toString(),
                        null,
                        null,
                        null,
                        WORKTREE_TEMPLATE,
                        null,
                        java.util.List.of(),
                        java.util.List.of());
        final ProjectId projectId = state.addProject(project);

        final SessionCreationService.CreatedSession created =
                new SessionCreationService(state, new Git())
                        .create(
                                projectId,
                                project,
                                new Agent(AGENT_NAME, AGENT_COMMAND),
                                "From main",
                                PROMPT,
                                "HEAD");

        assertTrue(Files.isDirectory(Path.of(created.worktreePath())), "worktree should exist");
        assertEquals(
                "from-main\n",
                TestGitRepository.output(
                        Path.of(created.worktreePath()), "git branch --show-current"),
                "explicit base branch should create the requested branch");
    }

    @Test
    void rejectsAnExistingWorktreePath(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        final AppState state = TestAppState.empty();
        final Project project =
                new Project(
                        PROJECT_NAME,
                        directory.toString(),
                        null,
                        null,
                        null,
                        WORKTREE_TEMPLATE,
                        null,
                        java.util.List.of(),
                        java.util.List.of());
        final ProjectId projectId = state.addProject(project);
        Files.createDirectories(directory.resolve("worktrees/fix-login"));

        final IOException exception =
                assertThrows(
                        IOException.class,
                        () ->
                                new SessionCreationService(state, new Git())
                                        .create(
                                                projectId,
                                                project,
                                                new Agent(AGENT_NAME, AGENT_COMMAND),
                                                SESSION_NAME,
                                                PROMPT,
                                                null));

        assertTrue(
                exception.getMessage().contains("already in use"),
                "error should identify the conflicting worktree path");
    }

    @Test
    void rejectsAWorktreePathAlreadyRegisteredInState(@TempDir final Path directory)
            throws IOException, InterruptedException, java.io.InvalidObjectException {
        TestGitRepository.initialize(directory);
        final AppState state = TestAppState.empty();
        final Project project =
                new Project(
                        PROJECT_NAME,
                        directory.toString(),
                        null,
                        null,
                        null,
                        WORKTREE_TEMPLATE,
                        null,
                        java.util.List.of(),
                        java.util.List.of());
        final ProjectId projectId = state.addProject(project);
        state.addSession(
                projectId,
                new com.jagent.desktop.models.Session(
                        projectId,
                        "Existing",
                        AGENT_NAME,
                        PROMPT,
                        directory.resolve("worktrees/fix-login").toString()));

        final IOException exception =
                assertThrows(
                        IOException.class,
                        () ->
                                new SessionCreationService(state, new Git())
                                        .create(
                                                projectId,
                                                project,
                                                new Agent(AGENT_NAME, AGENT_COMMAND),
                                                SESSION_NAME,
                                                PROMPT,
                                                null));

        assertTrue(
                exception.getMessage().contains("already in use"),
                "error should identify the registered worktree path");
    }

    @Test
    void reportsGitCreationFailuresAsIoExceptions(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        final AppState state = TestAppState.empty();
        final Project project = new Project(PROJECT_NAME, directory.toString(), null);
        final ProjectId projectId = state.addProject(project);

        final IOException exception =
                assertThrows(
                        IOException.class,
                        () ->
                                new SessionCreationService(state, new Git())
                                        .create(
                                                projectId,
                                                project,
                                                new Agent(AGENT_NAME, AGENT_COMMAND),
                                                SESSION_NAME,
                                                PROMPT,
                                                "missing-base-branch"));

        assertTrue(
                exception.getCause() != null || exception.getMessage() != null,
                "Git failures should retain an explanatory cause or message");
    }
}
