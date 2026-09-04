package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.services.ViewCoordinator.ViewState;

public final class ProblemsAction extends BaseAction {
    public ProblemsAction(final ActionContext actionContext) {
        super(actionContext);
    }

    @Override
    public String id() {
        return "problems";
    }

    @Override
    public String label() {
        return "Problems";
    }

    @Override
    public void execute() {
        actionContext.viewCoordinator().updateView(ViewId.PROBLEMS, ViewState.reset());
    }
}
