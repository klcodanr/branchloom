package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.models.ActionContext;
import javax.swing.JOptionPane;

public final class AboutAction extends BaseAction {

    public AboutAction(final ActionContext actionContext) {
        super(actionContext);
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
        JOptionPane.showMessageDialog(
                actionContext.window(),
                "Branchloom\nGit projects, worktrees, and agent sessions",
                "About Branchloom",
                JOptionPane.INFORMATION_MESSAGE);
    }
}
