package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.ui.utils.ErrorDialogs;

public final class RefreshSettingsAction extends BaseAction {
    public RefreshSettingsAction(final ActionContext actionContext) {
        super(actionContext);
    }

    @Override
    public String id() {
        return "refresh-settings";
    }

    @Override
    public String label() {
        return "Refresh Settings";
    }

    @Override
    public void execute() {
        try {
            actionContext.refreshSettings().run();
        } catch (IllegalStateException exception) {
            ErrorDialogs.show(
                    actionContext.window(),
                    "Settings could not be refreshed",
                    exception.getMessage());
        }
    }
}
