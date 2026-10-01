package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.async.ProgressOperation;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.services.ViewCoordinator.ViewState;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.services.github.GitHubAuth;
import com.jagent.desktop.ui.dialogs.ImportProjectDialog;
import com.jagent.desktop.ui.utils.ErrorDialogs;
import com.jagent.desktop.ui.utils.ErrorMessages;
import java.nio.file.Path;
import java.util.Optional;

/** Starts importing a project from a remote Git repository. */
public final class ImportProjectAction extends BaseAction {
    private static final String TITLE = "Clone remote project";
    private final String targetGroup;

    public ImportProjectAction(final ActionContext actionContext) {
        this(actionContext, null);
    }

    public ImportProjectAction(final ActionContext actionContext, final String targetGroup) {
        super(actionContext);
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
                        actionContext,
                        TITLE,
                        "Loading GitHub accounts...",
                        () -> new GitHubAuth().listCredentials(actionContext.appState()))
                .thenAccept(
                        configuredAuths ->
                                new ImportProjectDialog(
                                                actionContext, configuredAuths, this::importProject)
                                        .setVisible(true))
                .exceptionally(
                        failure -> {
                            new ImportProjectDialog(
                                            actionContext, java.util.List.of(), this::importProject)
                                    .setVisible(true);
                            return null;
                        });
    }

    private void importProject(final ImportProjectDialog.Request request) {
        final Path destinationPath = request.destination();
        final String projectName =
                Optional.ofNullable(destinationPath.getFileName()).map(Path::toString).orElse("");
        if (projectName.isBlank()) {
            ErrorDialogs.show(
                    actionContext.window(),
                    TITLE,
                    "Choose a destination directory below the filesystem root.");
            return;
        }
        if (actionContext.appState().projects().values().stream()
                .anyMatch(project -> project.name().equalsIgnoreCase(projectName))) {
            ErrorDialogs.show(
                    actionContext.window(),
                    TITLE,
                    "A project with that name is already registered.");
            return;
        }
        if (actionContext.appState().projects().values().stream()
                .anyMatch(
                        project ->
                                destinationPath.equals(
                                        Path.of(project.path()).toAbsolutePath().normalize()))) {
            ErrorDialogs.show(
                    actionContext.window(),
                    TITLE,
                    "That destination is already registered as a project.");
            return;
        }

        ProgressOperation.run(
                        actionContext,
                        TITLE,
                        "Cloning repository...",
                        () -> GitRepository.cloneRepository(request.remote(), destinationPath))
                .thenAccept(
                        clonedPath -> {
                            final Project project =
                                    new Project(projectName, clonedPath.toString(), request.auth());
                            final var projectId =
                                    actionContext
                                            .appState()
                                            .addProject(
                                                    targetGroup == null
                                                            ? project
                                                            : project.withGroup(targetGroup));
                            actionContext
                                    .viewCoordinator()
                                    .updateView(ViewId.PROJECT, ViewState.project(projectId));
                        })
                .exceptionally(
                        failure -> {
                            ErrorDialogs.show(
                                    actionContext.window(),
                                    TITLE,
                                    "Could not clone the repository:\n"
                                            + ErrorMessages.deepestCause(
                                                    failure, "Git did not provide more details."));
                            return null;
                        });
    }
}
