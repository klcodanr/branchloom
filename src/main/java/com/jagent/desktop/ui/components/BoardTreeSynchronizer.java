package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.board.BoardDefinition;
import com.jagent.desktop.services.AppState;
import java.util.List;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

final class BoardTreeSynchronizer {
    private BoardTreeSynchronizer() {}

    public static void synchronize(
            final AppState appState,
            final JTree tree,
            final DefaultMutableTreeNode root,
            final DefaultTreeModel model) {
        final DefaultMutableTreeNode boards = find(root, ProjectTreePanel.BoardsNode.INSTANCE);
        if (boards == null) {
            return;
        }
        final List<BoardDefinition> definitions = appState.appSettings().boards();
        removeStale(boards, definitions, model);
        arrange(boards, definitions, model);
        tree.expandPath(new TreePath(boards.getPath()));
    }

    private static void removeStale(
            final DefaultMutableTreeNode boards,
            final List<BoardDefinition> definitions,
            final DefaultTreeModel model) {
        for (int index = boards.getChildCount() - 1; index >= 0; index--) {
            final DefaultMutableTreeNode child = (DefaultMutableTreeNode) boards.getChildAt(index);
            if (!(child.getUserObject() instanceof BoardDefinition definition)
                    || !definitions.contains(definition)) {
                model.removeNodeFromParent(child);
            }
        }
    }

    private static void arrange(
            final DefaultMutableTreeNode boards,
            final List<BoardDefinition> definitions,
            final DefaultTreeModel model) {
        for (int index = 0; index < definitions.size(); index++) {
            final BoardDefinition definition = definitions.get(index);
            final DefaultMutableTreeNode child = find(boards, definition);
            if (child != null) {
                model.removeNodeFromParent(child);
            }
            model.insertNodeInto(
                    child == null ? new DefaultMutableTreeNode(definition) : child, boards, index);
        }
        model.reload(boards);
    }

    private static DefaultMutableTreeNode find(
            final DefaultMutableTreeNode parent, final Object value) {
        for (int index = 0; index < parent.getChildCount(); index++) {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode) parent.getChildAt(index);
            if (node.getUserObject().equals(value)) {
                return node;
            }
        }
        return null;
    }
}
