package com.jagent.desktop.ui.actions;

import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.ui.utils.PathUtils;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/** Copies the current session worktree or project path. */
public final class CopyPathAction implements Action {
    private final ViewController viewController;
    private final AppState appState;

    @Inject
    public CopyPathAction(final ViewController viewController, final AppState appState) {
        this.viewController = viewController;
        this.appState = appState;
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
        return path() != null;
    }

    @Override
    public void execute() {
        copy(path());
    }

    private String path() {
        return PathUtils.resolve(appState, viewController);
    }
}
