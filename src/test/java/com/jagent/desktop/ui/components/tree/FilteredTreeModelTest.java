package com.jagent.desktop.ui.components.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jagent.desktop.ui.components.tree.items.TreeItem;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class FilteredTreeModelTest {
    @Test
    void filtersByVisibleTextAndDescendantText() {
        final var root = new DefaultMutableTreeNode("root");
        final var matching = node("Matching");
        matching.add(node("Nested target"));
        root.add(matching);
        root.add(node("Other"));
        final var model = new FilteredTreeModel(root);

        model.setTerm(" TARGET ");

        assertEquals(1, model.getChildCount(root), "matching parent should remain visible");
        assertEquals(matching, model.getChild(root, 0), "matching parent should be returned");
        assertEquals(
                0, model.getIndexOfChild(root, matching), "matching index should be visible index");
        assertFalse(model.isLeaf(root), "root has a visible child");
        assertFalse(model.isLeaf(matching), "matching parent has a visible descendant");
    }

    @Test
    void nullAndBlankTermsRestoreAllChildren() {
        final var root = new DefaultMutableTreeNode("root");
        root.add(node("One"));
        root.add(node("Two"));
        final var model = new FilteredTreeModel(root);

        model.setTerm("one");
        assertEquals(1, model.getChildCount(root), "term should hide non-matching children");
        model.setTerm(null);
        assertEquals(2, model.getChildCount(root), "null should clear the filter");
        model.setTerm("   ");
        assertEquals(2, model.getChildCount(root), "blank should clear the filter");
    }

    @Test
    void mutationsWorkWhileFilteredAndRejectOrphanRemoval() {
        final var root = new DefaultMutableTreeNode("root");
        root.add(node("One"));
        final var model = new FilteredTreeModel(root);
        model.setTerm("one");
        final var added = node("Added");

        model.insertNodeInto(added, root, 1);
        assertEquals(1, model.getChildCount(root), "added non-matching node stays hidden");
        model.removeNodeFromParent(added);
        assertEquals(1, model.getChildCount(root), "removing hidden node keeps match visible");
        assertThrows(
                IllegalArgumentException.class,
                () -> model.removeNodeFromParent(new DefaultMutableTreeNode("orphan")),
                "removing an orphan should fail");
        model.nodeChanged(root);
    }

    private static DefaultMutableTreeNode node(final String text) {
        return new DefaultMutableTreeNode(new TestItem(text));
    }

    private record TestItem(String text) implements TreeItem {
        @Override
        public String getId() {
            return text;
        }

        @Override
        public String getTooltip() {
            return text;
        }

        @Override
        public CellRenderContents getCellRenderContents() {
            return new CellRenderContents(text, null);
        }

        @Override
        public javax.swing.JPopupMenu getMenu() {
            return null;
        }

        @Override
        public void select() {}
    }
}
