package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;

public final class OpenProjectSettingsAction extends BaseAction {
    public OpenProjectSettingsAction(final ActionContext actionContext) {
        super(actionContext);
    }

    @Override
    public String id() {
        return "project-settings";
    }

    @Override
    public String label() {
        return "Settings";
    }

    @Override
    public void execute() {
        actionContext.viewController().updateView(ViewId.PROJECT_SETTINGS, null);
    }
}
