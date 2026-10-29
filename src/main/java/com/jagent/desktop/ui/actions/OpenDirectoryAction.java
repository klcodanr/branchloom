package com.jagent.desktop.ui.actions;

import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.ui.utils.PathUtils;

/** Opens the current session worktree or project in the file manager. */
public final class OpenDirectoryAction implements Action {
    private final AppState appState;
    private final ViewController viewController;

    @Inject
    public OpenDirectoryAction(final AppState appState, final ViewController viewController) {
        this.appState = appState;
        this.viewController = viewController;
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
        return true;
    }

    @Override
    public void execute() {
        PlatformCommands.openPath(PathUtils.resolve(this.appState, this.viewController));
    }
}
