package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.Defaults;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class WorkspaceTreePanelUiTest {
    @Test
    void loadsWorkspaceFilesAndRendersGitStatus() throws IOException, InterruptedException {
        final Path workspace = Files.createTempDirectory("workspace-tree-test");
        try {
            final var panel = GuiActionRunner.execute(() -> create(workspace));
            waitForFile(panel, "Empty");

            final JTree tree = tree(panel);
            final var root = (DefaultMutableTreeNode) tree.getModel().getRoot();
            assertEquals(
                    workspace.toAbsolutePath().normalize(),
                    root.getUserObject(),
                    "workspace should be the tree root");
            assertTrue(root.getChildCount() >= 1, "workspace children should be loaded");
            assertNotNull(tree.getCellRenderer(), "workspace renderer should be installed");
        } finally {
            try (var paths = Files.walk(workspace)) {
                paths.sorted(Comparator.reverseOrder()).forEach(WorkspaceTreePanelUiTest::delete);
            }
        }
    }

    @Test
    void reportsGitStatusFailureWithoutAWindow() throws InterruptedException {
        final Path missing = Path.of("missing-workspace");
        final var panel = GuiActionRunner.execute(() -> create(missing));
        waitForLabel(panel, "Git status unavailable");

        assertEquals(
                "Git status unavailable",
                statusLabel(panel).getText(),
                "missing workspace should report status failure");
        assertTrue(
                refreshButton(panel).isEnabled(),
                "refresh should be re-enabled after status failure");
    }

    private static WorkspaceTreePanel create(final Path workspace) {
        final var state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        return new WorkspaceTreePanel(
                new ActionContext(new ViewCoordinator(state), state, null),
                workspace,
                ignored -> {});
    }

    private static JTree tree(final WorkspaceTreePanel panel) {
        return (JTree) ((JScrollPane) panel.getComponent(1)).getViewport().getView();
    }

    private static JLabel statusLabel(final WorkspaceTreePanel panel) {
        return (JLabel) ((java.awt.Container) panel.getComponent(0)).getComponent(1);
    }

    private static javax.swing.JButton refreshButton(final WorkspaceTreePanel panel) {
        return (javax.swing.JButton) ((java.awt.Container) panel.getComponent(0)).getComponent(2);
    }

    private static void waitForFile(final WorkspaceTreePanel panel, final String file)
            throws InterruptedException {
        final long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            final boolean loaded =
                    GuiActionRunner.execute(
                            () ->
                                    contains(
                                            (DefaultMutableTreeNode)
                                                    tree(panel).getModel().getRoot(),
                                            file));
            if (loaded) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("workspace file did not load: " + file);
    }

    private static boolean contains(final DefaultMutableTreeNode node, final String value) {
        if (value.equals(node.getUserObject().toString())) {
            return true;
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            if (contains((DefaultMutableTreeNode) node.getChildAt(index), value)) {
                return true;
            }
        }
        return false;
    }

    private static void delete(final Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException failure) {
            throw new IllegalStateException("temporary workspace cleanup failed", failure);
        }
    }

    private static void waitForLabel(final WorkspaceTreePanel panel, final String expected)
            throws InterruptedException {
        final long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            final boolean rendered =
                    GuiActionRunner.execute(() -> expected.equals(statusLabel(panel).getText()));
            if (rendered) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("workspace status did not render: " + expected);
    }
}
