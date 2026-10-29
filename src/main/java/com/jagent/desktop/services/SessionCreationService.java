package com.jagent.desktop.services;

import com.google.inject.Inject;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.models.git.Branch;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.services.github.GitHub;
import com.jagent.desktop.ui.utils.GitUtils;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates a session, its worktree, and its initial terminal. */
public final class SessionCreationService {
    private static final Logger LOG = LoggerFactory.getLogger(SessionCreationService.class);
    private final AppState state;

    @Inject
    public SessionCreationService(final AppState state) {
        this.state = state;
    }

    public CreatedSession create(
            final ProjectId projectId,
            final Project project,
            final Agent agent,
            final String name,
            final String prompt,
            final String baseBranch)
            throws IOException {
        try (GitRepository git = GitRepository.open(Path.of(project.path()))) {
            final String branch = GitUtils.toBranchSlug(name);

            requireSessionNameAvailable(project, name);
            final Session draft = Session.create(projectId, name, agent.name, prompt, "");

            final var expectedPath =
                    Template.resolvePath(
                            Template.expand(
                                    Template.worktree(project, state.appSettings()),
                                    project,
                                    draft,
                                    false),
                            project);
            final Path sessionPath = requireWorktreePathUnregistered(Path.of(expectedPath));

            if (Files.exists(sessionPath)) {
                throw new IOException("The worktree path is already in use:\n" + expectedPath);
            }
            if (git.branchExists(branch)) {
                throw new IOException("A branch named '" + branch + "' already exists.");
            }
            String base = baseBranch;
            if (base == null) {
                base = git.currentBranch();
            }
            final Branch newBranch = git.createBranch(new Branch(base), branch);
            git.addWorktree(sessionPath, newBranch);

            final var sessionId = registerSession(projectId, name, agent.name, prompt, sessionPath);
            return registerSessionTerminal(sessionId, agent, prompt);
        }
    }

    public SessionId createSession(
            final ProjectId projectId,
            final Project project,
            final String sessionName,
            final Path worktreePath)
            throws IOException, InvalidObjectException {
        requireSessionNameAvailable(project, sessionName);
        final Path normalizedPath = requireWorktreePathUnregistered(worktreePath);
        if (!Files.isDirectory(normalizedPath)) {
            throw new IOException("The worktree directory does not exist:\n" + normalizedPath);
        }
        return registerSession(
                projectId, sessionName, "", "Imported", worktreePath.toAbsolutePath().normalize());
    }

    public SessionId createWorktreeAndSession(
            final ProjectId projectId,
            final Project project,
            final SessionDetails sessionDetails,
            final WorktreeRequest worktreeRequest)
            throws IOException, InvalidObjectException {
        checkCreateSessionBranch(
                project,
                sessionDetails.sessionName(),
                worktreeRequest.worktreePath(),
                worktreeRequest.sourceRef());
        if (worktreeRequest.localBranch() != null && worktreeRequest.localBranch().isBlank()) {
            throw new IOException("Branch name cannot be blank.");
        }
        final Path normalizedPath = worktreeRequest.worktreePath().toAbsolutePath().normalize();
        addWorktree(
                project,
                normalizedPath,
                worktreeRequest.sourceRef(),
                worktreeRequest.localBranch());
        return registerSession(
                projectId,
                sessionDetails.sessionName(),
                sessionDetails.agent(),
                sessionDetails.prompt(),
                normalizedPath);
    }

    private void checkCreateSessionBranch(
            final Project project,
            final String sessionName,
            final Path worktreePath,
            final String sourceRef)
            throws IOException, InvalidObjectException {
        requireSessionNameAvailable(project, sessionName);
        if (sourceRef == null || sourceRef.isBlank()) {
            throw new IOException("Branch reference cannot be blank.");
        }
        final Path normalizedPath = requireWorktreePathUnregistered(worktreePath);
        if (Files.exists(normalizedPath)) {
            throw new IOException("The worktree path is already in use:\n" + normalizedPath);
        }
    }

    private void addWorktree(
            final Project project,
            final Path worktreePath,
            final String sourceRef,
            final String localBranch)
            throws IOException {
        try (GitRepository repository = GitRepository.open(Path.of(project.path()))) {
            if (localBranch != null && !localBranch.isBlank()) {
                if (!repository.branchExists(localBranch)) {
                    repository.createBranch(new Branch(sourceRef), localBranch);
                }
                repository.addWorktree(worktreePath, new Branch(localBranch));
                return;
            }
            repository.addWorktree(worktreePath, new Branch(sourceRef));
        }
    }

    private SessionId registerSession(
            final ProjectId projectId,
            final String sessionName,
            final String agent,
            final String prompt,
            final Path worktreePath)
            throws InvalidObjectException {
        final Session session =
                Session.create(projectId, sessionName, agent, prompt, worktreePath.toString());
        return state.addSession(projectId, session);
    }

    private CreatedSession registerSessionTerminal(
            final SessionId sessionId, final Agent agent, final String prompt)
            throws IOException, InvalidObjectException {
        final String contextPath = state.appSettings().agentContextPath();
        final ProjectId projectId = state.sessions().get(sessionId).projectId();
        final Project project = state.projects().get(projectId);
        final Session session = state.sessions().get(sessionId);

        try {
            final String githubUser = GitHub.forProject(state, projectId).getLogin();
            AgentContext.write(project, session, contextPath, githubUser);
        } catch (IOException exception) {
            AgentContext.write(project, session, contextPath, null);
            LOG.warn("Could not enrich agent context with GitHub login", exception);
        }
        final TerminalId terminalId =
                state.addTerminal(
                        sessionId,
                        Terminal.create(
                                sessionId,
                                agent.name,
                                agent.newSessionCommand.replace(
                                        "{prompt}", PlatformCommands.shellQuote(prompt))));

        return new CreatedSession(session, terminalId);
    }

    private void requireSessionNameAvailable(final Project project, final String sessionName)
            throws InvalidObjectException {
        if (project.sessionIds().stream()
                .map(state.sessions()::get)
                .anyMatch(
                        session ->
                                session != null && session.name().equalsIgnoreCase(sessionName))) {
            throw new InvalidObjectException("A session with that name already exists.");
        }
    }

    public Path requireWorktreePathUnregistered(final Path worktreePath) throws IOException {
        final Path normalizedPath = worktreePath.toAbsolutePath().normalize();
        if (isWorktreeRegistered(normalizedPath)) {
            throw new IOException("The worktree path is already in use:\n" + normalizedPath);
        }
        return normalizedPath;
    }

    private boolean isWorktreeRegistered(final Path worktree) {
        final Path normalized = worktree.toAbsolutePath().normalize();
        return state.sessions().values().stream()
                .map(Session::worktreePath)
                .filter(path -> path != null && !path.isBlank())
                .map(path -> Path.of(path).toAbsolutePath().normalize())
                .anyMatch(normalized::equals);
    }

    public record CreatedSession(Session session, TerminalId terminalId) {}

    public record SessionDetails(String sessionName, String agent, String prompt) {}

    public record WorktreeRequest(Path worktreePath, String sourceRef, String localBranch) {}
}
