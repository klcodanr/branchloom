package com.jagent.desktop.services;

import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Creates a session, its worktree, and its initial terminal. */
public final class SessionCreationService {
    private static final Logger LOG = Logger.getLogger(SessionCreationService.class.getName());
    private final AppState state;
    private final Git git;

    public SessionCreationService(final AppState state, final Git git) {
        this.state = state;
        this.git = git;
    }

    public CreatedSession create(
            final ProjectId projectId,
            final Project project,
            final Agent agent,
            final String name,
            final String prompt,
            final String baseBranch)
            throws IOException {
        final Session draft = new Session(projectId, name, agent.name, prompt, "");
        final String branch = branchSlug(name);
        if (join(git.branchExists(project, branch))) {
            throw new IOException("A branch named '" + branch + "' already exists.");
        }
        final String worktreePath = worktreePath(project, draft);
        final Path path = Path.of(worktreePath);
        if (isWorktreeRegistered(path) || Files.exists(path)) {
            throw new IOException("The worktree path is already in use:\n" + worktreePath);
        }
        if (baseBranch == null) {
            join(git.createWorktree(project, branch, path));
        } else {
            join(git.createWorktree(project, branch, path, baseBranch));
        }

        final Session session = new Session(projectId, name, agent.name, prompt, worktreePath);
        try {
            AgentContext.write(project, session);
        } catch (IOException exception) {
            LOG.log(Level.WARNING, "Could not write agent context", exception);
        }
        final SessionId sessionId = state.addSession(projectId, session);
        final TerminalId terminalId =
                state.addTerminal(
                        sessionId,
                        new Terminal(
                                sessionId,
                                agent.name,
                                agent.newSessionCommand.replace(
                                        "{prompt}", PlatformCommands.shellQuote(prompt))));
        return new CreatedSession(session, sessionId, terminalId, worktreePath);
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

    private <T> T join(final java.util.concurrent.CompletableFuture<T> future) throws IOException {
        try {
            return future.join();
        } catch (CompletionException exception) {
            final Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IOException(
                    cause.getMessage() == null ? "Git operation failed." : cause.getMessage(),
                    exception);
        }
    }

    public record CreatedSession(
            Session session, SessionId sessionId, TerminalId terminalId, String worktreePath) {}
}
