package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.models.ActionContext;

public final class OpenSessionAction extends BaseAction {
    public OpenSessionAction(final ActionContext actionContext) {
        super(actionContext);
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
        return actionContext.viewController().currentSessionId() != null;
    }

    @Override
    public void execute() {
        final var projectId = actionContext.viewController().currentProjectId();
        final var sessionId = actionContext.viewController().currentSessionId();
        if (projectId == null || sessionId == null) {
            return;
        }
        actionContext.viewController().openSession(projectId, sessionId);
    }
}
