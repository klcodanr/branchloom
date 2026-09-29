package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.CommandRunner;
import com.jagent.desktop.services.Template;
import com.jagent.desktop.ui.components.UiText;
import com.jagent.desktop.ui.utils.CurrentPath;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.nio.file.Path;
import java.util.Locale;
import javax.swing.JOptionPane;

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
            CommandRunner.run(
                    command,
                    Path.of(path),
                    null,
                    output ->
                            showError(
                                    owner,
                                    title,
                                    UiText.valueOrDefault(output, "Command failed.")));
        } catch (RuntimeException exception) {
            showFailure(title, owner, exception);
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
        return CurrentPath.resolve(appState) != null;
    }

    @Override
    public void execute() {
        try {
            final Project project = actionContext.appState().currentProject();
            if (project == null) {
                return;
            }
            final Session session = this.actionContext.appState().currentSession();
            final String path = CurrentPath.resolve(actionContext.appState());
            if (path == null) {
                return;
            }
            run(
                    Template.expand(command, project, session, true),
                    path,
                    label,
                    actionContext.window());
        } catch (RuntimeException exception) {
            showFailure(label, actionContext.window(), exception);
        }
    }

    private static void showFailure(
            final String title, final Window owner, final RuntimeException exception) {
        final String message = exception.getMessage();
        showError(owner, title, UiText.valueOrDefault(message, "Could not run command."));
    }

    private static void showError(final Window owner, final String title, final String message) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        JOptionPane.showMessageDialog(owner, message, title, JOptionPane.ERROR_MESSAGE);
    }
}
