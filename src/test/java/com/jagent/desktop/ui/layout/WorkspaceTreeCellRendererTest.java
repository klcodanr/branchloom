package com.jagent.desktop.ui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class WorkspaceTreeCellRendererTest {
    @Test
    void rendersCombinedPathRelativeToParent() {
        final Path workspace = Path.of("workspace").toAbsolutePath().normalize();
        final var root = new DefaultMutableTreeNode(workspace);
        final var file = new DefaultMutableTreeNode(workspace.resolve("nested/file.txt"));
        root.add(file);
        final JTree tree = GuiActionRunner.execute(() -> new JTree(new DefaultTreeModel(root)));

        final var renderer = new WorkspaceTreeCellRenderer(workspace, path -> " M");
        final JLabel label = render(renderer, tree, file);

        assertEquals("nested/file.txt [M]", label.getText(), "folded file should show its path");
    }

    @Test
    void rendersLoadingPlaceholder() {
        final Path workspace = Path.of("workspace").toAbsolutePath().normalize();
        final var root = new DefaultMutableTreeNode(workspace);
        final var loading = new DefaultMutableTreeNode("Loading...");
        root.add(loading);
        final JTree tree = GuiActionRunner.execute(() -> new JTree(new DefaultTreeModel(root)));
        final var renderer = new WorkspaceTreeCellRenderer(workspace, path -> null);

        assertEquals(
                "Loading...",
                render(renderer, tree, loading).getText(),
                "loading text should render");
    }

    private static JLabel render(
            final WorkspaceTreeCellRenderer renderer,
            final JTree tree,
            final DefaultMutableTreeNode node) {
        final TreePath path = new TreePath(node.getPath());
        return (JLabel)
                renderer.getTreeCellRendererComponent(
                        tree,
                        node,
                        false,
                        tree.isExpanded(path),
                        node.isLeaf(),
                        tree.getRowForPath(path),
                        false);
    }
}
