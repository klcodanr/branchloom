package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.Template;
import com.jagent.desktop.ui.components.UiText;
import com.jagent.desktop.ui.utils.ErrorDialogs;
import com.jagent.desktop.ui.utils.PathUtils;
import java.awt.Window;
import java.nio.file.Path;
import java.util.Locale;

/** Runs a command at the current session worktree or project path. */
public final class RunCommandAction extends BaseAction {
    private final String label;
    private final String command;

    public RunCommandAction(
            final ActionContext actionContext, final String label, final String command) {
        super(actionContext);
        this.label = label;
        this.command = command;
    }

    public static void run(
            final String command, final String path, final String title, final Window owner) {
        try {
            BackgroundOperations.runCommand("Commands", "run-command", command, Path.of(path), null)
                    .exceptionally(
                            exception -> {
                                final String message =
                                        exception.getCause() == null
                                                ? exception.getMessage()
                                                : exception.getCause().getMessage();
                                ErrorDialogs.show(
                                        owner,
                                        title,
                                        UiText.valueOrDefault(message, "Could not run command."));
                                return null;
                            });
        } catch (RuntimeException exception) {
            final String message = exception.getMessage();
            ErrorDialogs.show(
                    owner, title, UiText.valueOrDefault(message, "Could not run command."));
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
        final var appState = actionContext.appState();
        return PathUtils.resolve(appState) != null;
    }

    @Override
    public void execute() {
        try {
            final Project project = actionContext.appState().currentProject();
            if (project == null) {
                return;
            }
            final Session session = this.actionContext.appState().currentSession();
            final String path = PathUtils.resolve(actionContext.appState());
            if (path == null) {
                return;
            }
            run(
                    Template.expand(command, project, session, true),
                    path,
                    label,
                    actionContext.window());
        } catch (RuntimeException exception) {
            final String message = exception.getMessage();
            ErrorDialogs.show(
                    actionContext.window(),
                    label,
                    UiText.valueOrDefault(message, "Could not run command."));
        }
    }
}
