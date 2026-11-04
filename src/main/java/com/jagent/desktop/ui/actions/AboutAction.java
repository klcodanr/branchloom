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
                "Branchloom\n"
                        + "Git projects, worktrees, and agent sessions\n\n"
                        + "Java: "
                        + System.getProperty("java.version")
                        + " ("
                        + System.getProperty("java.vendor")
                        + ")\n"
                        + "OS: "
                        + System.getProperty("os.name")
                        + " "
                        + System.getProperty("os.version")
                        + " ("
                        + System.getProperty("os.arch")
                        + ")",
                "About Branchloom",
                DialogService.MessageType.INFORMATION);
    }
}
