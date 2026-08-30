package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/** Copies the current session worktree or project path. */
public final class CopyPathAction extends BaseAction {
    public CopyPathAction(final ActionContext actionContext) {
        super(actionContext);
    }

    public static void copy(final String path) {
        Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(path), null);
    }

    @Override
    public String id() {
        return "copy-path";
    }

    @Override
    public String label() {
        return "Copy path";
    }

    @Override
    public boolean enabled() {
        return currentPath() != null;
    }

    @Override
    public void execute() {
        copy(currentPath());
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
