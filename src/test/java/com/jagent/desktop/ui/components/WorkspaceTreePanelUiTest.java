package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.test.TestGitRepository;
import com.jagent.desktop.ui.Defaults;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
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

    @Test
    void filtersUnchangedFilesWhileKeepingChangedDirectories()
            throws IOException, InterruptedException {
        final Path workspace = Files.createTempDirectory("workspace-tree-filter-test");
        try {
            TestGitRepository.initialize(workspace);
            TestGitRepository.run(
                    workspace,
                    "mkdir nested && printf 'clean' > clean.txt && printf 'nested' > nested/file.txt"
                            + " && git add clean.txt nested/file.txt && git commit -qm files");
            TestGitRepository.run(workspace, "printf 'changed' >> nested/file.txt");

            final var panel = GuiActionRunner.execute(() -> create(workspace));
            waitForFile(panel, "clean.txt");
            expandDirectory(panel, "nested");
            waitForFile(panel, "file.txt");
            waitForLabel(panel, "~1");
            GuiActionRunner.execute(() -> changedOnlyButton(panel).doClick());

            expandDirectory(panel, "nested");
            waitForFile(panel, "file.txt");
            waitForFileAbsent(panel, "clean.txt");
            assertTrue(
                    containsFile(tree(panel), "nested"),
                    "changed parent directories should remain");
        } finally {
            try (var paths = Files.walk(workspace)) {
                paths.sorted(Comparator.reverseOrder()).forEach(WorkspaceTreePanelUiTest::delete);
            }
        }
    }

    private static WorkspaceTreePanel create(final Path workspace) {
        final var state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        return new WorkspaceTreePanel(
                new ActionContext(new ViewCoordinator(state), state, null),
                workspace,
                ignored -> {},
                ignored -> {});
    }

    private static JTree tree(final WorkspaceTreePanel panel) {
        return (JTree) ((JScrollPane) panel.getComponent(1)).getViewport().getView();
    }

    private static JLabel statusLabel(final WorkspaceTreePanel panel) {
        final var actions =
                (java.awt.Container) ((java.awt.Container) panel.getComponent(0)).getComponent(0);
        return (JLabel) actions.getComponent(0);
    }

    private static javax.swing.AbstractButton refreshButton(final WorkspaceTreePanel panel) {
        return button(panel, "Refresh Git status");
    }

    private static JToggleButton changedOnlyButton(final WorkspaceTreePanel panel) {
        return (JToggleButton) button(panel, "Show changed files only");
    }

    private static javax.swing.AbstractButton button(
            final WorkspaceTreePanel panel, final String accessibleName) {
        final var button = findButton((java.awt.Container) panel.getComponent(0), accessibleName);
        if (button == null) {
            throw new AssertionError("button not found: " + accessibleName);
        }
        return button;
    }

    private static javax.swing.AbstractButton findButton(
            final java.awt.Container container, final String accessibleName) {
        for (final java.awt.Component component : container.getComponents()) {
            if (component instanceof javax.swing.AbstractButton button
                    && accessibleName.equals(button.getAccessibleContext().getAccessibleName())) {
                return button;
            }
            if (component instanceof java.awt.Container child) {
                final var found = findButton(child, accessibleName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void expandDirectory(final WorkspaceTreePanel panel, final String directory)
            throws InterruptedException {
        final long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            final boolean expanded =
                    GuiActionRunner.execute(
                            () -> {
                                final JTree tree = tree(panel);
                                final DefaultMutableTreeNode node =
                                        findNode(
                                                (DefaultMutableTreeNode) tree.getModel().getRoot(),
                                                directory);
                                if (node == null) {
                                    return false;
                                }
                                tree.expandPath(new TreePath(node.getPath()));
                                return node.getChildCount() != 1
                                        || !"Loading..."
                                                .equals(
                                                        ((DefaultMutableTreeNode)
                                                                        node.getChildAt(0))
                                                                .getUserObject());
                            });
            if (expanded) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("workspace directory did not load: " + directory);
    }

    private static DefaultMutableTreeNode findNode(
            final DefaultMutableTreeNode node, final String value) {
        if (node.getUserObject() instanceof Path path) {
            final Path fileName = path.getFileName();
            if (fileName != null && value.equals(fileName.toString())) {
                return node;
            }
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            final var found = findNode((DefaultMutableTreeNode) node.getChildAt(index), value);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String fileName(final Path path) {
        final Path fileName = path.getFileName();
        return fileName == null ? null : fileName.toString();
    }

    private static void waitForFile(final WorkspaceTreePanel panel, final String file)
            throws InterruptedException {
        final long deadline = System.nanoTime() + 5_000_000_000L;
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
        if (value.equals(node.getUserObject().toString())
                || node.getUserObject() instanceof Path path && value.equals(fileName(path))) {
            return true;
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            if (contains((DefaultMutableTreeNode) node.getChildAt(index), value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsFile(final JTree tree, final String fileName) {
        return containsFile((DefaultMutableTreeNode) tree.getModel().getRoot(), fileName);
    }

    private static boolean containsFile(final DefaultMutableTreeNode node, final String fileName) {
        if (node.getUserObject() instanceof Path path) {
            final String nodeFileName = fileName(path);
            if (fileName.equals(nodeFileName)) {
                return true;
            }
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            if (containsFile((DefaultMutableTreeNode) node.getChildAt(index), fileName)) {
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

    private static void waitForFileAbsent(final WorkspaceTreePanel panel, final String file)
            throws InterruptedException {
        final long deadline = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < deadline) {
            final boolean absent = GuiActionRunner.execute(() -> !containsFile(tree(panel), file));
            if (absent) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("workspace file remained visible: " + file);
    }
}
