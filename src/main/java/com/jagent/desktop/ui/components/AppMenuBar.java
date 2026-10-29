package com.jagent.desktop.ui.components;

import com.formdev.flatlaf.util.SystemInfo;
import com.google.inject.Inject;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.ui.actions.AboutAction;
import com.jagent.desktop.ui.actions.CreateProjectAction;
import com.jagent.desktop.ui.actions.CreateSessionAction;
import com.jagent.desktop.ui.actions.CreateTerminalAction;
import com.jagent.desktop.ui.actions.FindAction;
import com.jagent.desktop.ui.actions.ImportProjectAction;
import com.jagent.desktop.ui.actions.OpenSettingsAction;
import com.jagent.desktop.ui.actions.ProblemsAction;
import com.jagent.desktop.ui.actions.RefreshSettingsAction;
import com.jagent.desktop.ui.actions.ResourceUsageAction;
import com.jagent.desktop.ui.actions.ShortcutsAction;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

/** Builds the application menu from the current application state. */
public final class AppMenuBar {
    private final ViewController viewController;
    private final AppState appState;
    private final Action aboutAction;
    private final Action createProjectAction;
    private final Action createSessionAction;
    private final Action createTerminalAction;
    private final Action findAction;
    private final Action importProjectAction;
    private final Action openSettingsAction;
    private final Action problemsAction;
    private final Action refreshSettingsAction;
    private final Action resourceUsageAction;
    private final Action shortcutsAction;
    private final ProjectActions projectActions;
    private final SessionActions sessionActions;
    private final Window window;
    private final JMenuBar menuBar = new JMenuBar();

    @Inject
    @SuppressWarnings("PMD.ExcessiveParameterList")
    public AppMenuBar(
            final ViewController viewController,
            final AppState appState,
            final ProjectActions projectActions,
            final SessionActions sessionActions,
            final AboutAction aboutAction,
            final CreateProjectAction createProjectAction,
            final CreateSessionAction createSessionAction,
            final CreateTerminalAction createTerminalAction,
            final FindAction findAction,
            final ImportProjectAction importProjectAction,
            final OpenSettingsAction openSettingsAction,
            final ProblemsAction problemsAction,
            final RefreshSettingsAction refreshSettingsAction,
            final ResourceUsageAction resourceUsageAction,
            final ShortcutsAction shortcutsAction,
            final Window window) {
        this.viewController = viewController;
        this.appState = appState;
        this.projectActions = projectActions;
        this.sessionActions = sessionActions;
        this.aboutAction = aboutAction;
        this.createProjectAction = createProjectAction;
        this.createSessionAction = createSessionAction;
        this.createTerminalAction = createTerminalAction;
        this.findAction = findAction;
        this.importProjectAction = importProjectAction;
        this.openSettingsAction = openSettingsAction;
        this.problemsAction = problemsAction;
        this.refreshSettingsAction = refreshSettingsAction;
        this.resourceUsageAction = resourceUsageAction;
        this.shortcutsAction = shortcutsAction;
        this.window = window;
    }

    public JMenuBar create() {
        menuBar.removeAll();
        menuBar.add(fileMenu());
        menuBar.add(viewMenu());
        if (viewController.currentProjectId() != null) {
            final JMenu projectMenu = new JMenu("Project");
            projectActions.populate(projectMenu);
            menuBar.add(projectMenu);
        }
        if (viewController.currentSessionId() != null) {
            final JMenu sessionMenu = new JMenu("Session");
            sessionActions.populate(sessionMenu);
            menuBar.add(sessionMenu);
        }
        menuBar.add(helpMenu());
        menuBar.revalidate();
        menuBar.repaint();
        return menuBar;
    }

    private JMenu fileMenu() {
        final JMenu file = new JMenu("File");
        file.add(
                actionItem(
                        createProjectAction,
                        shortcut(KeyStroke.getKeyStroke(KeyEvent.VK_N, 0), true)));
        file.add(actionItem(importProjectAction));
        final boolean projectSelected = viewController.currentProjectId() != null;
        if (projectSelected) {
            file.add(
                    actionItem(
                            createSessionAction,
                            shortcut(KeyStroke.getKeyStroke(KeyEvent.VK_N, 0), false)));
            file.add(
                    actionItem(
                            createTerminalAction,
                            shortcut(KeyStroke.getKeyStroke(KeyEvent.VK_T, 0), false)));
        }
        file.addSeparator();
        file.add(actionItem(openSettingsAction));
        file.add(actionItem(refreshSettingsAction));
        if (!SystemInfo.isMacOS) {
            file.addSeparator();
            file.add(
                    item(
                            "Exit",
                            () ->
                                    window.dispatchEvent(
                                            new WindowEvent(window, WindowEvent.WINDOW_CLOSING))));
        }
        return file;
    }

    private JMenu viewMenu() {
        final JMenu view = new JMenu("View");
        view.add(actionItem(findAction, shortcut(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), false)));
        view.add(actionItem(resourceUsageAction));
        view.add(actionItem(problemsAction));
        for (final var board : appState.appSettings().boards()) {
            view.add(
                    item(
                            board.name(),
                            () ->
                                    viewController.updateView(
                                            ViewId.BOARD,
                                            ViewController.ViewState.board(board.name()))));
        }
        return view;
    }

    private JMenu helpMenu() {
        final JMenu help = new JMenu("Help");
        help.add(actionItem(shortcutsAction));
        help.add(actionItem(aboutAction));
        return help;
    }

    private static JMenuItem item(final String label, final Runnable action) {
        final JMenuItem item = new JMenuItem(label);
        item.addActionListener(event -> action.run());
        return item;
    }

    private static JMenuItem actionItem(final Action action) {
        final JMenuItem item = new JMenuItem(action.label());
        item.setEnabled(action.enabled());
        item.addActionListener(event -> action.execute());
        return item;
    }

    private static JMenuItem actionItem(final Action action, final KeyStroke accelerator) {
        final JMenuItem item = new JMenuItem(action.label(), UiIcons.forAction(action));
        item.setEnabled(action.enabled());
        if (accelerator != null) {
            item.setAccelerator(accelerator);
        }
        item.addActionListener(event -> action.execute());
        return item;
    }

    private static KeyStroke shortcut(final KeyStroke keyStroke, final boolean shift) {
        final int modifiers =
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()
                        | (shift ? InputEvent.SHIFT_DOWN_MASK : 0);
        return KeyStroke.getKeyStroke(keyStroke.getKeyCode(), modifiers);
    }
}
