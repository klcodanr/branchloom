package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;

public final class ResourceUsageAction extends BaseAction {
    public ResourceUsageAction(final ActionContext actionContext) {
        super(actionContext);
    }

    @Override
    public String id() {
        return "resource-usage";
    }

    @Override
    public String label() {
        return "Resource Usage";
    }

    @Override
    public void execute() {
        actionContext.viewCoordinator().updateView(ViewId.RESOURCE_USAGE, null);
    }
}
