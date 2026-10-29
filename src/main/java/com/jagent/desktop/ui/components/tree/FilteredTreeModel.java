package com.jagent.desktop.ui.components.tree;

import com.jagent.desktop.ui.components.tree.items.TreeItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;

public class FilteredTreeModel extends DefaultTreeModel {
    private String term = "";

    public FilteredTreeModel(final DefaultMutableTreeNode root) {
        super(root);
    }

    public void setTerm(final String newTerm) {
        term = newTerm == null ? "" : newTerm.trim().toLowerCase(Locale.ROOT);
        fireTreeStructureChanged(this, new Object[] {getRoot()}, null, null);
    }

    @Override
    public void insertNodeInto(
            final MutableTreeNode child, final MutableTreeNode parent, final int index) {
        if (!filtering()) {
            super.insertNodeInto(child, parent, index);
            return;
        }
        parent.insert(child, index);
        fireStructureChanged(parent);
    }

    @Override
    public void removeNodeFromParent(final MutableTreeNode node) {
        if (!filtering()) {
            super.removeNodeFromParent(node);
            return;
        }
        final MutableTreeNode parent = (MutableTreeNode) node.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("node does not have a parent");
        }
        parent.remove(node);
        fireStructureChanged(parent);
    }

    @Override
    public void nodeChanged(final TreeNode node) {
        if (!filtering()) {
            super.nodeChanged(node);
            return;
        }
        final TreeNode parent = node.getParent();
        fireStructureChanged(parent == null ? (TreeNode) getRoot() : parent);
    }

    @Override
    public Object getChild(final Object parent, final int index) {
        return visibleChildren((DefaultMutableTreeNode) parent).get(index);
    }

    @Override
    public int getChildCount(final Object parent) {
        return visibleChildren((DefaultMutableTreeNode) parent).size();
    }

    @Override
    public int getIndexOfChild(final Object parent, final Object child) {
        return visibleChildren((DefaultMutableTreeNode) parent).indexOf(child);
    }

    @Override
    public boolean isLeaf(final Object node) {
        return getChildCount(node) == 0;
    }

    private List<DefaultMutableTreeNode> visibleChildren(final DefaultMutableTreeNode parent) {
        return IntStream.range(0, parent.getChildCount())
                .mapToObj(index -> (DefaultMutableTreeNode) parent.getChildAt(index))
                .filter(this::matches)
                .toList();
    }

    private boolean filtering() {
        return !term.isEmpty();
    }

    private void fireStructureChanged(final TreeNode node) {
        final List<TreeNode> path = new ArrayList<>();
        for (TreeNode current = node; current != null; current = current.getParent()) {
            path.add(0, current);
        }
        fireTreeStructureChanged(this, path.toArray(), null, null);
    }

    private boolean matches(final DefaultMutableTreeNode node) {
        if (term.isEmpty()) {
            return true;
        }
        final Object userObject = node.getUserObject();
        return (userObject instanceof TreeItem item
                        && item.getCellRenderContents()
                                .text()
                                .toLowerCase(Locale.ROOT)
                                .contains(term))
                || IntStream.range(0, node.getChildCount())
                        .mapToObj(index -> (DefaultMutableTreeNode) node.getChildAt(index))
                        .anyMatch(this::matches);
    }
}
