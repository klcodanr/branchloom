package com.jagent.desktop.ui.actions;

import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.async.ProgressOperation;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.services.github.GitHubAuth;
import com.jagent.desktop.ui.dialogs.ImportProjectDialog;
import com.jagent.desktop.ui.utils.ErrorMessages;
import java.nio.file.Path;
import java.util.Optional;

/** Starts importing a project from a remote Git repository. */
public final class ImportProjectAction implements Action {
    private final ViewController viewController;
    private final AppState appState;
    private final DialogService dialogService;
    private static final String TITLE = "Clone remote project";
    private final String targetGroup;

    @Inject
    public ImportProjectAction(
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService) {
        this(viewController, appState, dialogService, null);
    }

    public ImportProjectAction(
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService,
            final String targetGroup) {
        this.viewController = viewController;
        this.appState = appState;
        this.dialogService = dialogService;
        this.targetGroup = targetGroup;
    }

    @Override
    public String id() {
        return "import-project";
    }

    @Override
    public String label() {
        return TITLE;
    }

    @Override
    public void execute() {
        ProgressOperation.run(
                        TITLE,
                        "Loading GitHub accounts...",
                        () -> new GitHubAuth().listCredentials())
                .thenAccept(
                        auths ->
                                dialogService.show(
                                        TITLE,
                                        new ImportProjectDialog(
                                                dialogService, auths, this::importProject),
                                        "OK"));
    }

    private void importProject(final ImportProjectDialog.Request request) {

        final String validationError = validateRequest(request);
        if (validationError != null) {
            dialogService.message(TITLE, validationError, DialogService.MessageType.ERROR);
            return;
        }

        final String projectName =
                Optional.ofNullable(request.destination().getFileName())
                        .map(Path::toString)
                        .orElse("");
        ProgressOperation.run(
                        TITLE,
                        "Cloning repository...",
                        () ->
                                GitRepository.cloneRepository(
                                        request.remote(), request.destination()))
                .thenAccept(
                        clonedPath -> {
                            final Project project =
                                    Project.create(
                                            projectName, clonedPath.toString(), request.auth());
                            final var projectId =
                                    appState.addProject(
                                            targetGroup == null
                                                    ? project
                                                    : project.withGroup(targetGroup));
                            viewController.openProject(projectId);
                        })
                .exceptionally(
                        failure -> {
                            dialogService.message(
                                    TITLE,
                                    "Could not clone the repository:\n"
                                            + ErrorMessages.deepestCause(
                                                    failure, "Git did not provide more details."),
                                    DialogService.MessageType.ERROR);
                            return null;
                        });
    }

    private String validateRequest(final ImportProjectDialog.Request request) {
        final Path destinationPath = request.destination();
        final String projectName =
                Optional.ofNullable(destinationPath.getFileName()).map(Path::toString).orElse("");
        if (projectName.isBlank()) {
            return "Choose a destination directory below the filesystem root.";
        }
        if (appState.projects().values().stream()
                .anyMatch(project -> project.name().equalsIgnoreCase(projectName))) {
            return "A project with that name is already registered.";
        }
        if (appState.projects().values().stream()
                .anyMatch(
                        project ->
                                destinationPath.equals(
                                        Path.of(project.path()).toAbsolutePath().normalize()))) {
            return "That destination is already registered as a project.";
        }
        return null;
    }
}
