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
    private static final String AGENTS_LABEL = "Agents";
    private static final String EDITORS_LABEL = "Editors";
    private static final String AGENT_NAME = "Agent";
    private static final String AGENT_COMMAND = "agent";

    @Test
    void projectAndSessionMenusIncludeConfiguredToolsAndAgents()
            throws java.io.InvalidObjectException {
        final AppSettings settings =
                new AppSettings(
                        List.of(new Agent(AGENT_NAME, AGENT_COMMAND, AGENT_COMMAND)),
                        List.of(),
                        "review",
                        "System",
                        List.of(new Tool("Editor", "editor .")),
                        Defaults.DEFAULT_WORKTREE_TEMPLATE,
                        "");
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

        assertTrue(
                findMenu(projectMenu, AGENTS_LABEL) == null,
                "project should not use agents submenu");
        assertTrue(
                findMenu(projectMenu, EDITORS_LABEL) == null,
                "project should not use editors submenu");
        final var projectAgentsHeading = findItem(projectMenu, AGENTS_LABEL);
        final var projectEditorsHeading = findItem(projectMenu, EDITORS_LABEL);
        assertNotNull(projectAgentsHeading, "project agents heading should exist");
        assertTrue(!projectAgentsHeading.isEnabled(), "project agents heading should be disabled");
        assertNotNull(projectEditorsHeading, "project editors heading should exist");
        assertTrue(
                !projectEditorsHeading.isEnabled(), "project editors heading should be disabled");
        assertNotNull(findItem(projectMenu, AGENT_NAME), "project agent action should exist");
        assertNotNull(findItem(projectMenu, "Editor"), "project editor action should exist");
        final var importMenu = findMenu(projectMenu, "Import from");
        assertNotNull(importMenu, "project import menu should exist");
        assertEquals(
                "Import branch", importMenu.getItem(0).getText(), "branch import should exist");
        assertEquals(
                "Import worktree", importMenu.getItem(1).getText(), "worktree import should exist");
        assertEquals(
                "Start sessions from GitHub issues",
                importMenu.getItem(2).getText(),
                "issue import should exist");
        assertEquals(
                "Start sessions from pasted lines",
                importMenu.getItem(3).getText(),
                "paste import should exist");
        assertTrue(
                findMenu(sessionMenu, AGENTS_LABEL) == null,
                "session should not use agents submenu");
        assertTrue(
                findMenu(sessionMenu, EDITORS_LABEL) == null,
                "session should not use editors submenu");
        final var sessionAgentsHeading = findItem(sessionMenu, AGENTS_LABEL);
        final var sessionEditorsHeading = findItem(sessionMenu, EDITORS_LABEL);
        assertNotNull(sessionAgentsHeading, "session agents heading should exist");
        assertTrue(!sessionAgentsHeading.isEnabled(), "session agents heading should be disabled");
        assertNotNull(sessionEditorsHeading, "session editors heading should exist");
        assertTrue(
                !sessionEditorsHeading.isEnabled(), "session editors heading should be disabled");
        assertNotNull(findItem(sessionMenu, AGENT_NAME), "session agent action should exist");
        assertNotNull(findItem(sessionMenu, "Editor"), "session editor action should exist");

        final var updateBranch = findItem(sessionMenu, "Update branch");
        assertNotNull(updateBranch, "session update branch action should exist");
        GuiActionRunner.execute((GuiActionRunnable) updateBranch::doClick);
        assertEquals(
                sessionId, state.currentSessionId(), "session action should select the session");
    }

    @Test
    void projectMenuSelectsTargetBeforeEvaluatingActionState()
            throws java.io.InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project("Demo", "/tmp", null));
        final var context = new ActionContext(new ViewCoordinator(state), state, null);

        final var menu = GuiActionRunner.execute(() -> ProjectActions.menu(context, projectId));

        assertEquals(projectId, state.currentProjectId(), "menu should select its target project");
        assertNotNull(findItem(menu, "Start agent session"), "project action should be present");
        assertTrue(
                findItem(menu, "Start agent session").isEnabled(),
                "project action should be enabled for the target project");
    }

    @Test
    void sessionMenuSelectsTargetProjectBeforeEvaluatingActionState()
            throws java.io.InvalidObjectException {
        final AppSettings settings =
                new AppSettings(
                        List.of(new Agent(AGENT_NAME, AGENT_COMMAND, AGENT_COMMAND)),
                        List.of(),
                        "review",
                        "System",
                        List.of(),
                        Defaults.DEFAULT_WORKTREE_TEMPLATE);
        final AppState state = new AppState(settings, Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project("Demo", "/tmp", null));
        final var sessionId =
                state.addSession(projectId, new Session(projectId, "Feature", null, null, null));
        final var context = new ActionContext(new ViewCoordinator(state), state, null);

        final var menu = GuiActionRunner.execute(() -> SessionActions.menu(context, sessionId));

        assertEquals(projectId, state.currentProjectId(), "menu should select the session project");
        final javax.swing.JMenuItem agentsHeading = findItem(menu, AGENTS_LABEL);
        assertNotNull(agentsHeading, "session agents heading should exist");
        assertTrue(!agentsHeading.isEnabled(), "session agents heading should be disabled");
        final javax.swing.JMenuItem agentAction = findItem(menu, AGENT_NAME);
        assertNotNull(agentAction, "configured agent action should exist");
        assertTrue(
                agentAction.isEnabled(),
                "session agent action should be enabled for the target project");
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
        Theme.Colors.success();
        Theme.Colors.warning();
        Theme.Colors.danger();
        Theme.Colors.muted();
        assertNotNull(Theme.sectionBorder(1, 2, 3, 4), VALUE_MESSAGE);
        Theme.applySwingDefaults();
        assertTrue(Theme.FontSize.values().length > 0, "font sizes should be defined");
    }

    @Test
    void themeColorsResolveForEveryTheme() {
        for (final Theme.FlatLafTheme theme : Theme.FlatLafTheme.values()) {
            Theme.apply(theme);
            assertNotNull(Theme.Colors.success(), "success should resolve for " + theme);
            assertNotNull(Theme.Colors.warning(), "warning should resolve for " + theme);
            assertNotNull(Theme.Colors.danger(), "danger should resolve for " + theme);
            assertNotNull(Theme.Colors.focus(), "focus should resolve for " + theme);
            assertNotNull(Theme.Colors.merge(), "merge should resolve for " + theme);
            assertNotNull(Theme.Colors.muted(), "muted should resolve for " + theme);
            assertNotNull(Theme.Colors.border(), "border should resolve for " + theme);
            assertNotNull(Theme.Colors.background(), "background should resolve for " + theme);
            assertNotNull(Theme.Colors.foreground(), "foreground should resolve for " + theme);
            assertNotNull(
                    Theme.Colors.textareaBackground(),
                    "textarea background should resolve for " + theme);
            assertNotNull(
                    Theme.Colors.textareaForeground(),
                    "textarea foreground should resolve for " + theme);
            assertNotNull(
                    Theme.Colors.textSelectionBackground(),
                    "text selection background should resolve for " + theme);
            assertNotNull(
                    Theme.Colors.textSelectionForeground(),
                    "text selection foreground should resolve for " + theme);
            assertNotNull(
                    Theme.Colors.textCaretForeground(),
                    "text caret foreground should resolve for " + theme);
            assertNotNull(Theme.Colors.purple(), "purple should resolve for " + theme);
            assertNotNull(Theme.Colors.green(), "green should resolve for " + theme);
            assertNotNull(Theme.Colors.grey(), "grey should resolve for " + theme);
            assertNotNull(Theme.Colors.blue(), "blue should resolve for " + theme);
            assertNotNull(Theme.Colors.darkYellow(), "dark yellow should resolve for " + theme);
        }
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
