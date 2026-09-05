package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.AppSettings;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.Tool;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.Defaults;
import java.awt.Font;
import java.util.List;
import java.util.Map;
import org.assertj.swing.edt.GuiActionRunnable;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class ContextMenuAndThemeTest {
    private static final String VALUE_MESSAGE = "theme value should be available";

    @Test
    void projectAndSessionMenusIncludeConfiguredToolsAndAgents()
            throws java.io.InvalidObjectException {
        final AppSettings settings =
                new AppSettings(
                        List.of(new Agent("Agent", "agent", "agent")),
                        List.of(),
                        "review",
                        "System",
                        List.of(new Tool("Editor", "editor .")),
                        Defaults.DEFAULT_WORKTREE_TEMPLATE);
        final AppState state = new AppState(settings, Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project("Demo", "/tmp", null));
        final var sessionId =
                state.addSession(projectId, new Session(projectId, "Feature", null, null, null));
        state.updateCurrentProject(projectId);
        state.updateCurrentSession(sessionId);
        final var context = new ActionContext(new ViewCoordinator(state), state, null);

        final var projectMenu =
                GuiActionRunner.execute(() -> ProjectActions.menu(context, projectId));
        final var sessionMenu =
                GuiActionRunner.execute(() -> SessionActions.menu(context, sessionId));

        assertTrue(findMenu(projectMenu, "Agents") != null, "project agents menu should exist");
        assertTrue(findMenu(projectMenu, "Editors") != null, "project editors menu should exist");
        final var importMenu = findMenu(projectMenu, "Import from");
        assertNotNull(importMenu, "project import menu should exist");
        assertEquals("Branches", importMenu.getItem(0).getText(), "branch import should exist");
        assertEquals("Worktrees", importMenu.getItem(1).getText(), "worktree import should exist");
        assertEquals("GitHub issues", importMenu.getItem(2).getText(), "issue import should exist");
        assertEquals("Pasted lines", importMenu.getItem(3).getText(), "paste import should exist");
        assertTrue(findMenu(sessionMenu, "Agents") != null, "session agents menu should exist");
        assertTrue(findMenu(sessionMenu, "Editors") != null, "session editors menu should exist");

        final var updateBranch = findItem(sessionMenu, "Update branch");
        assertNotNull(updateBranch, "session update branch action should exist");
        GuiActionRunner.execute((GuiActionRunnable) updateBranch::doClick);
        assertEquals(
                sessionId, state.currentSessionId(), "session action should select the session");
    }

    @Test
    void themeHelpersResolveValuesAndFonts() {
        assertEquals("System", Theme.FlatLafTheme.SYSTEM.toString(), VALUE_MESSAGE);
        assertEquals(Theme.FlatLafTheme.DARK, Theme.FlatLafTheme.from("dark"), VALUE_MESSAGE);
        assertEquals(Theme.FlatLafTheme.SYSTEM, Theme.FlatLafTheme.from("unknown"), VALUE_MESSAGE);
        assertEquals(11, Theme.FontSize.XS.points(), VALUE_MESSAGE);
        assertNotNull(Theme.font(Theme.FontSize.MD), VALUE_MESSAGE);
        assertEquals(Font.BOLD, Theme.boldFont(Theme.FontSize.SM).getStyle(), VALUE_MESSAGE);
        assertEquals(
                Font.MONOSPACED, Theme.terminalFont(Theme.FontSize.SM).getFamily(), VALUE_MESSAGE);
        Theme.successColor();
        Theme.warningColor();
        Theme.dangerColor();
        Theme.mutedColor();
        assertNotNull(Theme.sectionBorder(1, 2, 3, 4), VALUE_MESSAGE);
        Theme.applySwingDefaults();
        assertTrue(Theme.FontSize.values().length > 0, "font sizes should be defined");
    }

    private static javax.swing.JMenu findMenu(
            final javax.swing.JPopupMenu menu, final String label) {
        for (final java.awt.Component component : menu.getComponents()) {
            if (component instanceof javax.swing.JMenu submenu && label.equals(submenu.getText())) {
                return submenu;
            }
        }
        return null;
    }

    private static javax.swing.JMenuItem findItem(
            final javax.swing.JPopupMenu menu, final String label) {
        for (final java.awt.Component component : menu.getComponents()) {
            if (component instanceof javax.swing.JMenuItem item && label.equals(item.getText())) {
                return item;
            }
        }
        return null;
    }
}
