package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

public final class ProjectTreeSearchHandler {
    private final JTree tree;
    private final SearchInput search;
    private final DefaultMutableTreeNode root;
    private final Consumer<DefaultMutableTreeNode> selectNode;
    private final Consumer<DefaultMutableTreeNode> select;
    private DefaultMutableTreeNode searchMatch;

    public ProjectTreeSearchHandler(
            final JTree tree,
            final SearchInput search,
            final DefaultMutableTreeNode root,
            final Consumer<DefaultMutableTreeNode> selectNode,
            final Consumer<DefaultMutableTreeNode> select) {
        this.tree = tree;
        this.search = search;
        this.root = root;
        this.selectNode = selectNode;
        this.select = select;
        search.onChange(this::selectSearchMatch);
        search.onSubmit(this::selectSearchResult);
        search.onCancel(this::clearSearch);
    }

    private void selectSearchMatch(final String value) {
        final String query = value.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            clearSelection();
            return;
        }
        final DefaultMutableTreeNode node = findSearchMatch(root, query);
        if (node == null) {
            clearSelection();
            return;
        }
        searchMatch = node;
        final TreePath path = new TreePath(node.getPath());
        tree.expandPath(path);
        tree.scrollPathToVisible(path);
        selectNode.accept(node);
    }

    private static DefaultMutableTreeNode findSearchMatch(
            final DefaultMutableTreeNode parent, final String query) {
        for (int index = 0; index < parent.getChildCount(); index++) {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode) parent.getChildAt(index);
            if (searchText(node).toLowerCase(Locale.ROOT).contains(query)) {
                return node;
            }
            final DefaultMutableTreeNode descendant = findSearchMatch(node, query);
            if (descendant != null) {
                return descendant;
            }
        }
        return null;
    }

    private void selectSearchResult() {
        if (searchMatch != null) {
            select.accept(searchMatch);
            tree.requestFocusInWindow();
        }
    }

    private void clearSearch() {
        search.setText("");
        search.setVisible(false);
        tree.clearSelection();
        tree.requestFocusInWindow();
    }

    private void clearSelection() {
        searchMatch = null;
        tree.clearSelection();
    }

    private static String searchText(final DefaultMutableTreeNode node) {
        final Object item = node.getUserObject();
        if (item instanceof Map.Entry<?, ?> entry) {
            if (entry.getValue() instanceof Project project) {
                return project.name();
            }
            if (entry.getValue() instanceof Session session) {
                return session.name();
            }
        }
        return item.toString();
    }
}
