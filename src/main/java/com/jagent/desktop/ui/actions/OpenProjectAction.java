package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.services.ViewCoordinator.ViewState;

/** Opens the selected project. */
public final class OpenProjectAction extends BaseAction {
    public OpenProjectAction(final ActionContext actionContext) {
        super(actionContext);
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
        return actionContext.appState().currentProjectId() != null;
    }

    @Override
    public void execute() {
        final var projectId = actionContext.appState().currentProjectId();
        if (projectId == null) {
            return;
        }
        actionContext.viewCoordinator().updateView(ViewId.PROJECT, ViewState.project(projectId));
    }
}
