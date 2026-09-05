package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.services.Git;
import com.jagent.desktop.services.ViewCoordinator.ViewState;
import com.jagent.desktop.ui.dialogs.ImportProjectDialog;
import com.jagent.desktop.ui.dialogs.ProgressOperation;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Starts importing a project from a remote Git repository. */
public final class ImportProjectAction extends BaseAction {
    private static final String TITLE = "Clone remote project";
    private static final Logger LOG = Logger.getLogger(ImportProjectAction.class.getName());

    public ImportProjectAction(final ActionContext actionContext) {
        super(actionContext);
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
        new ImportProjectDialog(actionContext, this::importProject).setVisible(true);
    }

    private void importProject(final ImportProjectDialog.Request request) {
        final Path destinationPath = request.destination();
        final String projectName =
                Optional.ofNullable(destinationPath.getFileName()).map(Path::toString).orElse("");
        if (projectName.isBlank()) {
            showError("Choose a destination directory below the filesystem root.");
            return;
        }
        final String registrationFailure = registrationFailure(projectName, destinationPath);
        if (registrationFailure != null) {
            showError(registrationFailure);
            return;
        }

        final ProgressOperation progress =
                ProgressOperation.start(actionContext.window(), TITLE, "Cloning repository...");
        new Git()
                .cloneRepository(request.remote(), destinationPath)
                .whenCompleteAsync(
                        (ignored, failure) -> {
                            progress.close();
                            if (failure != null) {
                                final Throwable cause =
                                        failure instanceof CompletionException
                                                        && failure.getCause() != null
                                                ? failure.getCause()
                                                : failure;
                                LOG.warning("Clone remote project failed: " + cause.getMessage());
                                showError("Could not clone the repository:\n" + message(cause));
                                return;
                            }
                            final var projectId =
                                    actionContext
                                            .appState()
                                            .addProject(
                                                    new Project(
                                                            projectName,
                                                            destinationPath.toString(),
                                                            null));
                            actionContext
                                    .viewCoordinator()
                                    .updateView(ViewId.PROJECT, ViewState.project(projectId));
                        },
                        SwingUtilities::invokeLater);
    }

    private String registrationFailure(final String projectName, final Path destination) {
        if (actionContext.appState().projects().values().stream()
                .anyMatch(project -> project.name().equalsIgnoreCase(projectName))) {
            return "A project with that name is already registered.";
        }
        if (actionContext.appState().projects().values().stream()
                .anyMatch(
                        project ->
                                destination.equals(
                                        Path.of(project.path()).toAbsolutePath().normalize()))) {
            return "That destination is already registered as a project.";
        }
        return null;
    }

    private void showError(final String message) {
        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            JOptionPane.showMessageDialog(
                    actionContext.window(), message, TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String message(final Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? "Git did not provide more details."
                : failure.getMessage();
    }
}
