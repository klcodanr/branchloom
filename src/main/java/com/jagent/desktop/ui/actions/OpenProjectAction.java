package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.Action;
import com.jagent.desktop.services.ViewController;

/** Opens the selected project. */
public final class OpenProjectAction implements Action {
    private final ViewController viewController;

    public OpenProjectAction(final ViewController viewController) {
        this.viewController = viewController;
    }

    @Override
    public String id() {
        return "open-project";
    }

    @Override
    public String label() {
        return "Open project view";
    }

    @Override
    public boolean enabled() {
        return viewController.currentProjectId() != null;
    }

    @Override
    public void execute() {
        final var projectId = viewController.currentProjectId();
        if (projectId == null) {
            return;
        }
        viewController.openProject(projectId);
    }
}
