package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.Action;
import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.Template;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.ui.components.UiText;
import com.jagent.desktop.ui.utils.PathUtils;
import java.nio.file.Path;
import java.util.Locale;

/** Runs a command at the current session worktree or project path. */
public final class RunCommandAction implements Action {
    private final ViewController viewController;
    private final AppState appState;
    private final DialogService dialogService;
    private final String label;
    private final String command;

    public RunCommandAction(
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService,
            final String label,
            final String command) {
        this.viewController = viewController;
        this.appState = appState;
        this.dialogService = dialogService;
        this.label = label;
        this.command = command;
    }

    public static void run(
            final String command,
            final String path,
            final String title,
            final DialogService dialogs) {
        try {
            BackgroundOperations.runCommand("Commands", "run-command", command, Path.of(path), null)
                    .exceptionally(
                            exception -> {
                                final String message =
                                        exception.getCause() == null
                                                ? exception.getMessage()
                                                : exception.getCause().getMessage();
                                dialogs.errorMessage(
                                        UiText.valueOrDefault(message, "Could not run command."),
                                        title);
                                return null;
                            });
        } catch (RuntimeException exception) {
            final String message = exception.getMessage();
            dialogs.errorMessage(UiText.valueOrDefault(message, "Could not run command."), title);
        }
    }

    @Override
    public String id() {
        return "run-command-" + label.toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public boolean enabled() {
        return PathUtils.resolve(appState, viewController) != null;
    }

    @Override
    public void execute() {
        try {
            final Project project = viewController.currentProject();
            if (project == null) {
                return;
            }
            final Session session = this.viewController.currentSession();
            final String path = PathUtils.resolve(appState, viewController);
            if (path == null) {
                return;
            }
            run(Template.expand(command, project, session, true), path, label, dialogService);
        } catch (RuntimeException exception) {
            final String message = exception.getMessage();
            dialogService.errorMessage(
                    UiText.valueOrDefault(message, "Could not run command."), label);
        }
    }
}
