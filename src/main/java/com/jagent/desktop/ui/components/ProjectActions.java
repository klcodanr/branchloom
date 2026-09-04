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
        actionContext.appState().updateCurrentProject(projectId);
        actionContext.appState().updateCurrentSession(null);
        menu(actionContext, projectId).show(invoker, point.x, point.y);
    }

    public static JPopupMenu menu(final ActionContext actionContext, final ProjectId projectId) {
        return menu(actionContext, projectId, null);
    }

    public static JPopupMenu menu(
            final ActionContext actionContext,
            final ProjectId projectId,
            final Runnable createTerminal) {
        final JPopupMenu menu = new JPopupMenu();
        populate(menu, actionContext, projectId, createTerminal);
        return menu;
    }

    public static void populate(
            final Container menu, final ActionContext actionContext, final ProjectId projectId) {
        populate(menu, actionContext, projectId, null);
    }

    public static void populate(
            final Container menu,
            final ActionContext actionContext,
            final ProjectId projectId,
            final Runnable createTerminal) {
        menu.add(
                projectActionItem(
                        actionContext,
                        projectId,
                        new CreateSessionAction(actionContext),
                        UiIcons.hatGlasses()));
        menu.add(
                projectActionItem(
                        actionContext, projectId, new BulkCreateSessionsAction(actionContext)));
        menu.add(
                projectActionItem(
                        actionContext,
                        projectId,
                        new CreateTerminalAction(actionContext),
                        UiIcons.terminal()));
        menu.add(new JSeparator());
        menu.add(
                projectActionItem(
                        actionContext, projectId, new OpenDirectoryAction(actionContext)));
        menu.add(projectActionItem(actionContext, projectId, new CopyPathAction(actionContext)));
        menu.add(
                projectActionItem(actionContext, projectId, new UpdateBranchAction(actionContext)));
        menu.add(
                projectActionItem(actionContext, projectId, new ImportBranchAction(actionContext)));
        menu.add(
                projectActionItem(
                        actionContext, projectId, new ImportWorktreeAction(actionContext)));

        addAgents(menu, actionContext, projectId);
        addEditors(menu, actionContext, projectId);
        menu.add(new JSeparator());
        menu.add(
                projectActionItem(
                        actionContext, projectId, new OpenProjectSettingsAction(actionContext)));
        menu.add(new JSeparator());
        final JMenuItem removeProject =
                projectActionItem(actionContext, projectId, new RemoveProjectAction(actionContext));
        removeProject.setForeground(Theme.dangerColor());
        menu.add(removeProject);
    }

    private static void addAgents(
            final Container menu, final ActionContext actionContext, final ProjectId projectId) {
        final JMenu agents = new JMenu("Agents");
        for (final Agent agent : actionContext.appState().appSettings().agents()) {
            agents.add(
                    projectActionItem(
                            actionContext,
                            projectId,
                            new CreateTerminalAction(
                                    actionContext, agent.name, agent.openCommand)));
        }
        if (agents.getItemCount() > 0) {
            menu.add(agents);
        }
    }

    private static void addEditors(
            final Container menu, final ActionContext actionContext, final ProjectId projectId) {
        final JMenu editors = new JMenu("Editors");
        for (final Tool editor : actionContext.appState().appSettings().tools()) {
            editors.add(
                    projectActionItem(
                            actionContext,
                            projectId,
                            new RunCommandAction(actionContext, editor.label(), editor.command())));
        }
        if (editors.getItemCount() > 0) {
            menu.add(editors);
        }
    }

    private static JMenuItem projectActionItem(
            final ActionContext actionContext, final ProjectId projectId, final Action action) {
        return projectActionItem(actionContext, projectId, action, null);
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

    public record MenuAction(String label, Runnable action) {}
}
