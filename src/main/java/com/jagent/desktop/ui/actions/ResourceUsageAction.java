package com.jagent.desktop.ui.actions;

import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.services.ViewController;

public final class ResourceUsageAction implements Action {
    private final ViewController viewController;

    @Inject
    public ResourceUsageAction(final ViewController viewController) {
        this.viewController = viewController;
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
        viewController.updateView(ViewId.RESOURCE_USAGE, null);
    }
}
