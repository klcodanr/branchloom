package com.jagent.desktop.ui.components.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.test.TestAppState;
import com.jagent.desktop.ui.components.tree.items.GroupTreeItem;
import com.jagent.desktop.ui.components.tree.items.ProjectTreeItem;
import com.jagent.desktop.ui.components.tree.items.SessionTreeItem;
import java.io.InvalidObjectException;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class ProjectsTreeTest {
    @Test
    void buildsDefaultProjectAndSessionNodes() throws InvalidObjectException {
        final AppState state = TestAppState.empty();
        final var projectId =
                state.addProject(new Project("Demo", "/tmp/demo", null).withGroup(null));
        final var sessionId =
                state.addSession(
                        projectId, new Session(projectId, "Feature", "Agent", "Prompt", null));
        final var model = new FilteredTreeModel(new DefaultMutableTreeNode("root"));
        final var tree = new JTree(model);
        final var projects = new ProjectsTree(TestAppState.context(state), model, tree);

        final var group = projects.getGroupNodes().get(0);
        final var project = (DefaultMutableTreeNode) group.getChildAt(0);
        final var session = (DefaultMutableTreeNode) project.getChildAt(0);
        assertEquals(
                "Default", ((GroupTreeItem) group.getUserObject()).getId(), "group should default");
        assertEquals(
                projectId,
                ((ProjectTreeItem) project.getUserObject()).getProjectId(),
                "project should be present");
        assertEquals(
                sessionId,
                ((SessionTreeItem) session.getUserObject()).getSessionId(),
                "session should be present");
    }

    @Test
    void respondsToProjectAndSessionLifecycleEvents()
            throws InvalidObjectException, InterruptedException, InvocationTargetException {
        final AppState state = TestAppState.empty();
        final var model = new FilteredTreeModel(new DefaultMutableTreeNode("root"));
        final var tree = new JTree(model);
        final var projects = new ProjectsTree(TestAppState.context(state), model, tree);
        final var projectId =
                state.addProject(new Project("Demo", "/tmp/demo", null).withGroup(null));
        SwingUtilities.invokeAndWait(() -> {});
        assertEquals(
                1,
                projects.getGroupNodes().get(0).getChildCount(),
                "project add should create a node");
        final var sessionId =
                state.addSession(
                        projectId, new Session(projectId, "Feature", "Agent", "Prompt", null));
        SwingUtilities.invokeAndWait(() -> {});
        assertEquals(
                1,
                ((DefaultMutableTreeNode) projects.getGroupNodes().get(0).getChildAt(0))
                        .getChildCount(),
                "session add should create a node");
        state.removeSession(sessionId);
        state.removeProject(projectId);
        SwingUtilities.invokeAndWait(() -> {});
        assertTrue(
                projects.getGroupNodes().isEmpty(), "removing the project should remove its group");
    }
}
