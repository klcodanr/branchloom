package com.jagent.desktop.ui.components;

import com.jagent.desktop.api.Action;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Tool;
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
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;

/** Project context-menu construction. */
public final class ProjectActions {
    private ProjectActions() {}

    public static void show(
            final ActionContext actionContext,
            final ProjectId projectId,
            final Component invoker,
            final Point point) {
        UiFactory.showPopupMenu(menu(actionContext, projectId), invoker, point.x, point.y);
    }

    public static JPopupMenu menu(final ActionContext actionContext, final ProjectId projectId) {
        final JPopupMenu menu = new JPopupMenu();
        populate(menu, actionContext, projectId);
        return menu;
    }

    public static void populate(
            final Container menu, final ActionContext actionContext, final ProjectId projectId) {
        actionContext.appState().updateCurrentProject(projectId);

        final var createSession = new CreateSessionAction(actionContext);
        menu.add(
                projectActionItem(actionContext, projectId, createSession, UiIcons.hatGlasses()),
                null);
        menu.add(
                projectActionItem(
                        actionContext,
                        projectId,
                        new CreateTerminalAction(actionContext),
                        UiIcons.terminal()));
        menu.add(new JSeparator());
        menu.add(
                projectActionItem(
                        actionContext, projectId, new OpenDirectoryAction(actionContext), null));
        menu.add(
                projectActionItem(
                        actionContext, projectId, new CopyPathAction(actionContext), null));
        menu.add(
                projectActionItem(
                        actionContext, projectId, new UpdateBranchAction(actionContext), null));
        final JMenu importFrom = new JMenu("Import from");
        importFrom.add(
                projectActionItem(
                        actionContext, projectId, new ImportBranchAction(actionContext), null));
        importFrom.add(
                projectActionItem(
                        actionContext, projectId, new ImportWorktreeAction(actionContext), null));
        importFrom.add(
                projectActionItem(
                        actionContext,
                        projectId,
                        new BulkCreateSessionsAction(actionContext),
                        null));
        importFrom.add(
                projectActionItem(
                        actionContext, projectId, new PasteSessionsAction(actionContext), null));
        menu.add(importFrom);

        addAgents(menu, actionContext, projectId);
        addEditors(menu, actionContext, projectId);
        menu.add(new JSeparator());
        menu.add(
                projectActionItem(
                        actionContext,
                        projectId,
                        new OpenProjectSettingsAction(actionContext),
                        null));
        menu.add(new JSeparator());
        final JMenuItem removeProject =
                projectActionItem(
                        actionContext, projectId, new RemoveProjectAction(actionContext), null);
        removeProject.setForeground(Theme.Colors.danger());
        menu.add(removeProject);
    }

    private static void addAgents(
            final Container menu, final ActionContext actionContext, final ProjectId projectId) {
        boolean added = false;
        for (final Agent agent : actionContext.appState().appSettings().agents()) {
            if (!added) {
                addSectionHeading(menu, "Agents");
                added = true;
            }
            menu.add(
                    projectActionItem(
                            actionContext,
                            projectId,
                            new CreateTerminalAction(actionContext, agent.name, agent.openCommand),
                            null));
        }
    }

    private static void addEditors(
            final Container menu, final ActionContext actionContext, final ProjectId projectId) {
        boolean added = false;
        for (final Tool editor : actionContext.appState().appSettings().tools()) {
            if (!added) {
                addSectionHeading(menu, "Editors");
                added = true;
            }
            menu.add(
                    projectActionItem(
                            actionContext,
                            projectId,
                            new RunCommandAction(actionContext, editor.label(), editor.command()),
                            null));
        }
    }

    private static void addSectionHeading(final Container menu, final String label) {
        menu.add(new JSeparator());
        final JMenuItem heading = new JMenuItem(label);
        heading.setEnabled(false);
        menu.add(heading);
    }

    private static JMenuItem projectActionItem(
            final ActionContext actionContext,
            final ProjectId projectId,
            final Action action,
            final javax.swing.Icon icon) {
        final JMenuItem item = new JMenuItem(action.label(), icon);
        item.setEnabled(action.enabled());
        item.addActionListener(
                event -> {
                    actionContext.appState().updateCurrentProject(projectId);
                    actionContext.appState().updateCurrentSession(null);
                    action.execute();
                });
        return item;
    }
}
