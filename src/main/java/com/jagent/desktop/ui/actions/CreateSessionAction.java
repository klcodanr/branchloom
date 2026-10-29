package com.jagent.desktop.ui.actions;

import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.async.ProgressOperation;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.git.Branch;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundJobs;
import com.jagent.desktop.services.SessionCreationService;
import com.jagent.desktop.services.SessionLauncher;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.ui.dialogs.NewSessionDialog;
import com.jagent.desktop.ui.utils.ErrorMessages;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Starts the session creation workflow. */
public final class CreateSessionAction implements Action {
    private final ViewController viewController;
    private final AppState appState;
    private final DialogService dialogService;
    private static final String CREATE_SESSION = "Create session";
    private final SessionCreationService sessionCreator;
    private final SessionLauncher sessionLauncher;

    @Inject
    public CreateSessionAction(
            final ViewController viewController,
            final AppState appState,
            final BackgroundJobs backgroundJobs,
            final DialogService dialogService) {
        this.viewController = viewController;
        this.appState = appState;
        this.dialogService = dialogService;
        sessionCreator = new SessionCreationService(appState);
        sessionLauncher =
                new SessionLauncher(viewController, appState, dialogService, backgroundJobs);
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
        return viewController.currentProjectId() != null;
    }

    @Override
    public void execute() {
        final var state = appState;
        final ProjectId projectId = viewController.currentProjectId();
        if (projectId == null || state.projects().get(projectId) == null) {
            return;
        }
        if (state.appSettings().agents().isEmpty()) {
            dialogService.message(
                    "Configure an agent in Settings before starting a session.",
                    "No agents configured",
                    DialogService.MessageType.INFORMATION);
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
                            dialogService.show(
                                    "New agent session",
                                    new NewSessionDialog(
                                            appState,
                                            dialogService,
                                            preferredBranches(branches),
                                            request -> addSession(projectId, request)),
                                    "OK");
                        })
                .exceptionally(
                        failure -> {
                            final String message =
                                    ErrorMessages.deepestCause(
                                            failure, "Git could not list branches.");
                            dialogService.message(
                                    message, "Load base branches", DialogService.MessageType.ERROR);
                            return null;
                        });
    }

    private List<Branch> preferredBranches(final List<Branch> branches) {
        return branches.stream()
                .sorted(Comparator.comparingInt(this::branchPriority).thenComparing(Branch::name))
                .toList();
    }

    private int branchPriority(final Branch branch) {
        return switch (branch.displayName()) {
            case "origin/main" -> 0;
            case "main" -> 1;
            case "origin/master" -> 2;
            case "master" -> 3;
            default -> branch.remote() ? 5 : 4;
        };
    }

    // Public for action integration tests and dialog callbacks.
    public void addSession(final ProjectId projectId, final NewSessionDialog.Request request) {
        final var state = appState;
        final Project project = state.projects().get(projectId);
        if (project.sessionIds().stream()
                .map(state.sessions()::get)
                .anyMatch(
                        session ->
                                session != null
                                        && session.name().equalsIgnoreCase(request.name()))) {
            dialogService.message(
                    "A session with that name already exists.",
                    CREATE_SESSION,
                    DialogService.MessageType.ERROR);
            return;
        }

        ProgressOperation.run(
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
                            dialogService.message(
                                    message, CREATE_SESSION, DialogService.MessageType.ERROR);
                            return null;
                        });
    }
}
