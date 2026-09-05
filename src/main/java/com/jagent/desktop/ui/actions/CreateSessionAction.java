package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.Git;
import com.jagent.desktop.services.SessionCreationService;
import com.jagent.desktop.ui.components.SessionLauncher;
import com.jagent.desktop.ui.dialogs.NewSessionDialog;
import com.jagent.desktop.ui.dialogs.ProgressOperation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Starts the session creation workflow. */
public final class CreateSessionAction extends BaseAction {
    private static final Logger LOG = Logger.getLogger(CreateSessionAction.class.getName());
    private static final String CREATE_SESSION = "Create session";
    private final Git git = new Git();
    private final SessionCreationService sessionCreator;
    private final SessionLauncher sessionLauncher;

    public CreateSessionAction(final ActionContext actionContext) {
        super(actionContext);
        sessionCreator = new SessionCreationService(actionContext.appState(), git);
        sessionLauncher = new SessionLauncher(actionContext);
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

        final ProgressOperation progress =
                ProgressOperation.start(
                        actionContext.window(), CREATE_SESSION, "Creating agent session...");
        com.jagent.desktop.services.BackgroundTasks.submit(
                        "Sessions",
                        "create",
                        () -> {
                            try {
                                return sessionCreator.create(
                                        projectId,
                                        project,
                                        request.agent(),
                                        request.name(),
                                        request.prompt(),
                                        request.baseBranch());
                            } catch (IOException exception) {
                                throw new UncheckedIOException(exception);
                            }
                        })
                .whenCompleteAsync(
                        (created, failure) -> {
                            progress.close();
                            if (failure != null) {
                                showError(
                                        CREATE_SESSION,
                                        message(failure, "Could not create the session."));
                            } else {
                                sessionLauncher.launch(project, created);
                            }
                        },
                        SwingUtilities::invokeLater);
    }

    private String message(final Throwable failure, final String fallback) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? fallback
                : cause.getMessage();
    }

    private void showError(final String title, final String message) {
        LOG.severe(title + ": " + message);
        if (actionContext.window() != null) {
            JOptionPane.showMessageDialog(
                    actionContext.window(), message, title, JOptionPane.ERROR_MESSAGE);
        }
    }
}
