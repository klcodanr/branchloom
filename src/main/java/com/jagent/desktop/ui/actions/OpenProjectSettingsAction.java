package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.Action;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.services.ViewController;

public final class OpenProjectSettingsAction implements Action {
    private final ViewController viewController;

    public OpenProjectSettingsAction(final ViewController viewController) {
        this.viewController = viewController;
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
        viewController.updateView(ViewId.PROJECT_SETTINGS, null);
    }
}
