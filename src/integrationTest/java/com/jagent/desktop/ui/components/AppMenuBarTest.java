package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.formdev.flatlaf.util.SystemInfo;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.Defaults;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Map;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class AppMenuBarTest {
    private static final String ASSERTION_MESSAGE = "menu behavior should match";
    private static final String PROJECT_PATH = "/tmp";

    @Test
    void emptyStateBuildsOnlyGlobalMenus() {
        final ActionContext context =
                context(new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of()));

        final JMenuBar bar = GuiActionRunner.execute(() -> AppMenuBar.create(context));

        assertEquals(3, bar.getMenuCount(), ASSERTION_MESSAGE);
        assertEquals("File", bar.getMenu(0).getText(), ASSERTION_MESSAGE);
        assertEquals("View", bar.getMenu(1).getText(), ASSERTION_MESSAGE);
        assertEquals("Help", bar.getMenu(2).getText(), ASSERTION_MESSAGE);
        assertFalse(hasMenu(bar, "Project"), ASSERTION_MESSAGE);
        assertFalse(hasMenu(bar, "Session"), ASSERTION_MESSAGE);

        final JMenu file = bar.getMenu(0);
        assertEquals("New project", file.getItem(0).getText(), ASSERTION_MESSAGE);
        assertTrue(file.getItem(0).isEnabled(), ASSERTION_MESSAGE);
        assertEquals("Settings", file.getItem(2).getText(), ASSERTION_MESSAGE);
        assertTrue(file.getMenuComponent(1) instanceof JSeparator, ASSERTION_MESSAGE);
        if (SystemInfo.isMacOS) {
            assertFalse(hasItem(file, "Exit"), ASSERTION_MESSAGE);
        } else {
            assertEquals("Exit", file.getItem(4).getText(), ASSERTION_MESSAGE);
        }
    }

    @Test
    void selectedProjectAndSessionAddContextMenusAndShortcuts()
            throws java.io.InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project("Demo", PROJECT_PATH, null));
        final var sessionId =
                state.addSession(
                        projectId, new Session(projectId, "Feature", null, null, PROJECT_PATH));
        state.updateCurrentProject(projectId);
        state.updateCurrentSession(sessionId);
        final ActionContext context = context(state);

        final JMenuBar bar = GuiActionRunner.execute(() -> AppMenuBar.create(context));

        assertEquals(5, bar.getMenuCount(), ASSERTION_MESSAGE);
        assertEquals("Project", bar.getMenu(2).getText(), ASSERTION_MESSAGE);
        assertEquals("Session", bar.getMenu(3).getText(), ASSERTION_MESSAGE);
        assertEquals("Help", bar.getMenu(4).getText(), ASSERTION_MESSAGE);

        final JMenu file = bar.getMenu(0);
        assertEquals("Start agent session", file.getItem(1).getText(), ASSERTION_MESSAGE);
        assertEquals("New Terminal Tab", file.getItem(2).getText(), ASSERTION_MESSAGE);
        assertTrue(file.getItem(1).isEnabled(), ASSERTION_MESSAGE);
        assertTrue(file.getItem(2).isEnabled(), ASSERTION_MESSAGE);
        assertEquals(
                shortcut(KeyEvent.VK_N, true), file.getItem(0).getAccelerator(), ASSERTION_MESSAGE);
        assertEquals(
                shortcut(KeyEvent.VK_N, false),
                file.getItem(1).getAccelerator(),
                ASSERTION_MESSAGE);
        assertEquals(
                shortcut(KeyEvent.VK_T, false),
                file.getItem(2).getAccelerator(),
                ASSERTION_MESSAGE);

        final JMenu view = bar.getMenu(1);
        assertEquals(
                shortcut(KeyEvent.VK_F, false),
                view.getItem(0).getAccelerator(),
                ASSERTION_MESSAGE);
        assertEquals("Problems", view.getItem(3).getText(), ASSERTION_MESSAGE);
        assertNotNull(bar.getMenu(2).getItem(0), ASSERTION_MESSAGE);
        assertNotNull(bar.getMenu(3).getItem(0), ASSERTION_MESSAGE);
    }

    @Test
    void terminalMenuItemExecutesItsAction() throws java.io.InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project("Demo", PROJECT_PATH, null));
        final var sessionId =
                state.addSession(
                        projectId, new Session(projectId, "Feature", null, null, PROJECT_PATH));
        state.updateCurrentProject(projectId);
        state.updateCurrentSession(sessionId);
        final var coordinator = new ViewCoordinator(state);
        final ActionContext context = new ActionContext(coordinator, state, null);
        final JMenuBar bar = GuiActionRunner.execute(() -> AppMenuBar.create(context));

        GuiActionRunner.execute(() -> bar.getMenu(0).getItem(2).doClick());

        assertEquals(1, state.sessions().get(sessionId).terminalIds().size(), ASSERTION_MESSAGE);
        assertEquals(
                com.jagent.desktop.api.ViewId.SESSION,
                coordinator.currentViewId(),
                ASSERTION_MESSAGE);
    }

    private static ActionContext context(final AppState state) {
        return new ActionContext(new ViewCoordinator(state), state, null);
    }

    private static boolean hasMenu(final JMenuBar bar, final String label) {
        for (int index = 0; index < bar.getMenuCount(); index++) {
            if (label.equals(bar.getMenu(index).getText())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasItem(final JMenu menu, final String label) {
        for (final var component : menu.getMenuComponents()) {
            if (component instanceof JMenuItem item && label.equals(item.getText())) {
                return true;
            }
        }
        return false;
    }

    private static KeyStroke shortcut(final int keyCode, final boolean shift) {
        final int modifiers =
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()
                        | (shift ? InputEvent.SHIFT_DOWN_MASK : 0);
        return KeyStroke.getKeyStroke(keyCode, modifiers);
    }
}
