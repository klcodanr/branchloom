package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.async.ProgressOperation;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.git.Branch;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.SessionCreationService;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.ui.components.SessionLauncher;
import com.jagent.desktop.ui.dialogs.NewSessionDialog;
import com.jagent.desktop.ui.utils.ErrorDialogs;
import com.jagent.desktop.ui.utils.ErrorMessages;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import javax.swing.JOptionPane;

/** Starts the session creation workflow. */
public final class CreateSessionAction extends BaseAction {
    private static final String CREATE_SESSION = "Create session";
    private final SessionCreationService sessionCreator;
    private final SessionLauncher sessionLauncher;

    public CreateSessionAction(final ActionContext actionContext) {
        super(actionContext);
        sessionCreator = new SessionCreationService(actionContext.appState());
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

        BackgroundOperations.submit(
                        "Sessions",
                        "list-branches",
                        () -> {
                            try (GitRepository repository =
                                    GitRepository.open(Path.of(project.path()))) {
                                return repository.listBranches();
                            }
                        })
                .thenAccept(
                        branches -> {
                            new NewSessionDialog(
                                            actionContext,
                                            preferredBranches(branches),
                                            request -> addSession(projectId, request))
                                    .setVisible(true);
                        })
                .exceptionally(
                        failure -> {
                            final String message =
                                    ErrorMessages.deepestCause(
                                            failure, "Git could not list branches.");
                            ErrorDialogs.show(
                                    actionContext.window(), "Load base branches", message);
                            return null;
                        });
    }

    private List<Branch> preferredBranches(final List<Branch> branches) {
        return branches.stream()
                .sorted(Comparator.comparingInt(this::branchPriority).thenComparing(Branch::name))
                .toList();
    }

    private int branchPriority(final Branch branch) {
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
            ErrorDialogs.show(
                    actionContext.window(),
                    CREATE_SESSION,
                    "A session with that name already exists.");
            return;
        }

        ProgressOperation.run(
                        actionContext,
                        CREATE_SESSION,
                        "Creating agent session...",
                        () ->
                                sessionCreator.create(
                                        projectId,
                                        project,
                                        request.agent(),
                                        request.name(),
                                        request.prompt(),
                                        request.baseBranch()))
                .thenAccept(
                        created -> {
                            sessionLauncher.launch(project, created);
                        })
                .exceptionally(
                        failure -> {
                            final String message =
                                    ErrorMessages.deepestCause(
                                            failure, "Could not create the session.");
                            ErrorDialogs.show(actionContext.window(), CREATE_SESSION, message);
                            return null;
                        });
    }
}
