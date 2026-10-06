package com.jagent.desktop.services;

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
import java.io.IOException;
import java.io.InvalidObjectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates a session, its worktree, and its initial terminal. */
public final class SessionCreationService {
    private static final Logger LOG = LoggerFactory.getLogger(SessionCreationService.class);
    private final AppState state;

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
            final String branch = branchSlug(name);
            final Path path = prepareWorktreePath(projectId, project, name, agent.name, prompt);
            final String worktreePath = path.toString();
            if (git.branchExists(branch)) {
                throw new IOException("A branch named '" + branch + "' already exists.");
            }
            String base = baseBranch;
            if (base == null) {
                base = git.currentBranch();
            }
            final Branch newBranch = git.createBranch(new Branch(base), branch);
            git.addWorktree(path, newBranch);
            return registerSessionWithTerminal(
                    projectId,
                    project,
                    name,
                    agent.name,
                    prompt,
                    worktreePath,
                    agent.newSessionCommand);
        }
    }

    public void checkCreateSession(
            final Project project, final String sessionName, final Path worktreePath)
            throws IOException, InvalidObjectException {
        requireSessionNameAvailable(project, sessionName);
        final Path normalizedPath = requireWorktreePathUnregistered(worktreePath);
        if (!Files.isDirectory(normalizedPath)) {
            throw new IOException("The worktree directory does not exist:\n" + normalizedPath);
        }
    }

    public SessionId createSession(
            final ProjectId projectId,
            final String sessionName,
            final String agent,
            final String prompt,
            final Path worktreePath)
            throws IOException, InvalidObjectException {
        return registerSession(
                projectId, sessionName, agent, prompt, worktreePath.toAbsolutePath().normalize());
    }

    public void checkCreateWorktreeAndSession(
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
    }

    public SessionId createWorktreeAndSession(
            final ProjectId projectId,
            final Project project,
            final SessionDetails sessionDetails,
            final WorktreeRequest worktreeRequest)
            throws IOException, InvalidObjectException {
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

    public void checkFetchBranchCreateWorktreeAndSession(
            final Project project,
            final SessionDetails sessionDetails,
            final WorktreeRequest worktreeRequest)
            throws IOException, InvalidObjectException {
        checkCreateWorktreeAndSession(project, sessionDetails, worktreeRequest);
        if (!worktreeRequest.sourceRef().contains("/")) {
            throw new IOException(
                    "Invalid remote branch reference: " + worktreeRequest.sourceRef());
        }
    }

    public SessionId fetchBranchCreateWorktreeAndSession(
            final ProjectId projectId,
            final Project project,
            final SessionDetails sessionDetails,
            final WorktreeRequest worktreeRequest)
            throws IOException, InvalidObjectException {
        try (GitRepository repository = GitRepository.open(Path.of(project.path()))) {
            repository.fetchRemoteRef(worktreeRequest.sourceRef());
        }
        return createWorktreeAndSession(projectId, project, sessionDetails, worktreeRequest);
    }

    public Path prepareWorktreePath(
            final ProjectId projectId,
            final Project project,
            final String sessionName,
            final String agent,
            final String prompt)
            throws IOException {
        requireSessionNameAvailable(project, sessionName);
        final Session draft = new Session(projectId, sessionName, agent, prompt, "");
        final String worktreePath = worktreePath(project, draft);
        final Path path = requireWorktreePathUnregistered(Path.of(worktreePath));
        if (Files.exists(path)) {
            throw new IOException("The worktree path is already in use:\n" + worktreePath);
        }
        return path;
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
                repository.createBranch(new Branch(sourceRef), localBranch);
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
                new Session(projectId, sessionName, agent, prompt, worktreePath.toString());
        return state.addSession(projectId, session);
    }

    private CreatedSession registerSessionWithTerminal(
            final ProjectId projectId,
            final Project project,
            final String sessionName,
            final String agent,
            final String prompt,
            final String worktreePath,
            final String commandTemplate)
            throws IOException, InvalidObjectException {
        final Session session = new Session(projectId, sessionName, agent, prompt, worktreePath);
        final String contextPath = state.appSettings().agentContextPath();
        AgentContext.write(project, session, contextPath, null);
        final SessionId sessionId = state.addSession(projectId, session);
        final TerminalId terminalId =
                state.addTerminal(
                        sessionId,
                        new Terminal(
                                sessionId,
                                agent,
                                commandTemplate.replace(
                                        "{prompt}", PlatformCommands.shellQuote(prompt))));
        try {
            final String githubUser = GitHub.forProject(state, projectId).getLogin();
            AgentContext.write(project, session, contextPath, githubUser);
        } catch (IOException exception) {
            LOG.warn("Could not enrich agent context with GitHub login", exception);
        }
        return new CreatedSession(session, sessionId, terminalId, worktreePath);
    }

    public void requireSessionNameAvailable(final Project project, final String sessionName)
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

    private String worktreePath(final Project project, final Session draft) {
        return Template.resolvePath(
                Template.expand(
                        Template.worktree(project, state.appSettings()), project, draft, false),
                project);
    }

    private String branchSlug(final String input) {
        if (input == null || input.isBlank()) {
            return "branch";
        }
        final String slug =
                Normalizer.normalize(input, Normalizer.Form.NFKD)
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("^-+|-+$", "");
        return slug.isEmpty()
                ? "branch"
                : slug.length() > 100 ? slug.substring(0, 100).replaceFirst("-+$", "") : slug;
    }

    private boolean isWorktreeRegistered(final Path worktree) {
        final Path normalized = worktree.toAbsolutePath().normalize();
        return state.sessions().values().stream()
                .map(Session::worktreePath)
                .filter(path -> path != null && !path.isBlank())
                .map(path -> Path.of(path).toAbsolutePath().normalize())
                .anyMatch(normalized::equals);
    }

    public record CreatedSession(
            Session session, SessionId sessionId, TerminalId terminalId, String worktreePath) {}

    public record SessionDetails(String sessionName, String agent, String prompt) {}

    public record WorktreeRequest(Path worktreePath, String sourceRef, String localBranch) {}
}
