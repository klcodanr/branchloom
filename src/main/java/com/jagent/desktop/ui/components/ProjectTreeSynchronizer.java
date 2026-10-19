package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.services.AppState;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

final class ProjectTreeSynchronizer {
    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private final Map<ProjectId, DefaultMutableTreeNode> projectNodes = new HashMap<>();
    private final ProjectTreeSessionSynchronizer sessionSynchronizer;
    private final ProjectTreeProjectSynchronizer projectSynchronizer;

    public ProjectTreeSynchronizer(
            final JTree tree,
            final DefaultMutableTreeNode root,
            final BiConsumer<Project, Session> loadPullRequestStatus) {
        this.tree = tree;
        this.root = root;
        sessionSynchronizer = new ProjectTreeSessionSynchronizer(loadPullRequestStatus);
        projectSynchronizer =
                new ProjectTreeProjectSynchronizer(tree, root, projectNodes, sessionSynchronizer);
    }

    public DefaultMutableTreeNode projectNode(final ProjectId projectId) {
        return projectNodes.get(projectId);
    }

    public DefaultMutableTreeNode sessionNode(final SessionId sessionId) {
        return sessionSynchronizer.sessionNode(sessionId);
    }

    public void synchronize(final AppState appState) {
        final DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        ensureGlobalNodes(model);
        BoardTreeSynchronizer.synchronize(appState, tree, root, model);
        projectSynchronizer.synchronize(appState, model);
    }

    private void ensureGlobalNodes(final DefaultTreeModel model) {
        final Object[] desired = {
            ProjectTreePanel.MyPullRequestsNode.INSTANCE, ProjectTreePanel.BoardsNode.INSTANCE
        };
        for (int index = root.getChildCount() - 1; index >= 0; index--) {
            final Object value = ((DefaultMutableTreeNode) root.getChildAt(index)).getUserObject();
            if (!(value instanceof String)
                    && value != ProjectTreePanel.MyPullRequestsNode.INSTANCE
                    && value != ProjectTreePanel.BoardsNode.INSTANCE) {
                model.removeNodeFromParent((DefaultMutableTreeNode) root.getChildAt(index));
            }
        }
        for (int index = 0; index < desired.length; index++) {
            final DefaultMutableTreeNode node = globalNode(desired[index]);
            if (node == null) {
                model.insertNodeInto(new DefaultMutableTreeNode(desired[index]), root, index);
            } else if (root.getIndex(node) != index) {
                model.removeNodeFromParent(node);
                model.insertNodeInto(node, root, index);
            }
        }
    }

    private DefaultMutableTreeNode globalNode(final Object value) {
        for (int index = 0; index < root.getChildCount(); index++) {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode) root.getChildAt(index);
            if (node.getUserObject().equals(value)) {
                return node;
            }
        }
        return null;
    }
}
