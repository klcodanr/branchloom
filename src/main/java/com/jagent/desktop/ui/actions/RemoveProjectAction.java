package com.jagent.desktop.ui.actions;

import com.jagent.desktop.api.BaseAction;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import javax.swing.JOptionPane;

/** Removes the selected project from the application. */
public final class RemoveProjectAction extends BaseAction {
    public RemoveProjectAction(final ActionContext actionContext) {
        super(actionContext);
    }

    @Override
    public String id() {
        return "remove-project";
    }

    @Override
    public String label() {
        return "Remove project";
    }

    @Override
    public void execute() {
        final ProjectId projectId = actionContext.viewController().currentProjectId();
        final Project project =
                projectId == null ? null : actionContext.appState().projects().get(projectId);
        if (project == null
                || JOptionPane.showConfirmDialog(
                                actionContext.window(),
                                "Remove project '" + project.name() + "' from Branchloom?",
                                "Remove project",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.WARNING_MESSAGE)
                        != JOptionPane.YES_OPTION) {
            return;
        }
        actionContext.appState().removeProject(projectId);
    }
}
