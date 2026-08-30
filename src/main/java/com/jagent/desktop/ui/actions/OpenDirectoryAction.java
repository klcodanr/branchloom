package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import java.awt.Component;
import java.awt.Desktop;
import java.nio.file.Path;
import javax.swing.JOptionPane;

/** Opens the current session worktree or project in the file manager. */
public final class OpenDirectoryAction extends BaseAction {
    public OpenDirectoryAction(final ActionContext actionContext) {
        super(actionContext);
    }

    public static void open(final String path, final Component owner) {
        if (!Desktop.isDesktopSupported()) {
            JOptionPane.showMessageDialog(
                    owner,
                    "Could not open the file manager.",
                    "Open directory",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().open(Path.of(path).toFile());
        } catch (Exception exception) {
            JOptionPane.showMessageDialog(
                    owner, exception.getMessage(), "Open directory", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public String id() {
        return "open-directory";
    }

    @Override
    public String label() {
        return "Open in file manager";
    }

    @Override
    public boolean enabled() {
        return currentPath() != null;
    }

    @Override
    public void execute() {
        open(currentPath(), actionContext.window());
    }

    private String currentPath() {
        final Session session =
                actionContext.appState().currentSessionId() == null
                        ? null
                        : actionContext
                                .appState()
                                .sessions()
                                .get(actionContext.appState().currentSessionId());
        if (session != null && session.worktreePath() != null) {
            return session.worktreePath();
        }
        final Project project =
                actionContext.appState().currentProjectId() == null
                        ? null
                        : actionContext
                                .appState()
                                .projects()
                                .get(actionContext.appState().currentProjectId());
        return project == null ? null : project.path();
    }
}
