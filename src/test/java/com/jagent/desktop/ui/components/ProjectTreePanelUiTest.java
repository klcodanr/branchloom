package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.services.terminal.TerminalState;
import com.jagent.desktop.ui.Defaults;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.assertj.swing.edt.GuiActionRunnable;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class ProjectTreePanelUiTest {
    private static final String DEMO = "Demo";
    private static final String PROJECT_PATH = "/tmp/demo";
    private static final String SESSION_NAME = "Feature";
    private static final String GROUP = "Group";
    private static final String PROMPT = "prompt";

    @Test
    void clickingSettingsUpdatesNavigation() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addProject(new Project(DEMO, PROJECT_PATH, null));
        final var changedViews = new ArrayList<ViewId>();
        final var coordinator = new ViewCoordinator(state, changedViews::add);
        final var panel =
                GuiActionRunner.execute(
                        () -> {
                            final var created =
                                    new ProjectTreePanel(
                                            new ActionContext(coordinator, state, null));
                            created.refresh(null, null);
                            return created;
                        });
        final JButton settings = (JButton) panel.getComponent(2);
        GuiActionRunner.execute((GuiActionRunnable) settings::doClick);

        assertEquals(
                ViewId.SETTINGS,
                coordinator.currentViewId(),
                "settings button should navigate to settings");
        assertEquals(
                List.of(ViewId.SETTINGS),
                changedViews,
                "settings navigation should notify the coordinator");
    }

    @Test
    void selectingProjectUpdatesApplicationSelection() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project(DEMO, PROJECT_PATH, null));
        final var changedViews = new ArrayList<ViewId>();
        final var coordinator = new ViewCoordinator(state, changedViews::add);
        final var panel =
                GuiActionRunner.execute(
                        () -> {
                            final var created =
                                    new ProjectTreePanel(
                                            new ActionContext(coordinator, state, null));
                            created.refresh(null, null);
                            return created;
                        });

        GuiActionRunner.execute(() -> panel.tree().setSelectionRow(2));

        assertEquals(projectId, state.currentProjectId(), "selected tree project should be stored");
        assertEquals(
                ViewId.PROJECT, coordinator.currentViewId(), "project selection should navigate");
        assertEquals(List.of(ViewId.PROJECT), changedViews, "selection should notify navigation");
    }

    @Test
    void selectingHomeClearsApplicationSelection() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project(DEMO, PROJECT_PATH, null));
        final var coordinator = new ViewCoordinator(state);
        final var panel =
                GuiActionRunner.execute(
                        () -> {
                            final var created =
                                    new ProjectTreePanel(
                                            new ActionContext(coordinator, state, null));
                            created.refresh(state.projects().get(projectId), null);
                            return created;
                        });

        GuiActionRunner.execute(() -> panel.tree().setSelectionPath(panel.tree().getPathForRow(0)));

        assertNull(state.currentProjectId(), "home selection should clear the current project");
        assertNull(state.currentSessionId(), "home selection should clear the current session");
        assertEquals(ViewId.HOME, coordinator.currentViewId(), "home selection should navigate");
    }

    @Test
    void groupsProjectsCaseInsensitivelyAndUsesDefaultForBlankGroups() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addProject(project("Zulu", "/tmp/zulu", "z-group"));
        state.addProject(project("Defaulted", "/tmp/defaulted", " "));
        state.addProject(project("alpha", "/tmp/alpha", "A-group"));
        final var coordinator = new ViewCoordinator(state);
        final var panel =
                GuiActionRunner.execute(
                        () -> {
                            final var created =
                                    new ProjectTreePanel(
                                            new ActionContext(coordinator, state, null));
                            created.refresh(null, null);
                            return created;
                        });

        final var root = (DefaultMutableTreeNode) panel.tree().getModel().getRoot();
        assertEquals(4, root.getChildCount(), "all groups should be rendered");
        assertEquals("Home", root.getChildAt(0).toString(), "home should be first");
        assertEquals(
                "A-group", root.getChildAt(1).toString(), "groups should sort case-insensitively");
        assertEquals(
                Defaults.DEFAULT_GROUP,
                root.getChildAt(2).toString(),
                "blank groups should use default");
        assertEquals("z-group", root.getChildAt(3).toString(), "groups should sort alphabetically");
    }

    @Test
    void refreshPreservesMultipleExpandedProjects() throws java.io.InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var firstProjectId = state.addProject(project("First", "/tmp/first", GROUP));
        final var secondProjectId = state.addProject(project("Second", "/tmp/second", GROUP));
        state.addSession(
                firstProjectId,
                new Session(firstProjectId, "First session", "agent", PROMPT, null));
        state.addSession(
                secondProjectId,
                new Session(secondProjectId, "Second session", "agent", PROMPT, null));
        final var panel =
                GuiActionRunner.execute(
                        () -> {
                            final var created =
                                    new ProjectTreePanel(
                                            new ActionContext(
                                                    new ViewCoordinator(state), state, null));
                            created.refresh(null, null);
                            return created;
                        });

        GuiActionRunner.execute(
                () -> {
                    final var root = (DefaultMutableTreeNode) panel.tree().getModel().getRoot();
                    final var group = (DefaultMutableTreeNode) root.getChildAt(1);
                    final var firstProject = group.getChildAt(0);
                    final var secondProject = group.getChildAt(1);
                    panel.tree()
                            .expandPath(
                                    new TreePath(
                                            ((DefaultMutableTreeNode) firstProject).getPath()));
                    panel.tree()
                            .expandPath(
                                    new TreePath(
                                            ((DefaultMutableTreeNode) secondProject).getPath()));
                    panel.refresh(null, null);

                    org.junit.jupiter.api.Assertions.assertSame(
                            firstProject,
                            group.getChildAt(0),
                            "unchanged project nodes should not be recreated");
                    org.junit.jupiter.api.Assertions.assertSame(
                            secondProject,
                            group.getChildAt(1),
                            "unchanged project nodes should retain their identity");

                    final ProjectId firstNodeId =
                            (ProjectId)
                                    ((Map.Entry<?, ?>)
                                                    ((DefaultMutableTreeNode) firstProject)
                                                            .getUserObject())
                                            .getKey();
                    state.updateProject(
                            firstNodeId, state.projects().get(firstNodeId).withName("Updated"));
                    panel.refresh(null, null);
                    org.junit.jupiter.api.Assertions.assertSame(
                            firstProject,
                            group.getChildAt(0),
                            "changed project nodes should retain their identity");
                    assertEquals(
                            "Updated",
                            ((Project)
                                            ((Map.Entry<?, ?>)
                                                            ((DefaultMutableTreeNode) firstProject)
                                                                    .getUserObject())
                                                    .getValue())
                                    .name(),
                            "changed project data should update the existing node");
                });

        final var root = (DefaultMutableTreeNode) panel.tree().getModel().getRoot();
        final var group = (DefaultMutableTreeNode) root.getChildAt(1);
        assertTrue(
                panel.tree()
                        .isExpanded(
                                new TreePath(
                                        ((DefaultMutableTreeNode) group.getChildAt(0)).getPath())),
                "first project should remain expanded after refresh");
        assertTrue(
                panel.tree()
                        .isExpanded(
                                new TreePath(
                                        ((DefaultMutableTreeNode) group.getChildAt(1)).getPath())),
                "second project should remain expanded after refresh");
    }

    @Test
    void refreshWithNoProjectsSelectsHome() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var changedViews = new ArrayList<ViewId>();
        final var coordinator = new ViewCoordinator(state, changedViews::add);
        final var panel =
                GuiActionRunner.execute(
                        () -> {
                            final var created =
                                    new ProjectTreePanel(
                                            new ActionContext(coordinator, state, null));
                            created.refresh(null, null);
                            return created;
                        });

        assertEquals(1, panel.tree().getRowCount(), "an empty project tree should show home");
        assertEquals(
                "Home",
                panel.tree().getPathForRow(0).getLastPathComponent().toString(),
                "home should be the fallback selection");
        assertEquals(
                null,
                coordinator.currentViewId(),
                "refresh should not navigate while restoring home");
        assertEquals(
                List.of(),
                changedViews,
                "refresh should not notify navigation while restoring home");
    }

    @Test
    void projectRendererAndTooltipExposeProjectDetails() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addProject(project("<" + DEMO + ">", "/tmp/project&path", GROUP));
        final var coordinator = new ViewCoordinator(state);
        final var panel =
                GuiActionRunner.execute(
                        () -> {
                            final var created =
                                    new ProjectTreePanel(
                                            new ActionContext(coordinator, state, null));
                            created.refresh(null, null);
                            return created;
                        });
        final JTree tree = panel.tree();
        final var root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        final var group = (DefaultMutableTreeNode) root.getChildAt(1);
        final var projectNode = (DefaultMutableTreeNode) group.getChildAt(0);
        final var rendered =
                tree.getCellRenderer()
                        .getTreeCellRendererComponent(
                                tree, projectNode, false, false, true, 2, false);
        assertEquals(
                "<Demo>", ((javax.swing.JLabel) rendered).getText(), "project name should render");

        tree.setSize(400, 300);
        tree.doLayout();
        final var path = new TreePath(projectNode.getPath());
        final var bounds = tree.getPathBounds(path);
        assertNotNull(bounds, "project node should have bounds");
        final var event =
                new java.awt.event.MouseEvent(
                        tree,
                        java.awt.event.MouseEvent.MOUSE_MOVED,
                        0,
                        0,
                        bounds.x + 1,
                        bounds.y + 1,
                        0,
                        false);
        final var tooltip = tree.getToolTipText(event);
        assertTrue(tooltip.contains("&lt;Demo&gt;"), "tooltip should escape project name");
        assertTrue(tooltip.contains("/tmp/project&amp;path"), "tooltip should escape project path");
        assertTrue(tooltip.contains("0 sessions"), "tooltip should show session count");
    }

    @Test
    void selectingSessionUpdatesNavigationAndRendererShowsStatus()
            throws java.io.InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(project(DEMO, PROJECT_PATH, GROUP));
        final var sessionId =
                state.addSession(
                        projectId,
                        new Session(projectId, SESSION_NAME, "Agent", "prompt", PROJECT_PATH));
        final var coordinator = new ViewCoordinator(state);
        final var panel =
                GuiActionRunner.execute(
                        () -> {
                            final var created =
                                    new ProjectTreePanel(
                                            new ActionContext(coordinator, state, null));
                            created.refresh(
                                    state.projects().get(projectId),
                                    state.sessions().get(sessionId));
                            return created;
                        });
        final var root = (DefaultMutableTreeNode) panel.tree().getModel().getRoot();
        final var group = (DefaultMutableTreeNode) root.getChildAt(1);
        final var projectNode = (DefaultMutableTreeNode) group.getChildAt(0);
        final var sessionNode = (DefaultMutableTreeNode) projectNode.getChildAt(0);

        panel.updateSessionStatus(state.sessions().get(sessionId), TerminalState.FAILED);
        GuiActionRunner.execute(
                () -> panel.tree().setSelectionPath(new TreePath(sessionNode.getPath())));

        assertEquals(sessionId, state.currentSessionId(), "selected session should be stored");
        assertEquals(ViewId.SESSION, coordinator.currentViewId(), "session should be opened");
        final var rendered =
                panel.tree()
                        .getCellRenderer()
                        .getTreeCellRendererComponent(
                                panel.tree(), sessionNode, false, false, true, 3, false);
        assertEquals(
                SESSION_NAME,
                ((javax.swing.JLabel) rendered).getText(),
                "session name should render");
    }

    @Test
    void populatedTreeRendersSessionsAndSelectsThem() throws java.io.InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(project("Demo", "/tmp/demo", GROUP));
        final var sessionId =
                state.addSession(
                        projectId, new Session(projectId, SESSION_NAME, "agent", "prompt", null));
        final var coordinator = new ViewCoordinator(state);
        final var panel =
                GuiActionRunner.execute(
                        () -> {
                            final var created =
                                    new ProjectTreePanel(
                                            new ActionContext(coordinator, state, null));
                            created.refresh(
                                    state.projects().get(projectId),
                                    state.sessions().get(sessionId));
                            return created;
                        });

        final var root = (DefaultMutableTreeNode) panel.tree().getModel().getRoot();
        final var sessionNode =
                (DefaultMutableTreeNode)
                        ((DefaultMutableTreeNode)
                                        ((DefaultMutableTreeNode) root.getChildAt(1)).getChildAt(0))
                                .getChildAt(0);
        assertEquals(
                SESSION_NAME,
                ((Session) ((Map.Entry<?, ?>) sessionNode.getUserObject()).getValue()).name(),
                "session name should be stored");
        assertEquals(2, root.getChildCount(), "populated tree should contain home and group");

        GuiActionRunner.execute(
                () -> panel.tree().setSelectionPath(new TreePath(sessionNode.getPath())));

        assertEquals(
                projectId, state.currentProjectId(), "session selection should retain project");
        assertEquals(sessionId, state.currentSessionId(), "session selection should be stored");
        assertEquals(
                ViewId.SESSION, coordinator.currentViewId(), "session selection should navigate");
    }

    private static Project project(final String name, final String path, final String group) {
        return new Project(name, path, group, null, null, null, null, List.of(), List.of());
    }
}
