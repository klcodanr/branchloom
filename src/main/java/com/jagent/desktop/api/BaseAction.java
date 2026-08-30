package com.jagent.desktop.api;

import com.jagent.desktop.models.ActionContext;

public abstract class BaseAction implements Action {
    protected final ActionContext actionContext;

    protected BaseAction(final ActionContext actionContext) {
        this.actionContext = actionContext;
    }
}
