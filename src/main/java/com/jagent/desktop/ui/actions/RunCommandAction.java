package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.CommandRunner;
import com.jagent.desktop.services.Template;
import com.jagent.desktop.ui.utils.CurrentPath;
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
        CommandRunner.run(
                command,
                Path.of(path),
                null,
                output ->
                        JOptionPane.showMessageDialog(
                                owner,
                                output == null || output.isBlank() ? "Command failed." : output,
                                title,
                                JOptionPane.ERROR_MESSAGE));
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
        final Project project = actionContext.appState().currentProject();
        if (project == null) {
            return;
        }
        final Session session = this.actionContext.appState().currentSession();
        final String path = CurrentPath.resolve(actionContext.appState());
        if (path == null) {
            return;
        }
        run(Template.expand(command, project, session, true), path, label, actionContext.window());
    }
}
