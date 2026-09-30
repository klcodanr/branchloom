package com.jagent.desktop.ui.components;

import com.jagent.desktop.api.Action;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.Tool;
import com.jagent.desktop.ui.actions.CopyBranchAction;
import com.jagent.desktop.ui.actions.CopyPathAction;
import com.jagent.desktop.ui.actions.CreateTerminalAction;
import com.jagent.desktop.ui.actions.OpenDirectoryAction;
import com.jagent.desktop.ui.actions.RemoveSessionAction;
import com.jagent.desktop.ui.actions.RenameSessionAction;
import com.jagent.desktop.ui.actions.RunCommandAction;
import com.jagent.desktop.ui.actions.UpdateBranchAction;
import java.awt.Container;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;

/** Session context-menu construction. */
public final class SessionActions {
    private SessionActions() {}

    public static JPopupMenu menu(final ActionContext actionContext, final SessionId sessionId) {
        final JPopupMenu menu = new JPopupMenu();
        populate(menu, actionContext, sessionId);
        return menu;
    }

    public static void populate(
            final Container menu, final ActionContext actionContext, final SessionId sessionId) {
        selectSession(actionContext, sessionId);
        menu.add(
                sessionActionItem(
                        actionContext,
                        sessionId,
                        new CreateTerminalAction(actionContext),
                        UiIcons.terminal()));
        menu.add(new JSeparator());
        menu.add(
                sessionActionItem(
                        actionContext, sessionId, new OpenDirectoryAction(actionContext)));
        menu.add(sessionActionItem(actionContext, sessionId, new CopyPathAction(actionContext)));
        menu.add(sessionActionItem(actionContext, sessionId, new CopyBranchAction(actionContext)));
        menu.add(
                sessionActionItem(actionContext, sessionId, new UpdateBranchAction(actionContext)));

        addAgents(menu, actionContext, sessionId);
        addEditors(menu, actionContext, sessionId);
        menu.add(new JSeparator());
        menu.add(
                sessionActionItem(
                        actionContext, sessionId, new RenameSessionAction(actionContext)));
        final JMenuItem removeSession =
                sessionActionItem(actionContext, sessionId, new RemoveSessionAction(actionContext));
        removeSession.setForeground(Theme.Colors.danger());
        menu.add(removeSession);
    }

    private static void addAgents(
            final Container menu, final ActionContext actionContext, final SessionId sessionId) {
        boolean added = false;
        for (final Agent agent : actionContext.appState().appSettings().agents()) {
            if (!added) {
                addSectionHeading(menu, "Agents");
                added = true;
            }
            menu.add(
                    sessionActionItem(
                            actionContext,
                            sessionId,
                            new CreateTerminalAction(
                                    actionContext, agent.name, agent.openCommand)));
        }
    }

    private static void addEditors(
            final Container menu, final ActionContext actionContext, final SessionId sessionId) {
        boolean added = false;
        for (final Tool editor : actionContext.appState().appSettings().tools()) {
            if (!added) {
                addSectionHeading(menu, "Editors");
                added = true;
            }
            menu.add(
                    sessionActionItem(
                            actionContext,
                            sessionId,
                            new RunCommandAction(actionContext, editor.label(), editor.command())));
        }
    }

    private static void addSectionHeading(final Container menu, final String label) {
        menu.add(new JSeparator());
        final JMenuItem heading = new JMenuItem(label);
        heading.setEnabled(false);
        menu.add(heading);
    }

    private static JMenuItem sessionActionItem(
            final ActionContext actionContext, final SessionId sessionId, final Action action) {
        return sessionActionItem(actionContext, sessionId, action, null);
    }

    private static JMenuItem sessionActionItem(
            final ActionContext actionContext,
            final SessionId sessionId,
            final Action action,
            final javax.swing.Icon icon) {
        final JMenuItem item = new JMenuItem(action.label(), icon);
        item.setEnabled(action.enabled());
        item.addActionListener(
                event -> {
                    selectSession(actionContext, sessionId);
                    action.execute();
                });
        return item;
    }

    private static void selectSession(
            final ActionContext actionContext, final SessionId sessionId) {
        final Session session = actionContext.appState().sessions().get(sessionId);
        if (session != null) {
            actionContext.appState().updateCurrentProject(session.projectId());
        }
        actionContext.appState().updateCurrentSession(sessionId);
    }
}
