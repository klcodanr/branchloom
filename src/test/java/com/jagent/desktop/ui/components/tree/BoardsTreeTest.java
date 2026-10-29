package com.jagent.desktop.ui.components.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.jagent.desktop.test.TestAppState;
import com.jagent.desktop.ui.components.tree.items.BoardTreeItem;
import com.jagent.desktop.ui.components.tree.items.BoardsTreeItem;
import javax.swing.tree.DefaultMutableTreeNode;
import org.junit.jupiter.api.Test;

class BoardsTreeTest {
    @Test
    void createsSortedBoardNodesAndRebuildsThem() {
        final var state = TestAppState.empty();
        final var tree = new BoardsTree(TestAppState.context(state));
        final var root = tree.getNode();

        assertInstanceOf(
                BoardsTreeItem.class, root.getUserObject(), "root should represent boards");
        assertEquals(2, root.getChildCount(), "default boards should be present");
        assertEquals(
                "My Work",
                board(root, 0).getCellRenderContents().text(),
                "boards should be sorted");
        assertEquals(
                "Reviews",
                board(root, 1).getCellRenderContents().text(),
                "boards should be sorted");

        tree.rebuild();

        assertEquals(2, root.getChildCount(), "rebuild should restore board nodes");
        assertEquals("My Work", board(root, 0).getId(), "rebuild should preserve sorting");
    }

    private static BoardTreeItem board(final DefaultMutableTreeNode root, final int index) {
        return (BoardTreeItem) ((DefaultMutableTreeNode) root.getChildAt(index)).getUserObject();
    }
}
