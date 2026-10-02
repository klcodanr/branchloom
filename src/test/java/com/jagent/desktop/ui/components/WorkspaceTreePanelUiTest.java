package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.test.SwingTestSupport;
import com.jagent.desktop.test.TestGitRepository;
import com.jagent.desktop.ui.Defaults;
import com.jagent.desktop.ui.layout.WorkspaceTreePanel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class WorkspaceTreePanelUiTest {
    private static final String NESTED = "nested";
    private static final String FILE = "file.txt";
    private static final String OPEN_SELECTED_FILE = "open-selected-file";

    @Test
    void exposesKeyboardActionsForSelectedWorkspaceItems() {
        final WorkspaceTreePanel panel = create(Path.of("workspace"));
        final JTree tree = tree(panel);

        assertEquals(
                OPEN_SELECTED_FILE,
                tree.getInputMap(JTree.WHEN_FOCUSED)
                        .get(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0)),
                "Enter should open the selected file");
        assertEquals(
                "show-context-menu",
                tree.getInputMap(JTree.WHEN_FOCUSED)
                        .get(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_CONTEXT_MENU, 0)),
                "context-menu key should expose file actions");
        assertEquals(
                "Workspace files",
                tree.getAccessibleContext().getAccessibleName(),
                "workspace tree should have an accessible name");
    }

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
    void filtersUnchangedFilesAndFoldsSingleChildDirectories()
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
            expandDirectory(panel, NESTED);
            waitForFile(panel, FILE);
            waitForLabel(panel, "~1");
            GuiActionRunner.execute(() -> changedOnlyButton(panel).doClick());

            SwingTestSupport.await(
                    () -> {
                        final var workspaceRoot = root(panel);
                        return workspaceRoot.getChildCount() == 1
                                && workspace
                                        .resolve(NESTED, FILE)
                                        .toAbsolutePath()
                                        .normalize()
                                        .equals(
                                                ((DefaultMutableTreeNode)
                                                                workspaceRoot.getChildAt(0))
                                                        .getUserObject());
                    },
                    "changed tree should fold the single-child directory");
            final var changedFile = (DefaultMutableTreeNode) root(panel).getChildAt(0);
            assertEquals(0, changedFile.getChildCount(), "folded changed file should be a leaf");
            assertEquals(
                    "nested/file.txt [M]",
                    rowText(panel, changedFile),
                    "folded file should render its workspace-relative path");
            waitForFileAbsent(panel, "clean.txt");
        } finally {
            try (var paths = Files.walk(workspace)) {
                paths.sorted(Comparator.reverseOrder()).forEach(WorkspaceTreePanelUiTest::delete);
            }
        }
    }

    @Test
    void keepsBranchDirectoriesAndChangedChildrenInChangedTree()
            throws IOException, InterruptedException {
        final Path workspace = Files.createTempDirectory("workspace-tree-branch-test");
        try {
            TestGitRepository.initialize(workspace);
            TestGitRepository.run(
                    workspace,
                    "mkdir -p src/a src/b && printf 'x' > src/a/x.txt"
                            + " && printf 'y' > src/b/y.txt"
                            + " && git add src && git commit -qm files");
            TestGitRepository.run(
                    workspace,
                    "printf 'changed' >> src/a/x.txt && printf 'changed' >> src/b/y.txt");

            final var panel = GuiActionRunner.execute(() -> create(workspace));
            waitForLabel(panel, "~2");
            GuiActionRunner.execute(() -> changedOnlyButton(panel).doClick());

            SwingTestSupport.await(
                    () -> root(panel).getChildCount() == 1,
                    "changed tree should contain the branch directory");
            final var src = (DefaultMutableTreeNode) root(panel).getChildAt(0);
            assertEquals(
                    workspace.resolve("src").toAbsolutePath().normalize(),
                    src.getUserObject(),
                    "branch directory path should be preserved");
            assertEquals(2, src.getChildCount(), "branch directory should keep both changed files");
            GuiActionRunner.execute(() -> tree(panel).expandPath(new TreePath(src.getPath())));
            final var first = (DefaultMutableTreeNode) src.getChildAt(0);
            final var second = (DefaultMutableTreeNode) src.getChildAt(1);
            assertEquals("a/x.txt [M]", rowText(panel, first), "first changed file should render");
            assertEquals(
                    "b/y.txt [M]", rowText(panel, second), "second changed file should render");
        } finally {
            try (var paths = Files.walk(workspace)) {
                paths.sorted(Comparator.reverseOrder()).forEach(WorkspaceTreePanelUiTest::delete);
            }
        }
    }

    @Test
    void showsDeletedFilesInChangedTree() throws IOException, InterruptedException {
        final Path workspace = Files.createTempDirectory("workspace-tree-deleted-test");
        try {
            TestGitRepository.initialize(workspace);
            TestGitRepository.run(
                    workspace,
                    "mkdir src && printf 'deleted' > src/a.txt"
                            + " && git add src/a.txt && git commit -qm files");
            TestGitRepository.run(workspace, "rm src/a.txt");

            final var panel = GuiActionRunner.execute(() -> create(workspace));
            waitForLabel(panel, "-1");
            GuiActionRunner.execute(() -> changedOnlyButton(panel).doClick());

            SwingTestSupport.await(
                    () -> root(panel).getChildCount() == 1,
                    "changed tree should contain the deleted file");
            assertEquals(
                    "src/a.txt [D]",
                    rowText(panel, (DefaultMutableTreeNode) root(panel).getChildAt(0)),
                    "deleted file should render its status");
        } finally {
            try (var paths = Files.walk(workspace)) {
                paths.sorted(Comparator.reverseOrder()).forEach(WorkspaceTreePanelUiTest::delete);
            }
        }
    }

    @Test
    void showsEmptyPlaceholderWhenNothingChanged() throws IOException, InterruptedException {
        final Path workspace = Files.createTempDirectory("workspace-tree-empty-diff-test");
        try {
            TestGitRepository.initialize(workspace);
            final var panel = GuiActionRunner.execute(() -> create(workspace));
            waitForLabel(panel, "Clean");
            GuiActionRunner.execute(() -> changedOnlyButton(panel).doClick());

            SwingTestSupport.await(
                    () -> root(panel).getChildCount() == 1,
                    "changed tree should show an empty placeholder");
            assertEquals(
                    "Empty",
                    ((DefaultMutableTreeNode) root(panel).getChildAt(0)).getUserObject(),
                    "empty changed tree should show the placeholder");
        } finally {
            try (var paths = Files.walk(workspace)) {
                paths.sorted(Comparator.reverseOrder()).forEach(WorkspaceTreePanelUiTest::delete);
            }
        }
    }

    @Test
    void refreshPreservesExpandedDirectoriesAndSelectedFile()
            throws IOException, InterruptedException {
        final Path workspace = Files.createTempDirectory("workspace-tree-refresh-test");
        try {
            TestGitRepository.initialize(workspace);
            TestGitRepository.run(
                    workspace,
                    "mkdir nested && printf 'file' > nested/file.txt"
                            + " && git add nested/file.txt && git commit -qm files");

            final var panel = GuiActionRunner.execute(() -> create(workspace));
            waitForFile(panel, NESTED);
            expandDirectory(panel, NESTED);
            waitForFile(panel, FILE);
            final var fileNode = findNode(root(panel), FILE);
            GuiActionRunner.execute(
                    () -> tree(panel).setSelectionPath(new TreePath(fileNode.getPath())));

            GuiActionRunner.execute(() -> refreshButton(panel).doClick());
            waitForSelection(panel, FILE);

            final var nestedNode = findNode(root(panel), NESTED);
            assertTrue(
                    tree(panel).isExpanded(new TreePath(nestedNode.getPath())),
                    "refresh should preserve expanded directories");
            assertEquals(
                    FILE,
                    fileName(
                            (Path)
                                    ((DefaultMutableTreeNode)
                                                    tree(panel).getLastSelectedPathComponent())
                                            .getUserObject()),
                    "refresh should preserve the selected file");
        } finally {
            try (var paths = Files.walk(workspace)) {
                paths.sorted(Comparator.reverseOrder()).forEach(WorkspaceTreePanelUiTest::delete);
            }
        }
    }

    @Test
    void enterOpensSelectedFileWithCurrentFilterFlag() throws IOException, InterruptedException {
        final Path workspace = Files.createTempDirectory("workspace-tree-open-test");
        final AtomicReference<Path> openedPath = new AtomicReference<>();
        final AtomicReference<Boolean> openedFiltered = new AtomicReference<>();
        try {
            TestGitRepository.initialize(workspace);
            TestGitRepository.run(
                    workspace,
                    "printf 'content' > file.txt && git add file.txt && git commit -qm file");
            TestGitRepository.run(workspace, "printf 'changed' >> file.txt");
            final var panel =
                    GuiActionRunner.execute(
                            () ->
                                    create(
                                            workspace,
                                            ignored -> {},
                                            (path, filtered) -> {
                                                openedPath.set(path);
                                                openedFiltered.set(filtered);
                                            }));
            waitForFile(panel, FILE);
            waitForLabel(panel, "~1");
            final var node = findNode(root(panel), FILE);
            GuiActionRunner.execute(
                    () -> tree(panel).setSelectionPath(new TreePath(node.getPath())));
            waitForSelection(panel, FILE);
            GuiActionRunner.execute(
                    () -> tree(panel).getActionMap().get(OPEN_SELECTED_FILE).actionPerformed(null));

            assertEquals(
                    workspace.resolve(FILE).toAbsolutePath().normalize(),
                    openedPath.get(),
                    "enter should open the selected file");
            assertEquals(
                    Boolean.FALSE, openedFiltered.get(), "filter flag should be false by default");
        } finally {
            try (var paths = Files.walk(workspace)) {
                paths.sorted(Comparator.reverseOrder()).forEach(WorkspaceTreePanelUiTest::delete);
            }
        }
    }

    @Test
    void enterIgnoresDirectorySelection() throws IOException, InterruptedException {
        final Path workspace = Files.createTempDirectory("workspace-tree-open-dir-test");
        final AtomicReference<Path> openedPath = new AtomicReference<>();
        try {
            TestGitRepository.initialize(workspace);
            TestGitRepository.run(
                    workspace,
                    "mkdir nested && printf 'value' > nested/file.txt"
                            + " && git add nested/file.txt && git commit -qm nested");
            final var panel =
                    GuiActionRunner.execute(
                            () ->
                                    create(
                                            workspace,
                                            ignored -> {},
                                            (path, filtered) -> openedPath.set(path)));
            waitForFile(panel, NESTED);
            final var node = findNode(root(panel), NESTED);
            GuiActionRunner.execute(
                    () -> tree(panel).setSelectionPath(new TreePath(node.getPath())));
            GuiActionRunner.execute(
                    () -> tree(panel).getActionMap().get(OPEN_SELECTED_FILE).actionPerformed(null));

            assertNull(openedPath.get(), "directories should not trigger file open actions");
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
                (ignored, filtered) -> {});
    }

    private static WorkspaceTreePanel create(
            final Path workspace,
            final java.util.function.Consumer<Path> openTerminal,
            final java.util.function.BiConsumer<Path, Boolean> openFile) {
        final var state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        return new WorkspaceTreePanel(
                new ActionContext(new ViewCoordinator(state), state, null),
                workspace,
                openTerminal,
                openFile);
    }

    private static JTree tree(final WorkspaceTreePanel panel) {
        return (JTree) ((JScrollPane) panel.getComponent(1)).getViewport().getView();
    }

    private static DefaultMutableTreeNode root(final WorkspaceTreePanel panel) {
        return (DefaultMutableTreeNode) tree(panel).getModel().getRoot();
    }

    private static JLabel statusLabel(final WorkspaceTreePanel panel) {
        final var actions =
                (java.awt.Container) ((java.awt.Container) panel.getComponent(0)).getComponent(0);
        final var label = findLabel(actions);
        if (label == null) {
            throw new AssertionError("status label not found");
        }
        return label;
    }

    private static JLabel findLabel(final java.awt.Container container) {
        for (final java.awt.Component component : container.getComponents()) {
            if (component instanceof JLabel label) {
                return label;
            }
            if (component instanceof java.awt.Container child) {
                final var found = findLabel(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
        SwingTestSupport.await(
                () -> {
                    final JTree tree = tree(panel);
                    final DefaultMutableTreeNode node =
                            findNode((DefaultMutableTreeNode) tree.getModel().getRoot(), directory);
                    if (node == null) {
                        return false;
                    }
                    tree.expandPath(new TreePath(node.getPath()));
                    return node.getChildCount() != 1
                            || !"Loading..."
                                    .equals(
                                            ((DefaultMutableTreeNode) node.getChildAt(0))
                                                    .getUserObject());
                },
                "workspace directory did not load: " + directory);
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

    private static void waitForSelection(final WorkspaceTreePanel panel, final String file)
            throws InterruptedException {
        SwingTestSupport.await(
                () -> {
                    final Object value = tree(panel).getLastSelectedPathComponent();
                    return value instanceof DefaultMutableTreeNode node
                            && node.getUserObject() instanceof Path path
                            && file.equals(fileName(path));
                },
                "workspace file was not reselected: " + file);
    }

    private static String fileName(final Path path) {
        final Path fileName = path.getFileName();
        return fileName == null ? null : fileName.toString();
    }

    private static String rowText(
            final WorkspaceTreePanel panel, final DefaultMutableTreeNode node) {
        final JTree workspaceTree = tree(panel);
        final TreePath path = new TreePath(node.getPath());
        final int row = workspaceTree.getRowForPath(path);
        final var rendered =
                workspaceTree
                        .getCellRenderer()
                        .getTreeCellRendererComponent(
                                workspaceTree,
                                node,
                                false,
                                workspaceTree.isExpanded(path),
                                node.isLeaf(),
                                row,
                                false);
        return ((JLabel) rendered).getText();
    }

    private static void waitForFile(final WorkspaceTreePanel panel, final String file)
            throws InterruptedException {
        SwingTestSupport.await(
                () -> contains((DefaultMutableTreeNode) tree(panel).getModel().getRoot(), file),
                "workspace file did not load: " + file);
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
        SwingTestSupport.await(
                () -> expected.equals(statusLabel(panel).getText()),
                "workspace status did not render: " + expected);
    }

    private static void waitForFileAbsent(final WorkspaceTreePanel panel, final String file)
            throws InterruptedException {
        SwingTestSupport.await(
                () -> !containsFile(tree(panel), file), "workspace file remained visible: " + file);
    }
}
