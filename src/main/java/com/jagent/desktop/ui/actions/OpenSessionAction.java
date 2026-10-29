package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.Action;
import com.jagent.desktop.services.ViewController;

public final class OpenSessionAction implements Action {
    private final ViewController viewController;

    public OpenSessionAction(final ViewController viewController) {
        this.viewController = viewController;
    }

    @Override
    public String id() {
        return "open-session";
    }

    @Override
    public String label() {
        return "Open session";
    }

    @Override
    public boolean enabled() {
        return viewController.currentSessionId() != null;
    }

    @Override
    public void execute() {
        final var projectId = viewController.currentProjectId();
        final var sessionId = viewController.currentSessionId();
        if (projectId == null || sessionId == null) {
            return;
        }
        viewController.openSession(projectId, sessionId);
    }
}
