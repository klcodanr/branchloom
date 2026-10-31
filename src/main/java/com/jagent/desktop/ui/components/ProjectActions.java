package com.jagent.desktop.ui.components;

import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Tool;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.ui.actions.BulkCreateSessionsAction;
import com.jagent.desktop.ui.actions.CopyPathAction;
import com.jagent.desktop.ui.actions.CreateSessionAction;
import com.jagent.desktop.ui.actions.CreateTerminalAction;
import com.jagent.desktop.ui.actions.ImportBranchAction;
import com.jagent.desktop.ui.actions.ImportWorktreeAction;
import com.jagent.desktop.ui.actions.OpenDirectoryAction;
import com.jagent.desktop.ui.actions.OpenProjectSettingsAction;
import com.jagent.desktop.ui.actions.PasteSessionsAction;
import com.jagent.desktop.ui.actions.RemoveProjectAction;
import com.jagent.desktop.ui.actions.RunCommandAction;
import com.jagent.desktop.ui.actions.UpdateBranchAction;
import java.awt.Container;
import javax.swing.Icon;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;

/**
 * Project viewController, appState, window, dataDirectory, refreshSettings, githubProvider,
 * dialogService-menu construction.
 */
public final class ProjectActions {

    private final AppState appState;
    private final DialogService dialogService;
    private final ViewController viewController;

    private final CreateSessionAction createSessionAction;
    private final CreateTerminalAction createTerminalAction;
    private final OpenDirectoryAction openDirectoryAction;
    private final CopyPathAction copyPathAction;
    private final UpdateBranchAction updateBranchAction;
    private final ImportBranchAction importBranchAction;
    private final ImportWorktreeAction importWorktreeAction;
    private final BulkCreateSessionsAction bulkCreateSessionsAction;
    private final PasteSessionsAction pasteSessionsAction;
    private final RemoveProjectAction removeProjectAction;

    @Inject
    private ProjectActions(
            final AppState appState,
            final ViewController viewController,
            final CreateSessionAction createSessionAction,
            final CreateTerminalAction createTerminalAction,
            final OpenDirectoryAction openDirectoryAction,
            final CopyPathAction copyPathAction,
            final UpdateBranchAction updateBranchAction,
            final ImportBranchAction importBranchAction,
            final ImportWorktreeAction importWorktreeAction,
            final BulkCreateSessionsAction bulkCreateSessionsAction,
            final PasteSessionsAction pasteSessionsAction,
            final DialogService dialogService,
            final RemoveProjectAction removeProjectAction) {
        this.appState = appState;
        this.dialogService = dialogService;
        this.viewController = viewController;
        this.createSessionAction = createSessionAction;
        this.createTerminalAction = createTerminalAction;
        this.openDirectoryAction = openDirectoryAction;
        this.copyPathAction = copyPathAction;
        this.updateBranchAction = updateBranchAction;
        this.importBranchAction = importBranchAction;
        this.importWorktreeAction = importWorktreeAction;
        this.bulkCreateSessionsAction = bulkCreateSessionsAction;
        this.pasteSessionsAction = pasteSessionsAction;
        this.removeProjectAction = removeProjectAction;
    }

    public JPopupMenu menu(final ProjectId projectId) {
        viewController.selectProject(projectId);
        final JPopupMenu menu = new JPopupMenu();
        populate(menu);

        return menu;
    }

    public void populate(final Container menu) {
        menu.add(projectActionItem(this.createSessionAction, UiIcons.hatGlasses()));
        menu.add(projectActionItem(this.createTerminalAction, UiIcons.terminal()));
        menu.add(new JSeparator());
        menu.add(projectActionItem(this.openDirectoryAction, null));
        menu.add(projectActionItem(this.copyPathAction, null));
        menu.add(projectActionItem(this.updateBranchAction, null));
        final JMenu importFrom = new JMenu("Import from");
        importFrom.add(projectActionItem(this.importBranchAction, null));
        importFrom.add(projectActionItem(this.importWorktreeAction, null));
        importFrom.add(projectActionItem(bulkCreateSessionsAction, null));
        importFrom.add(projectActionItem(pasteSessionsAction, null));
        menu.add(importFrom);

        addAgents(menu);
        addEditors(menu);
        menu.add(new JSeparator());
        menu.add(projectActionItem(new OpenProjectSettingsAction(viewController), null));
        menu.add(new JSeparator());
        final JMenuItem removeProject = projectActionItem(removeProjectAction, null);
        removeProject.setForeground(Theme.Colors.danger());
        menu.add(removeProject);
    }

    private void addAgents(final Container menu) {
        boolean added = false;
        for (final Agent agent : appState.appSettings().agents()) {
            if (!added) {
                addSectionHeading(menu, "Agents");
                added = true;
            }
            menu.add(
                    projectActionItem(
                            new CreateTerminalAction(
                                    viewController, appState, agent.name, agent.openCommand),
                            null));
        }
    }

    private void addEditors(final Container menu) {
        boolean added = false;
        for (final Tool editor : appState.appSettings().tools()) {
            if (!added) {
                addSectionHeading(menu, "Editors");
                added = true;
            }
            menu.add(
                    projectActionItem(
                            new RunCommandAction(
                                    viewController,
                                    appState,
                                    dialogService,
                                    editor.label(),
                                    editor.command()),
                            null));
        }
    }

    private static void addSectionHeading(final Container menu, final String label) {
        menu.add(new JSeparator());
        final JMenuItem heading = new JMenuItem(label);
        heading.setEnabled(false);
        menu.add(heading);
    }

    private static JMenuItem projectActionItem(final Action action, final Icon icon) {
        final JMenuItem item = new JMenuItem(action.label(), icon);
        item.setEnabled(action.enabled());
        item.addActionListener(event -> action.execute());
        return item;
    }
}
