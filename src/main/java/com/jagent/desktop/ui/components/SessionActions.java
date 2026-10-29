package com.jagent.desktop.ui.components;

import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.Tool;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.ui.actions.CopyBranchAction;
import com.jagent.desktop.ui.actions.CopyPathAction;
import com.jagent.desktop.ui.actions.CreateTerminalAction;
import com.jagent.desktop.ui.actions.OpenDirectoryAction;
import com.jagent.desktop.ui.actions.RemoveSessionAction;
import com.jagent.desktop.ui.actions.RenameSessionAction;
import com.jagent.desktop.ui.actions.RunCommandAction;
import com.jagent.desktop.ui.actions.UpdateBranchAction;
import java.awt.Container;
import javax.swing.Icon;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;

/** Builds actions for the selected session. */
public final class SessionActions {
    private final AppState appState;
    private final ViewController viewController;
    private final DialogService dialogService;
    private final CreateTerminalAction createTerminalAction;
    private final OpenDirectoryAction openDirectoryAction;
    private final CopyPathAction copyPathAction;
    private final CopyBranchAction copyBranchAction;
    private final UpdateBranchAction updateBranchAction;
    private final RenameSessionAction renameSessionAction;
    private final RemoveSessionAction removeSessionAction;

    @Inject
    private SessionActions(
            final AppState appState,
            final ViewController viewController,
            final DialogService dialogService,
            final CreateTerminalAction createTerminalAction,
            final OpenDirectoryAction openDirectoryAction,
            final CopyPathAction copyPathAction,
            final CopyBranchAction copyBranchAction,
            final UpdateBranchAction updateBranchAction,
            final RenameSessionAction renameSessionAction,
            final RemoveSessionAction removeSessionAction) {
        this.appState = appState;
        this.viewController = viewController;
        this.dialogService = dialogService;
        this.createTerminalAction = createTerminalAction;
        this.openDirectoryAction = openDirectoryAction;
        this.copyPathAction = copyPathAction;
        this.copyBranchAction = copyBranchAction;
        this.updateBranchAction = updateBranchAction;
        this.renameSessionAction = renameSessionAction;
        this.removeSessionAction = removeSessionAction;
    }

    public JPopupMenu menu(final SessionId sessionId) {
        selectSession(sessionId);
        final JPopupMenu menu = new JPopupMenu();
        populate(menu);
        return menu;
    }

    public void populate(final Container menu) {
        menu.add(sessionActionItem(createTerminalAction, UiIcons.terminal()));
        menu.add(new JSeparator());
        menu.add(sessionActionItem(openDirectoryAction));
        menu.add(sessionActionItem(copyPathAction));
        menu.add(sessionActionItem(copyBranchAction));
        menu.add(sessionActionItem(updateBranchAction));

        addAgents(menu);
        addEditors(menu);
        menu.add(new JSeparator());
        menu.add(sessionActionItem(renameSessionAction));
        final JMenuItem removeSession = sessionActionItem(removeSessionAction);
        removeSession.setForeground(Theme.Colors.danger());
        menu.add(removeSession);
    }

    public void populate(final Container menu, final SessionId sessionId) {
        selectSession(sessionId);
        populate(menu);
    }

    private void addAgents(final Container menu) {
        boolean added = false;
        for (final Agent agent : appState.appSettings().agents()) {
            if (!added) {
                addSectionHeading(menu, "Agents");
                added = true;
            }
            menu.add(
                    sessionActionItem(
                            new CreateTerminalAction(
                                    viewController, appState, agent.name, agent.openCommand)));
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
                    sessionActionItem(
                            new RunCommandAction(
                                    viewController,
                                    appState,
                                    dialogService,
                                    editor.label(),
                                    editor.command())));
        }
    }

    private static void addSectionHeading(final Container menu, final String label) {
        menu.add(new JSeparator());
        final JMenuItem heading = new JMenuItem(label);
        heading.setEnabled(false);
        menu.add(heading);
    }

    private JMenuItem sessionActionItem(final Action action) {
        return sessionActionItem(action, null);
    }

    private JMenuItem sessionActionItem(final Action action, final Icon icon) {
        final JMenuItem item = new JMenuItem(action.label(), icon);
        item.setEnabled(action.enabled());
        item.addActionListener(
                event -> {
                    selectSession(viewController.currentSessionId());
                    action.execute();
                });
        return item;
    }

    private void selectSession(final SessionId sessionId) {
        final var session = appState.sessions().get(sessionId);
        if (session != null) {
            viewController.selectSession(session.projectId(), sessionId);
        }
    }
}
