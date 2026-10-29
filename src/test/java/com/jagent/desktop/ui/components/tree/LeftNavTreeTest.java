package com.jagent.desktop.ui.components.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.test.TestAppState;
import com.jagent.desktop.ui.components.tree.items.ProjectTreeItem;
import com.jagent.desktop.ui.components.tree.items.PullRequestsItem;
import java.io.InvalidObjectException;
import java.lang.reflect.InvocationTargetException;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class LeftNavTreeTest {
    @Test
    void buildsNavigationNodesAndFiltersMatches()
            throws InvalidObjectException, InterruptedException, InvocationTargetException {
        final AppState state = TestAppState.empty();
        final var projectId =
                state.addProject(Project.create("Demo", "/tmp/demo", null).withGroup(null));
        final var tree =
                new LeftNavTree(
                        term -> {},
                        TestAppState.controller(state),
                        state,
                        null,
                        TestAppState.sessionActions(state),
                        TestAppState.projectActions(state));
        final var root = (DefaultMutableTreeNode) tree.getModel().getRoot();

        assertEquals(
                3,
                root.getChildCount(),
                "navigation should contain pull requests, boards, and projects");
        assertInstanceOf(
                PullRequestsItem.class,
                ((DefaultMutableTreeNode) root.getChildAt(0)).getUserObject(),
                "first navigation item should be pull requests");

        tree.setSearchQuery("demo");
        assertEquals(
                1, tree.getModel().getChildCount(root), "search should retain the matching group");
        final var group = (DefaultMutableTreeNode) tree.getModel().getChild(root, 0);
        final var project = (DefaultMutableTreeNode) tree.getModel().getChild(group, 0);
        assertEquals(
                projectId,
                ((ProjectTreeItem) project.getUserObject()).getProjectId(),
                "search should retain the project");

        tree.setSearchQuery("");
        assertEquals(
                3,
                tree.getModel().getChildCount(root),
                "clearing search should restore navigation");
        tree.rebuild();
        SwingUtilities.invokeAndWait(() -> {});
        assertEquals(3, tree.getModel().getChildCount(root), "rebuild should restore navigation");
    }
}
