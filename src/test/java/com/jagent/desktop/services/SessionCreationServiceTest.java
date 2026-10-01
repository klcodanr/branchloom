package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.SessionId;
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
    private static final String WORKTREE_DIRECTORY = "worktree";

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
                        WORKTREE_TEMPLATE,
                        null,
                        java.util.List.of(),
                        java.util.List.of(),
                        null,
                        null);
        final ProjectId projectId = state.addProject(project);
        final Agent agent = new Agent(AGENT_NAME, AGENT_COMMAND + " --prompt {prompt}");

        final SessionCreationService.CreatedSession created =
                new SessionCreationService(state)
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
    void doesNotCreateSessionWhenAgentContextCannotBeWritten(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        final Path contextParent = directory.resolve(".cursor-notes");
        Files.writeString(contextParent, "existing file");
        final AppState state = TestAppState.empty();
        final Project project =
                new Project(
                        PROJECT_NAME,
                        directory.toString(),
                        null,
                        null,
                        WORKTREE_TEMPLATE,
                        null,
                        java.util.List.of(),
                        java.util.List.of(),
                        contextParent.resolve("BRANCHLOOM_CONTEXT.md").toString(),
                        null);
        final ProjectId projectId = state.addProject(project);
        final Agent agent = new Agent(AGENT_NAME, AGENT_COMMAND + " --prompt {prompt}");

        assertThrows(
                IOException.class,
                () ->
                        new SessionCreationService(state)
                                .create(projectId, project, agent, SESSION_NAME, PROMPT, null),
                "session creation should fail when agent context cannot be written");
        assertTrue(
                state.sessions().isEmpty(), "failed context creation must not register a session");
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
                        WORKTREE_TEMPLATE,
                        null,
                        java.util.List.of(),
                        java.util.List.of(),
                        null,
                        null);
        final ProjectId projectId = state.addProject(project);

        final IOException exception =
                assertThrows(
                        IOException.class,
                        () ->
                                new SessionCreationService(state)
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
                        WORKTREE_TEMPLATE,
                        null,
                        java.util.List.of(),
                        java.util.List.of(),
                        null,
                        null);
        final ProjectId projectId = state.addProject(project);

        final SessionCreationService.CreatedSession created =
                new SessionCreationService(state)
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
                        WORKTREE_TEMPLATE,
                        null,
                        java.util.List.of(),
                        java.util.List.of(),
                        null,
                        null);
        final ProjectId projectId = state.addProject(project);
        Files.createDirectories(directory.resolve("worktrees/fix-login"));

        final IOException exception =
                assertThrows(
                        IOException.class,
                        () ->
                                new SessionCreationService(state)
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
                        WORKTREE_TEMPLATE,
                        null,
                        java.util.List.of(),
                        java.util.List.of(),
                        null,
                        null);
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
                                new SessionCreationService(state)
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
                                new SessionCreationService(state)
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

    @Test
    void checkCreateSessionRejectsMissingWorktreeDirectory(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        final AppState state = TestAppState.empty();
        final Project project = new Project(PROJECT_NAME, directory.toString(), null);

        final IOException exception =
                assertThrows(
                        IOException.class,
                        () ->
                                new SessionCreationService(state)
                                        .checkCreateSession(
                                                project,
                                                SESSION_NAME,
                                                directory.resolve("missing-worktree")));

        assertTrue(
                exception.getMessage().contains("does not exist"),
                "missing directory should be rejected");
    }

    @Test
    void createSessionRegistersSessionUsingNormalizedPath(@TempDir final Path directory)
            throws IOException, InterruptedException, java.io.InvalidObjectException {
        TestGitRepository.initialize(directory);
        final AppState state = TestAppState.empty();
        final Project project = new Project(PROJECT_NAME, directory.toString(), null);
        final ProjectId projectId = state.addProject(project);
        final SessionCreationService service = new SessionCreationService(state);

        final SessionId sessionId =
                service.createSession(
                        projectId,
                        SESSION_NAME,
                        AGENT_NAME,
                        PROMPT,
                        directory.resolve(".").resolve(WORKTREE_DIRECTORY));

        assertEquals(
                directory.resolve(WORKTREE_DIRECTORY).toAbsolutePath().normalize().toString(),
                state.sessions().get(sessionId).worktreePath(),
                "session should be registered with a normalized absolute path");
    }

    @Test
    void checkCreateWorktreeAndSessionRejectsBlankLocalBranch(@TempDir final Path directory)
            throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        final AppState state = TestAppState.empty();
        final Project project = new Project(PROJECT_NAME, directory.toString(), null);
        final SessionCreationService service = new SessionCreationService(state);

        final IOException exception =
                assertThrows(
                        IOException.class,
                        () ->
                                service.checkCreateWorktreeAndSession(
                                        project,
                                        new SessionCreationService.SessionDetails(
                                                SESSION_NAME, AGENT_NAME, PROMPT),
                                        new SessionCreationService.WorktreeRequest(
                                                directory.resolve(WORKTREE_DIRECTORY),
                                                "HEAD",
                                                " ")));

        assertTrue(
                exception.getMessage().contains("cannot be blank"),
                "blank local branch should be rejected");
    }

    @Test
    void checkFetchBranchCreateWorktreeAndSessionRejectsInvalidRemoteRef(
            @TempDir final Path directory) throws IOException, InterruptedException {
        TestGitRepository.initialize(directory);
        final AppState state = TestAppState.empty();
        final Project project = new Project(PROJECT_NAME, directory.toString(), null);
        final SessionCreationService service = new SessionCreationService(state);

        final IOException exception =
                assertThrows(
                        IOException.class,
                        () ->
                                service.checkFetchBranchCreateWorktreeAndSession(
                                        project,
                                        new SessionCreationService.SessionDetails(
                                                SESSION_NAME, AGENT_NAME, PROMPT),
                                        new SessionCreationService.WorktreeRequest(
                                                directory.resolve(WORKTREE_DIRECTORY),
                                                "feature",
                                                null)));

        assertTrue(
                exception.getMessage().contains("Invalid remote branch reference"),
                "source ref without remote prefix should be rejected");
    }

    @Test
    void createWorktreeAndSessionCreatesWorktreeAndRegistersSession(@TempDir final Path directory)
            throws IOException, InterruptedException, java.io.InvalidObjectException {
        TestGitRepository.initialize(directory);
        final AppState state = TestAppState.empty();
        final Project project = new Project(PROJECT_NAME, directory.toString(), null);
        final ProjectId projectId = state.addProject(project);
        final SessionCreationService service = new SessionCreationService(state);
        final Path worktree = directory.resolve("imported-worktree");
        final SessionCreationService.SessionDetails details =
                new SessionCreationService.SessionDetails(SESSION_NAME, AGENT_NAME, PROMPT);
        final SessionCreationService.WorktreeRequest request =
                new SessionCreationService.WorktreeRequest(worktree, "HEAD", "fix-login-import");

        service.checkCreateWorktreeAndSession(project, details, request);
        final SessionId sessionId =
                service.createWorktreeAndSession(projectId, project, details, request);

        assertTrue(Files.isDirectory(worktree), "worktree should be created on disk");
        assertEquals(
                "fix-login-import",
                TestGitRepository.output(worktree, "git branch --show-current").trim(),
                "imported worktree should use the requested local branch name");
        assertEquals(
                worktree.toAbsolutePath().normalize().toString(),
                state.sessions().get(sessionId).worktreePath(),
                "session should point to the created worktree path");
    }

    @Test
    void fetchesRemoteBranchBeforeCreatingWorktree(@TempDir final Path directory)
            throws IOException, InterruptedException, java.io.InvalidObjectException {
        TestGitRepository.initialize(directory);
        TestGitRepository.run(directory, "git clone -q --bare . remote.git");
        TestGitRepository.run(directory, "git remote add origin remote.git");
        TestGitRepository.run(
                directory,
                "git clone -q remote.git remote-update && "
                        + "git -C remote-update config user.name test && "
                        + "git -C remote-update config user.email test && "
                        + "printf 'updated' > remote-update/tracked.txt && "
                        + "git -C remote-update add tracked.txt && "
                        + "git -C remote-update commit -qm updated && "
                        + "git -C remote-update push -q origin master");

        final AppState state = TestAppState.empty();
        final Project project = new Project(PROJECT_NAME, directory.toString(), null);
        final ProjectId projectId = state.addProject(project);
        final Path worktree = directory.resolve("imported-remote-worktree");
        final SessionCreationService.SessionDetails details =
                new SessionCreationService.SessionDetails(SESSION_NAME, AGENT_NAME, PROMPT);
        final SessionCreationService.WorktreeRequest request =
                new SessionCreationService.WorktreeRequest(
                        worktree, "origin/master", "imported-remote");

        final SessionId sessionId =
                new SessionCreationService(state)
                        .fetchBranchCreateWorktreeAndSession(projectId, project, details, request);

        assertEquals(
                "updated",
                Files.readString(worktree.resolve("tracked.txt")),
                "worktree should be created from the fetched remote branch");
        assertEquals(
                worktree.toAbsolutePath().normalize().toString(),
                state.sessions().get(sessionId).worktreePath(),
                "session should point to the fetched remote worktree");
    }
}
