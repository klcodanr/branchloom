package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.ui.components.Theme;
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
        final var result = actionContext.persistence().reloadSettings();
        if (result.hasError()) {
            ErrorDialogs.show(
                    actionContext.window(), "Settings could not be refreshed", result.error());
            return;
        }
        Theme.apply(Theme.FlatLafTheme.from(result.settings().theme()));
        actionContext
                .viewCoordinator()
                .updateView(actionContext.viewCoordinator().currentViewId(), null);
    }
}
