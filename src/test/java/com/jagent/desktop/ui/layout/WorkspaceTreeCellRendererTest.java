package com.jagent.desktop.ui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class WorkspaceTreeCellRendererTest {
    private static final String WORKSPACE = "workspace";

    @Test
    void rendersCombinedPathRelativeToParent() {
        final Path workspace = Path.of(WORKSPACE).toAbsolutePath().normalize();
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
        final Path workspace = Path.of(WORKSPACE).toAbsolutePath().normalize();
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

    @Test
    void rendersWorkspaceRootAndFallbackFileNameWithoutStatus() {
        final Path workspace = Path.of(WORKSPACE).toAbsolutePath().normalize();
        final var root = new DefaultMutableTreeNode(workspace);
        final var file = new DefaultMutableTreeNode(workspace.resolve("file.txt"));
        root.add(file);
        final JTree tree = GuiActionRunner.execute(() -> new JTree(new DefaultTreeModel(root)));
        final var renderer = new WorkspaceTreeCellRenderer(workspace, path -> " ");
        final Path workspaceName = workspace.getFileName();
        assertNotNull(workspaceName, "test workspace should have a file name");

        assertEquals(
                workspaceName.toString(),
                render(renderer, tree, root).getText(),
                "workspace root should use its file name");
        assertEquals(
                "file.txt", render(renderer, tree, file).getText(), "file name should be shown");
        final Path normalizedFile = workspace.resolve("file.txt");
        assertEquals(
                normalizedFile.toString(),
                render(renderer, tree, file).getToolTipText(),
                "file tooltip should use the normalized path");
    }

    @Test
    void rendersStatusColorsForSelectedAndUnselectedChanges() {
        final Path workspace = Path.of("workspace").toAbsolutePath().normalize();
        final var root = new DefaultMutableTreeNode(workspace);
        final var deleted = new DefaultMutableTreeNode(workspace.resolve("deleted.txt"));
        final var added = new DefaultMutableTreeNode(workspace.resolve("added.txt"));
        final var changed = new DefaultMutableTreeNode(workspace.resolve("changed.txt"));
        root.add(deleted);
        root.add(added);
        root.add(changed);
        final JTree tree = GuiActionRunner.execute(() -> new JTree(new DefaultTreeModel(root)));
        final var renderer =
                new WorkspaceTreeCellRenderer(
                        workspace,
                        path ->
                                path.endsWith("deleted.txt")
                                        ? " D"
                                        : path.endsWith("added.txt") ? " A" : " M");

        final JLabel deletedLabel = render(renderer, tree, deleted);
        final String deletedText = deletedLabel.getText();
        final var deletedColor = deletedLabel.getForeground();
        final JLabel addedLabel = render(renderer, tree, added);
        final String addedText = addedLabel.getText();
        final var addedColor = addedLabel.getForeground();
        final JLabel selectedLabel = renderSelected(renderer, tree, changed);
        final var selectedColor = selectedLabel.getForeground();

        assertEquals("deleted.txt [D]", deletedText, "deleted status should be rendered");
        assertEquals("added.txt [A]", addedText, "added status should be rendered");
        assertNotEquals(
                deletedColor,
                addedColor,
                "danger and success statuses should use different colors");
        assertEquals(
                renderer.getTextSelectionColor(),
                selectedColor,
                "selected paths should use the selection foreground color");
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

    private static JLabel renderSelected(
            final WorkspaceTreeCellRenderer renderer,
            final JTree tree,
            final DefaultMutableTreeNode node) {
        final TreePath path = new TreePath(node.getPath());
        return (JLabel)
                renderer.getTreeCellRendererComponent(
                        tree,
                        node,
                        true,
                        tree.isExpanded(path),
                        node.isLeaf(),
                        tree.getRowForPath(path),
                        false);
    }
}
