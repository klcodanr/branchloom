package com.jagent.desktop.ui.actions;

import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.services.dialog.DialogService;

public final class AboutAction implements Action {
    private final DialogService dialogService;

    @Inject
    public AboutAction(final DialogService dialogService) {
        this.dialogService = dialogService;
    }

    @Override
    public String id() {
        return "about";
    }

    @Override
    public String label() {
        return "About Branchloom";
    }

    @Override
    public void execute() {
        dialogService.message(
                "Branchloom\nGit projects, worktrees, and agent sessions",
                "About Branchloom",
                DialogService.MessageType.INFORMATION);
    }
}
