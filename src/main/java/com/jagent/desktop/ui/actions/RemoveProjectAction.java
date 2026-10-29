package com.jagent.desktop.ui.actions;

import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;

/** Removes the selected project from the application. */
public final class RemoveProjectAction implements Action {
    private final ViewController viewController;
    private final AppState appState;
    private final DialogService dialogService;

    @Inject
    public RemoveProjectAction(
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService) {
        this.viewController = viewController;
        this.appState = appState;
        this.dialogService = dialogService;
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
        final ProjectId projectId = viewController.currentProjectId();
        final Project project = projectId == null ? null : appState.projects().get(projectId);
        if (project == null
                || dialogService.confirm(
                                "Remove project '" + project.name() + "' from Branchloom?",
                                "Remove project",
                                DialogService.OptionType.YES_NO,
                                DialogService.MessageType.WARNING)
                        != DialogService.ReturnType.OK) {
            return;
        }
        appState.removeProject(projectId);
    }
}
