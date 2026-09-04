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
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class ProjectTreePanelUiTest {
    private static final String DEMO = "Demo";
    private static final String PROJECT_PATH = "/tmp/demo";
    private static final String SESSION_NAME = "Feature";
    private static final String GROUP = "Group";
    private static final String PROMPT = "prompt";
    private static final String AGENT = "agent";

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

        GuiActionRunner.execute(() -> panel.tree().setSelectionRow(3));

        assertEquals(projectId, state.currentProjectId(), "selected tree project should be stored");
        assertEquals(
                ViewId.PROJECT, coordinator.currentViewId(), "project selection should navigate");
        assertEquals(List.of(ViewId.PROJECT), changedViews, "selection should notify navigation");
    }

    @Test
    void selectingReviewQueueClearsApplicationSelection() {
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

        GuiActionRunner.execute(() -> panel.tree().setSelectionPath(panel.tree().getPathForRow(1)));

        assertNull(
                state.currentProjectId(),
                "review queue selection should clear the current project");
        assertNull(
                state.currentSessionId(),
                "review queue selection should clear the current session");
        assertEquals(
                ViewId.REVIEW_QUEUE,
                coordinator.currentViewId(),
                "review queue selection should navigate");
    }

    @Test
    void typingDoesNotSelectTreeItems() throws java.io.InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(new Project(DEMO, PROJECT_PATH, null));
        state.updateCurrentProject(projectId);
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

        GuiActionRunner.execute(
                () ->
                        panel.tree()
                                .dispatchEvent(
                                        new java.awt.event.KeyEvent(
                                                panel.tree(),
                                                java.awt.event.KeyEvent.KEY_TYPED,
                                                System.currentTimeMillis(),
                                                0,
                                                java.awt.event.KeyEvent.VK_UNDEFINED,
                                                'h')));

        assertEquals(projectId, state.currentProjectId(), "typing should not change selection");
        assertNull(coordinator.currentViewId(), "typing should not navigate");
    }

    @Test
    void searchSelectsRenderedNamesAndEnterOpensTheMatch() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(project(DEMO, PROJECT_PATH, GROUP));
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
        final var treeContent = (JPanel) panel.getComponent(1);
        final var search = (JTextField) treeContent.getComponent(0);
        org.junit.jupiter.api.Assertions.assertFalse(
                search.isVisible(), "search should be hidden until typing starts");
        assertNotNull(
                search.getClientProperty("JTextField.leadingIcon"),
                "search should show the FlatLaf leading search icon");

        GuiActionRunner.execute(
                () -> {
                    search.setVisible(true);
                    search.setText(String.valueOf(DEMO.charAt(0)));
                });

        assertTrue(search.isVisible(), "typing should show the search field");
        assertEquals("D", search.getText(), "the first typed character should seed the search");
        GuiActionRunner.execute(() -> search.setText("emo"));
        assertNull(
                state.currentProjectId(),
                "search should not change the current project before confirmation");
        final var selectedNode =
                (DefaultMutableTreeNode)
                        ((JTree) treeContent.getComponent(1)).getLastSelectedPathComponent();
        assertEquals(
                DEMO,
                ((Project) ((Map.Entry<?, ?>) selectedNode.getUserObject()).getValue()).name(),
                "search should select the rendered project name");

        GuiActionRunner.execute(search::postActionEvent);

        assertEquals(projectId, state.currentProjectId(), "Enter should open the search match");
        assertEquals(ViewId.PROJECT, coordinator.currentViewId(), "Enter should navigate");
    }

    @Test
    void searchWithNoMatchClearsSelectionAndDoesNotNavigate() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.addProject(project(DEMO, PROJECT_PATH, GROUP));
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
        final var treeContent = (JPanel) panel.getComponent(1);
        final var search = (SearchInput) treeContent.getComponent(0);
        treeContent.setSize(400, 400);
        treeContent.doLayout();
        final int collapsedTreeY = panel.tree().getY();

        GuiActionRunner.execute(
                () -> {
                    search.setVisible(true);
                    search.setText("missing");
                    search.postActionEvent();
                });
        treeContent.doLayout();
        assertTrue(
                panel.tree().getY() > collapsedTreeY,
                "visible search should reserve space above the tree");

        assertNull(panel.tree().getLastSelectedPathComponent(), "no match should clear selection");
        assertNull(coordinator.currentViewId(), "no match should not navigate");

        GuiActionRunner.execute(
                () -> {
                    search.setText("");
                    search.setVisible(false);
                });
        treeContent.doLayout();
        assertEquals(
                collapsedTreeY,
                panel.tree().getY(),
                "hidden search should release its space above the tree");
    }

    @Test
    void searchFindsAndExpandsCollapsedSessions() throws java.io.InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var projectId = state.addProject(project("Demo", PROJECT_PATH, GROUP));
        final var sessionId =
                state.addSession(
                        projectId, new Session(projectId, "Second session", AGENT, PROMPT, null));
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
        final var tree = panel.tree();
        final var root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        final var projectNode =
                (DefaultMutableTreeNode)
                        ((DefaultMutableTreeNode) root.getChildAt(2)).getChildAt(0);
        org.junit.jupiter.api.Assertions.assertFalse(
                tree.isExpanded(new TreePath(projectNode.getPath())),
                "project should be collapsed before searching");

        final var search = (JTextField) ((JPanel) panel.getComponent(1)).getComponent(0);
        GuiActionRunner.execute(() -> search.setText("Second"));

        assertEquals(
                sessionId,
                ((Map.Entry<?, ?>)
                                ((DefaultMutableTreeNode) tree.getLastSelectedPathComponent())
                                        .getUserObject())
                        .getKey(),
                "search should select a session in a collapsed project");
        assertTrue(
                tree.isExpanded(new TreePath(projectNode.getPath())),
                "search should expand the matching session's project");
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
        assertEquals(5, root.getChildCount(), "global views and all groups should be rendered");
        assertEquals(
                "My Pull Requests", root.getChildAt(0).toString(), "authored PRs should be first");
        assertEquals(
                "Review Queue", root.getChildAt(1).toString(), "review queue should be second");
        assertEquals(
                "A-group", root.getChildAt(2).toString(), "groups should sort case-insensitively");
        assertEquals(
                Defaults.DEFAULT_GROUP,
                root.getChildAt(3).toString(),
                "blank groups should use default");
        assertEquals("z-group", root.getChildAt(4).toString(), "groups should sort alphabetically");
    }

    @Test
    void refreshPreservesMultipleExpandedProjects() throws java.io.InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var firstProjectId = state.addProject(project("First", "/tmp/first", GROUP));
        final var secondProjectId = state.addProject(project("Second", "/tmp/second", GROUP));
        state.addSession(
                firstProjectId, new Session(firstProjectId, "First session", AGENT, PROMPT, null));
        state.addSession(
                secondProjectId,
                new Session(secondProjectId, "Second session", AGENT, PROMPT, null));
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
                    final var group = (DefaultMutableTreeNode) root.getChildAt(2);
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
        final var group = (DefaultMutableTreeNode) root.getChildAt(2);
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
    void refreshWithNoProjectsLeavesGlobalNavigationUnselected() {
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

        final var root = (DefaultMutableTreeNode) panel.tree().getModel().getRoot();
        assertEquals(2, root.getChildCount(), "global views should remain available");
        assertEquals(
                null, panel.tree().getSelectionPath(), "refresh should not select a global view");
        assertEquals(
                null,
                coordinator.currentViewId(),
                "refresh should not navigate while restoring no project");
        assertEquals(
                List.of(),
                changedViews,
                "refresh should not notify navigation while restoring no project");
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
        final var group = (DefaultMutableTreeNode) root.getChildAt(2);
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
        final var group = (DefaultMutableTreeNode) root.getChildAt(2);
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
                        projectId, new Session(projectId, SESSION_NAME, AGENT, PROMPT, null));
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
                                        ((DefaultMutableTreeNode) root.getChildAt(2)).getChildAt(0))
                                .getChildAt(0);
        assertEquals(
                SESSION_NAME,
                ((Session) ((Map.Entry<?, ?>) sessionNode.getUserObject()).getValue()).name(),
                "session name should be stored");
        assertEquals(
                3, root.getChildCount(), "populated tree should contain global views and group");

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
