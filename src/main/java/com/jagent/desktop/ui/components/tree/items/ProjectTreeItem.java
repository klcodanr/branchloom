package com.jagent.desktop.ui.components.tree.items;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.ui.components.ProjectActions;
import com.jagent.desktop.ui.components.UiText;
import javax.swing.JPopupMenu;

public class ProjectTreeItem implements TreeItem {
    private final ViewController viewController;
    private final AppState appState;
    private final ProjectActions projectActions;
    private final ProjectId projectId;

    public ProjectTreeItem(
            final ViewController viewController,
            final AppState appState,
            final ProjectActions projectActions,
            final ProjectId projectId) {
        this.projectId = projectId;
        this.viewController = viewController;
        this.appState = appState;
        this.projectActions = projectActions;
    }

    @Override
    public String getId() {
        return projectId.toString();
    }

    public Project getProject() {
        return this.appState.projects().get(this.projectId);
    }

    public ProjectId getProjectId() {
        return projectId;
    }

    @Override
    public String getTooltip() {
        final Project project = getProject();
        return "<html><b>"
                + UiText.escapeHtml(project.name())
                + "</b><br>"
                + UiText.escapeHtml(project.path())
                + "<br>"
                + project.sessionIds().size()
                + (project.sessionIds().size() == 1 ? " session" : " sessions")
                + "</html>";
    }

    @Override
    public CellRenderContents getCellRenderContents() {
        final Project project = getProject();
        return new CellRenderContents(project.name(), null);
    }

    @Override
    public JPopupMenu getMenu() {
        return projectActions.menu(this.projectId);
    }

    @Override
    public void select() {
        viewController.openProject(projectId);
    }
}
