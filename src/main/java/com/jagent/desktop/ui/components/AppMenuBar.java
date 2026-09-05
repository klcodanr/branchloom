package com.jagent.desktop.ui.components;

import com.formdev.flatlaf.util.SystemInfo;
import com.jagent.desktop.api.Action;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.ui.actions.AboutAction;
import com.jagent.desktop.ui.actions.CreateProjectAction;
import com.jagent.desktop.ui.actions.CreateSessionAction;
import com.jagent.desktop.ui.actions.CreateTerminalAction;
import com.jagent.desktop.ui.actions.FindAction;
import com.jagent.desktop.ui.actions.ImportProjectAction;
import com.jagent.desktop.ui.actions.OpenSettingsAction;
import com.jagent.desktop.ui.actions.ProblemsAction;
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

/** Builds the application menu from the current action context. */
public final class AppMenuBar {
    private AppMenuBar() {}

    public static JMenuBar create(final ActionContext actionContext) {
        final boolean projectSelected = actionContext.appState().currentProjectId() != null;
        final boolean sessionSelected = actionContext.appState().currentSessionId() != null;
        final Window window = actionContext.window();
        final JMenuBar bar = new JMenuBar();

        final JMenu file = new JMenu("File");
        file.add(
                actionItem(
                        new CreateProjectAction(actionContext),
                        shortcut(KeyStroke.getKeyStroke(KeyEvent.VK_N, 0), true)));
        file.add(actionItem(new ImportProjectAction(actionContext)));
        if (projectSelected) {
            file.add(
                    actionItem(
                            new CreateSessionAction(actionContext),
                            shortcut(KeyStroke.getKeyStroke(KeyEvent.VK_N, 0), false)));
            file.add(
                    actionItem(
                            new CreateTerminalAction(actionContext),
                            shortcut(KeyStroke.getKeyStroke(KeyEvent.VK_T, 0), false)));
        }
        file.addSeparator();
        file.add(actionItem(new OpenSettingsAction(actionContext)));
        if (!SystemInfo.isMacOS) {
            file.addSeparator();
            file.add(
                    item(
                            "Exit",
                            () ->
                                    window.dispatchEvent(
                                            new WindowEvent(window, WindowEvent.WINDOW_CLOSING))));
        }
        bar.add(file);

        final JMenu view = new JMenu("View");
        view.add(
                actionItem(
                        new FindAction(actionContext),
                        shortcut(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), false)));
        view.add(actionItem(new ResourceUsageAction(actionContext)));
        view.addSeparator();
        view.add(actionItem(new ProblemsAction(actionContext)));
        bar.add(view);

        if (projectSelected) {
            final JMenu project = new JMenu("Project");
            ProjectActions.populate(
                    project, actionContext, actionContext.appState().currentProjectId());
            bar.add(project);
        }

        if (sessionSelected) {
            final JMenu session = new JMenu("Session");
            SessionActions.populate(
                    session, actionContext, actionContext.appState().currentSessionId(), null);
            bar.add(session);
        }

        final JMenu help = new JMenu("Help");
        help.add(actionItem(new ShortcutsAction(actionContext)));
        help.add(actionItem(new AboutAction(actionContext)));
        bar.add(help);
        return bar;
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
