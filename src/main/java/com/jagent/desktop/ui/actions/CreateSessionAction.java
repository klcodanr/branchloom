package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.services.AgentContext;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.CommandRunner;
import com.jagent.desktop.services.Git;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.Template;
import com.jagent.desktop.services.ViewCoordinator.ViewState;
import com.jagent.desktop.ui.components.TerminalPanel;
import com.jagent.desktop.ui.dialogs.NewSessionDialog;
import com.jagent.desktop.ui.dialogs.ProgressOperation;
import com.jagent.desktop.ui.utils.GitUtils;
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
                        actionContext.window(), CREATE_SESSION, "Creating agent session...");
        final Session draft =
                new Session(projectId, request.name(), request.agent().name, request.prompt(), "");
        git.branchExists(project, branch)
                .whenCompleteAsync(
                        (exists, failure) -> {
                            if (failure != null) {
                                fail(
                                        progress,
                                        failure.getMessage() == null
                                                ? "Git could not list branches."
                                                : failure.getMessage());
                            } else {
                                prepareWorktree(
                                        projectId, project, request, branch, draft, progress,
                                        exists);
                            }
                        },
                        SwingUtilities::invokeLater);
    }

    private void prepareWorktree(
            final ProjectId projectId,
            final Project project,
            final NewSessionDialog.Request request,
            final String branch,
            final Session draft,
            final ProgressOperation progress,
            final boolean branchExists) {
        if (branchExists) {
            fail(progress, "A branch named '" + branch + "' already exists.");
            return;
        }
        final AppState state = actionContext.appState();
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
            fail(progress, "The worktree path is already in use:\n" + worktreePath);
            return;
        }
        final var worktreeCreation =
                request.baseBranch() == null
                        ? git.createWorktree(project, branch, path)
                        : git.createWorktree(project, branch, path, request.baseBranch());
        worktreeCreation.whenCompleteAsync(
                (ignored, failure) -> {
                    if (failure == null) {
                        finishSession(projectId, project, request, worktreePath, progress);
                        return;
                    }
                    fail(
                            progress,
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
            final ProgressOperation progress) {
        final AppState state = actionContext.appState();
        progress.close();
        final Session session =
                new Session(
                        projectId,
                        request.name(),
                        request.agent().name,
                        request.prompt(),
                        worktreePath);
        try {
            AgentContext.write(projectFor(state, projectId), session);
        } catch (IOException exception) {
            LOG.log(Level.WARNING, "Could not write agent context", exception);
        }
        final SessionId sessionId;
        try {
            sessionId = state.addSession(projectId, session);
        } catch (IOException exception) {
            LOG.log(Level.SEVERE, CREATE_SESSION, exception);
            showError(
                    CREATE_SESSION,
                    exception.getMessage() == null
                            ? "Could not save the new session."
                            : exception.getMessage());
            return;
        }
        final var terminalId =
                state.addTerminal(
                        sessionId,
                        new Terminal(
                                sessionId,
                                request.agent().name,
                                request.agent()
                                        .newSessionCommand
                                        .replace(
                                                "{prompt}",
                                                PlatformCommands.shellQuote(request.prompt()))));
        final Terminal terminal = state.terminals().get(terminalId);
        final TerminalPanel terminalPanel =
                TerminalPanel.retained(
                        terminalId,
                        terminal,
                        Path.of(worktreePath).toAbsolutePath().normalize(),
                        project.name() + " > " + session.name() + " > " + terminal.title());
        actionContext
                .viewCoordinator()
                .updateView(
                        ViewId.SESSION,
                        ViewState.sessionTerminal(projectId, sessionId, terminalId));
        if (project.startupCommands().isEmpty()) {
            terminalPanel.start();
        } else {
            final var job = actionContext.viewCoordinator().backgroundJobs().start("Session setup");
            runStartupCommand(project, worktreePath, job, 0, terminalPanel::start);
        }
    }

    private void runStartupCommand(
            final Project project,
            final String worktreePath,
            final com.jagent.desktop.services.BackgroundJobs.Handle job,
            final int commandIndex,
            final Runnable onComplete) {
        if (commandIndex >= project.startupCommands().size()) {
            job.complete();
            onComplete.run();
            return;
        }
        final String command = project.startupCommands().get(commandIndex);
        job.update(
                "Running startup command "
                        + (commandIndex + 1)
                        + " of "
                        + project.startupCommands().size());
        CommandRunner.run(
                command,
                Path.of(worktreePath),
                ignored -> {},
                () -> runStartupCommand(project, worktreePath, job, commandIndex + 1, onComplete),
                output -> {
                    job.fail(output == null || output.isBlank() ? "Setup command failed." : output);
                });
    }

    private Project projectFor(final AppState state, final ProjectId projectId) {
        return state.projects().get(projectId);
    }

    private void fail(final ProgressOperation progress, final String message) {
        progress.close();
        showError(CREATE_SESSION, message);
    }

    private void showError(final String title, final String message) {
        LOG.severe(title + ": " + message);
        if (actionContext.window() != null) {
            JOptionPane.showMessageDialog(
                    actionContext.window(), message, title, JOptionPane.ERROR_MESSAGE);
        }
    }
}
