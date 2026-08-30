package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.services.ViewCoordinator.ViewState;

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
        return actionContext.appState().currentSessionId() != null;
    }

    @Override
    public void execute() {
        final var projectId = actionContext.appState().currentProjectId();
        final var sessionId = actionContext.appState().currentSessionId();
        if (projectId == null || sessionId == null) {
            return;
        }
        actionContext
                .viewCoordinator()
                .updateView(ViewId.SESSION, ViewState.session(projectId, sessionId));
    }
}
