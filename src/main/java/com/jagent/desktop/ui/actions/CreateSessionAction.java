package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.Git;
import com.jagent.desktop.services.Template;
import com.jagent.desktop.services.ViewCoordinator.ViewState;
import com.jagent.desktop.ui.GitUtils;
import com.jagent.desktop.ui.dialogs.NewSessionDialog;
import com.jagent.desktop.ui.dialogs.ProgressOperation;
import java.io.InvalidObjectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Starts the session creation workflow. */
public final class CreateSessionAction extends BaseAction {
    private static final Logger LOG = Logger.getLogger(CreateSessionAction.class.getName());
    private final Git git = new Git();

    public CreateSessionAction(final ActionContext actionContext) {
        super(actionContext);
    }

    @Override
    public String id() {
        return "new-session";
    }

    @Override
    public String label() {
        return "Start agent session";
    }

    @Override
    public boolean enabled() {
        return actionContext.appState().currentProjectId() != null;
    }

    @Override
    public void execute() {
        final AppState state = actionContext.appState();
        final ProjectId projectId = state.currentProjectId();
        if (projectId == null || state.projects().get(projectId) == null) {
            return;
        }
        if (state.appSettings().agents().isEmpty()) {
            JOptionPane.showMessageDialog(
                    actionContext.window(),
                    "Configure an agent in Settings before starting a session.",
                    "No agents configured",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        new NewSessionDialog(actionContext, request -> addSession(projectId, request))
                .setVisible(true);
    }

    private void addSession(final ProjectId projectId, final NewSessionDialog.Request request) {
        final AppState state = actionContext.appState();
        final Project project = state.projects().get(projectId);
        if (project.sessionIds().stream()
                .map(state.sessions()::get)
                .anyMatch(
                        session ->
                                session != null
                                        && session.name().equalsIgnoreCase(request.name()))) {
            LOG.severe("Create session: A session with that name already exists.");
            return;
        }

        final String branch = GitUtils.toBranchSlug(request.name());
        final ProgressOperation progress =
                ProgressOperation.start(
                        actionContext.window(), "New agent session", "Creating agent session...");
        git.branchExists(project, branch)
                .whenCompleteAsync(
                        (exists, failure) -> {
                            if (failure != null) {
                                fail(
                                        progress,
                                        "Check existing branches",
                                        failure.getMessage(),
                                        "Git could not list branches.");
                            } else {
                                prepareWorktree(
                                        projectId, project, request, branch, progress, exists);
                            }
                        },
                        SwingUtilities::invokeLater);
    }

    private void prepareWorktree(
            final ProjectId projectId,
            final Project project,
            final NewSessionDialog.Request request,
            final String branch,
            final ProgressOperation progress,
            final boolean branchExists) {
        if (branchExists) {
            final String message = "A branch named '" + branch + "' already exists.";
            fail(progress, "Create session", message, message);
            return;
        }
        final AppState state = actionContext.appState();
        final Session draft =
                new Session(projectId, request.name(), request.agent().name, request.prompt(), "");
        final String worktreePath =
                Template.resolvePath(
                        Template.expand(
                                Template.worktree(project, state.appSettings()),
                                project,
                                draft,
                                false),
                        project);
        final Path path = Path.of(worktreePath);
        if (GitUtils.isWorktreeRegistered(state.sessions(), path) || Files.exists(path)) {
            final String message = "The worktree path is already in use:\n" + worktreePath;
            fail(progress, "Create session", message, message);
            return;
        }
        git.createWorktree(project, branch, path)
                .whenCompleteAsync(
                        (ignored, failure) -> {
                            if (failure == null) {
                                finishSession(projectId, request, worktreePath, progress);
                                return;
                            }
                            fail(
                                    progress,
                                    "Create session worktree",
                                    failure.getMessage(),
                                    "Git could not create the worktree.");
                        },
                        SwingUtilities::invokeLater);
    }

    private void finishSession(
            final ProjectId projectId,
            final NewSessionDialog.Request request,
            final String worktreePath,
            final ProgressOperation progress) {
        progress.close();
        final AppState state = actionContext.appState();
        final Session session =
                new Session(
                        projectId,
                        request.name(),
                        request.agent().name,
                        request.prompt(),
                        worktreePath);
        try {
            final var sessionId = state.addSession(projectId, session);
            final var terminalId =
                    state.addTerminal(
                            sessionId,
                            new Terminal(
                                    sessionId,
                                    request.agent().name,
                                    request.agent()
                                            .newSessionCommand
                                            .replace(
                                                    "{prompt}", Git.shellQuote(request.prompt()))));
            actionContext
                    .viewCoordinator()
                    .updateView(
                            ViewId.SESSION,
                            ViewState.sessionTerminal(projectId, sessionId, terminalId));
        } catch (InvalidObjectException exception) {
            LOG.log(Level.SEVERE, "Create session", exception);
        }
    }

    private void fail(
            final ProgressOperation progress,
            final String title,
            final String output,
            final String fallback) {
        progress.close();
        showError(title, output == null || output.isBlank() ? fallback : output);
    }

    private void showError(final String title, final String message) {
        LOG.severe(title + ": " + message);
        JOptionPane.showMessageDialog(
                actionContext.window(), message, title, JOptionPane.ERROR_MESSAGE);
    }
}
