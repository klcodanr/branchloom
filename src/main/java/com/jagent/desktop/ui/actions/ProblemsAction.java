package com.jagent.desktop.ui.actions;

import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.services.ViewController;

public final class ProblemsAction implements Action {
    private final ViewController viewController;

    @Inject
    public ProblemsAction(final ViewController viewController) {
        this.viewController = viewController;
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
        viewController.updateView(ViewId.PROBLEMS, null);
    }
}
