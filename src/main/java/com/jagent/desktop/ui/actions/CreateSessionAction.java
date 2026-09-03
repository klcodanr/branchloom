package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.AgentContext;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.CommandRunner;
import com.jagent.desktop.services.Git;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.SessionSetup;
import com.jagent.desktop.services.Template;
import com.jagent.desktop.services.ViewCoordinator.ViewState;
import com.jagent.desktop.ui.GitUtils;
import com.jagent.desktop.ui.dialogs.NewSessionDialog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Starts the session creation workflow. */
public final class CreateSessionAction extends BaseAction {
    private static final Logger LOG = Logger.getLogger(CreateSessionAction.class.getName());
    private static final String CREATE_SESSION = "Create session";
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
        final Project project = state.projects().get(projectId);
        git.listBranches(project)
                .whenCompleteAsync(
                        (branches, failure) -> {
                            if (failure != null) {
                                showError(
                                        "Load base branches",
                                        failure.getMessage() == null
                                                ? "Git could not list branches."
                                                : failure.getMessage());
                                return;
                            }
                            new NewSessionDialog(
                                            actionContext,
                                            preferredBranches(branches),
                                            request -> addSession(projectId, request))
                                    .setVisible(true);
                        },
                        SwingUtilities::invokeLater);
    }

    private List<Git.Branch> preferredBranches(final List<Git.Branch> branches) {
        return branches.stream()
                .sorted(
                        Comparator.comparingInt(this::branchPriority)
                                .thenComparing(Git.Branch::name))
                .toList();
    }

    private int branchPriority(final Git.Branch branch) {
        return switch (branch.name()) {
            case "origin/main" -> 0;
            case "main" -> 1;
            case "origin/master" -> 2;
            case "master" -> 3;
            default -> branch.remote() ? 5 : 4;
        };
    }

    protected void addSession(final ProjectId projectId, final NewSessionDialog.Request request) {
        final AppState state = actionContext.appState();
        final SessionSetup setup = actionContext.viewCoordinator().sessionSetup();
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
        final Session draft =
                new Session(projectId, request.name(), request.agent().name, request.prompt(), "");
        final var sessionId = setup.begin(draft);
        state.updateCurrentProject(projectId);
        state.updateCurrentSession(sessionId);
        actionContext
                .viewCoordinator()
                .updateView(ViewId.SESSION, ViewState.session(projectId, sessionId));
        git.branchExists(project, branch)
                .whenCompleteAsync(
                        (exists, failure) -> {
                            if (failure != null) {
                                fail(
                                        sessionId,
                                        failure.getMessage() == null
                                                ? "Git could not list branches."
                                                : failure.getMessage());
                            } else {
                                prepareWorktree(
                                        projectId, project, request, branch, sessionId, exists);
                            }
                        },
                        SwingUtilities::invokeLater);
    }

    private void prepareWorktree(
            final ProjectId projectId,
            final Project project,
            final NewSessionDialog.Request request,
            final String branch,
            final SessionId sessionId,
            final boolean branchExists) {
        if (branchExists) {
            final String message = "A branch named '" + branch + "' already exists.";
            fail(sessionId, message);
            return;
        }
        final AppState state = actionContext.appState();
        final SessionSetup setup = actionContext.viewCoordinator().sessionSetup();
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
            fail(sessionId, message);
            return;
        }
        setup.update(sessionId, "Creating worktree...");
        final var worktreeCreation =
                request.baseBranch() == null
                        ? git.createWorktree(project, branch, path)
                        : git.createWorktree(project, branch, path, request.baseBranch());
        worktreeCreation.whenCompleteAsync(
                (ignored, failure) -> {
                    if (failure == null) {
                        setup.update(sessionId, "Starting agent...");
                        finishSession(projectId, project, request, worktreePath, sessionId);
                        return;
                    }
                    fail(
                            sessionId,
                            failure.getMessage() == null
                                    ? "Git could not create the worktree."
                                    : failure.getMessage());
                },
                SwingUtilities::invokeLater);
    }

    private void finishSession(
            final ProjectId projectId,
            final Project project,
            final NewSessionDialog.Request request,
            final String worktreePath,
            final SessionId sessionId) {
        final AppState state = actionContext.appState();
        final SessionSetup setup = actionContext.viewCoordinator().sessionSetup();
        setup.update(sessionId, "Starting agent...");
        final Session session =
                new Session(
                        projectId,
                        request.name(),
                        request.agent().name,
                        request.prompt(),
                        worktreePath);
        try {
            AgentContext.write(projectFor(state, projectId), session);
            final SessionId persistedSessionId = setup.promote(state, sessionId, session);
            final var terminalId =
                    state.addTerminal(
                            persistedSessionId,
                            new Terminal(
                                    persistedSessionId,
                                    request.agent().name,
                                    request.agent()
                                            .newSessionCommand
                                            .replace(
                                                    "{prompt}",
                                                    PlatformCommands.shellQuote(
                                                            request.prompt()))));
            actionContext
                    .viewCoordinator()
                    .updateView(ViewId.SESSION, ViewState.session(projectId, persistedSessionId));
            runStartupCommand(
                    projectId, project, request, worktreePath, persistedSessionId, terminalId, 0);
        } catch (IOException exception) {
            LOG.log(Level.SEVERE, CREATE_SESSION, exception);
            fail(
                    sessionId,
                    exception.getMessage() == null
                            ? "Could not save the new session."
                            : exception.getMessage());
        }
    }

    private void runStartupCommand(
            final ProjectId projectId,
            final Project project,
            final NewSessionDialog.Request request,
            final String worktreePath,
            final SessionId sessionId,
            final TerminalId terminalId,
            final int commandIndex) {
        if (commandIndex >= project.startupCommands().size()) {
            actionContext.viewCoordinator().sessionSetup().complete(sessionId);
            actionContext
                    .viewCoordinator()
                    .updateView(
                            ViewId.SESSION,
                            ViewState.sessionTerminal(projectId, sessionId, terminalId));
            return;
        }
        final String command = project.startupCommands().get(commandIndex);
        final SessionSetup setup = actionContext.viewCoordinator().sessionSetup();
        setup.update(sessionId, "Running setup command...");
        CommandRunner.run(
                command,
                Path.of(worktreePath),
                ignored -> {},
                () -> {
                    setup.complete(sessionId);
                    runStartupCommand(
                            projectId,
                            project,
                            request,
                            worktreePath,
                            sessionId,
                            terminalId,
                            commandIndex + 1);
                },
                output -> {
                    fail(
                            sessionId,
                            output == null || output.isBlank() ? "Setup command failed." : output);
                });
    }

    private Project projectFor(final AppState state, final ProjectId projectId) {
        return state.projects().get(projectId);
    }

    private void fail(final SessionId sessionId, final String message) {
        LOG.severe(CREATE_SESSION + ": " + message);
        actionContext.viewCoordinator().sessionSetup().fail(sessionId, message);
    }

    private void showError(final String title, final String message) {
        LOG.severe(title + ": " + message);
        JOptionPane.showMessageDialog(
                actionContext.window(), message, title, JOptionPane.ERROR_MESSAGE);
    }
}
