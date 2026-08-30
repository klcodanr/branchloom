package com.jagent.desktop.ui.components;

import com.jagent.desktop.api.Action;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.Tool;
import com.jagent.desktop.ui.actions.CopyBranchAction;
import com.jagent.desktop.ui.actions.CopyPathAction;
import com.jagent.desktop.ui.actions.CreateTerminalAction;
import com.jagent.desktop.ui.actions.OpenDirectoryAction;
import com.jagent.desktop.ui.actions.RemoveSessionAction;
import com.jagent.desktop.ui.actions.RenameSessionAction;
import com.jagent.desktop.ui.actions.RunCommandAction;
import java.awt.Container;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;

/** Session context-menu construction. */
public final class SessionActions {
    private SessionActions() {}

    public static JPopupMenu menu(final ActionContext actionContext, final SessionId sessionId) {
        return menu(actionContext, sessionId, null);
    }

    public static JPopupMenu menu(
            final ActionContext actionContext,
            final SessionId sessionId,
            final Runnable createTerminal) {
        final JPopupMenu menu = new JPopupMenu();
        populate(menu, actionContext, sessionId, createTerminal);
        return menu;
    }

    public static void populate(
            final Container menu,
            final ActionContext actionContext,
            final SessionId sessionId,
            final Runnable createTerminal) {
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

        addAgents(menu, actionContext, sessionId);
        addEditors(menu, actionContext, sessionId);
        menu.add(new JSeparator());
        menu.add(
                sessionActionItem(
                        actionContext, sessionId, new RenameSessionAction(actionContext)));
        final JMenuItem removeSession =
                sessionActionItem(actionContext, sessionId, new RemoveSessionAction(actionContext));
        removeSession.setForeground(Theme.dangerColor());
        menu.add(removeSession);
    }

    private static void addAgents(
            final Container menu, final ActionContext actionContext, final SessionId sessionId) {
        final JMenu agents = new JMenu("Agents");
        for (final Agent agent : actionContext.appState().appSettings().agents()) {
            agents.add(
                    sessionActionItem(
                            actionContext,
                            sessionId,
                            new CreateTerminalAction(
                                    actionContext, agent.name, agent.openCommand)));
        }
        if (agents.getItemCount() > 0) {
            menu.add(agents);
        }
    }

    private static void addEditors(
            final Container menu, final ActionContext actionContext, final SessionId sessionId) {
        final JMenu editors = new JMenu("Editors");
        for (final Tool editor : actionContext.appState().appSettings().tools()) {
            editors.add(
                    sessionActionItem(
                            actionContext,
                            sessionId,
                            new RunCommandAction(actionContext, editor.label(), editor.command())));
        }
        if (editors.getItemCount() > 0) {
            menu.add(editors);
        }
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
                    actionContext.appState().updateCurrentSession(sessionId);
                    action.execute();
                });
        return item;
    }
}
