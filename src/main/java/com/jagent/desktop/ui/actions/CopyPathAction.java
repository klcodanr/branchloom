package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.ui.utils.CurrentPath;
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
        return CurrentPath.resolve(actionContext.appState()) != null;
    }

    @Override
    public void execute() {
        copy(CurrentPath.resolve(actionContext.appState()));
    }
}
