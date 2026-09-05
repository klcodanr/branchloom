package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.ui.utils.CurrentPath;
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
        return CurrentPath.resolve(actionContext.appState()) != null;
    }

    @Override
    public void execute() {
        open(CurrentPath.resolve(actionContext.appState()), actionContext.window());
    }
}
