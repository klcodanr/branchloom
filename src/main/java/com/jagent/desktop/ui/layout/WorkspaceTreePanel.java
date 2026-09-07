package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.services.BackgroundTasks;
import com.jagent.desktop.services.Git;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.WorkspaceFiles;
import com.jagent.desktop.ui.actions.CopyPathAction;
import com.jagent.desktop.ui.actions.OpenDirectoryAction;
import com.jagent.desktop.ui.actions.RunCommandAction;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

/** Read-only, lazy-loaded workspace navigator. */
public final class WorkspaceTreePanel extends JPanel {
    private static final String LOADING = "Loading...";
    private static final String EMPTY = "Empty";
    private final transient ActionContext actionContext;
    private final transient WorkspaceFiles workspaceFiles;
    private final transient Consumer<Path> openTerminal;
    private final transient BiConsumer<Path, Boolean> openFile;
    private final Path workspace;
    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private volatile Map<String, String> statuses = Map.of();
    private GitStatusPanel statusPanel;
    private SmIconButton refreshButton;
    private SmIconButton changedOnlyButton;
    private SmIconButton comparisonButton;
    private Runnable hideAction = () -> {};
    private boolean compareSourceBranch;
    private Set<Path> expandedPaths = Set.of();
    private Path selectedPath;

    public WorkspaceTreePanel(
            final ActionContext actionContext,
            final Path workspace,
            final Consumer<Path> openTerminal,
            final BiConsumer<Path, Boolean> openFile) {
        super(new BorderLayout());
        this.actionContext = actionContext;
        setOpaque(false);
        this.workspace = workspace.toAbsolutePath().normalize();
        this.workspaceFiles = new WorkspaceFiles(this.workspace);
        this.openTerminal = openTerminal;
        this.openFile = openFile;
        this.root = node(workspace);
        add(header(), BorderLayout.NORTH);
        this.tree = new WorkspaceTree();
        add(new JScrollPane(tree), BorderLayout.CENTER);
        loadChildren(root);
        refreshStatus();
    }

    private JPanel header() {
        final JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, UiConstants.CONTENT_PADDING, 0));
        statusPanel = new GitStatusPanel();
        refreshButton = new SmIconButton("Refresh Git status", UiIcons.refresh());
        refreshButton.addActionListener(ignored -> refreshStatus());
        changedOnlyButton = new SmIconButton("Show changed files only", UiIcons.funnel());
        changedOnlyButton.setSelectedIcon(UiIcons.funnelX());
        changedOnlyButton.addActionListener(ignored -> reloadWorkspace());
        comparisonButton =
                new SmIconButton("Comparison scope: Working tree", UiIcons.gitCompareArrows());
        comparisonButton.getAccessibleContext().setAccessibleName("Comparison scope");
        comparisonButton.addActionListener(
                ignored ->
                        SwingUtilities.invokeLater(
                                () -> {
                                    comparisonButton.setSelected(compareSourceBranch);
                                    comparisonMenu()
                                            .show(
                                                    comparisonButton,
                                                    0,
                                                    comparisonButton.getHeight());
                                }));
        final JButton hideButton = UiFactory.iconButton(UiIcons.chevronRight(), "Hide files");
        hideButton.addActionListener(ignored -> hideAction.run());
        final JPanel buttons =
                new JPanel(new FlowLayout(FlowLayout.RIGHT, UiConstants.SPACING_XS, 0));
        buttons.setOpaque(false);
        buttons.add(refreshButton);
        buttons.add(changedOnlyButton);
        buttons.add(comparisonButton);
        buttons.add(hideButton);
        final JPanel statusWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        statusWrapper.setOpaque(false);
        statusPanel.setAlignmentY(CENTER_ALIGNMENT);
        statusWrapper.add(statusPanel);
        final JPanel actions = new JPanel(new BorderLayout(UiConstants.SPACING_XS, 0));
        actions.setOpaque(false);
        actions.add(statusWrapper, BorderLayout.CENTER);
        actions.add(buttons, BorderLayout.EAST);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    public void setHideAction(final Runnable hideAction) {
        this.hideAction = hideAction;
    }

    private JPopupMenu comparisonMenu() {
        final JPopupMenu menu = new JPopupMenu();
        final JMenuItem local = new JMenuItem("Working tree only");
        local.addActionListener(ignored -> setComparisonScope(false));
        menu.add(local);
        final JMenuItem source = new JMenuItem("Current branch since upstream");
        source.addActionListener(ignored -> setComparisonScope(true));
        menu.add(source);
        return menu;
    }

    private void setComparisonScope(final boolean sourceBranch) {
        compareSourceBranch = sourceBranch;
        comparisonButton.setSelected(sourceBranch);
        comparisonButton.setToolTipText(
                sourceBranch
                        ? "Comparison scope: Current branch since upstream"
                        : "Comparison scope: Working tree");
        refreshStatus();
    }

    private void refreshStatus() {
        final boolean includeSourceBranch = compareSourceBranch;
        refreshButton.setSelected(false);
        refreshButton.setEnabled(false);
        statusPanel.showRefreshing();
        BackgroundTasks.submit(
                        "Workspace",
                        "git-status",
                        () -> {
                            try {
                                return Git.worktreeStatus(workspace, includeSourceBranch);
                            } catch (IOException failure) {
                                throw new CompletionException(failure);
                            } catch (InterruptedException failure) {
                                Thread.currentThread().interrupt();
                                throw new CompletionException(failure);
                            }
                        })
                .thenAcceptAsync(
                        updated -> {
                            statuses = updated.files();
                            statusPanel.showStatus(updated);
                            refreshButton.setEnabled(true);
                            tree.repaint();
                            reloadWorkspace();
                        },
                        SwingUtilities::invokeLater)
                .exceptionally(
                        failure -> {
                            SwingUtilities.invokeLater(
                                    () -> {
                                        statuses = Map.of();
                                        statusPanel.showUnavailable("Git status unavailable");
                                        refreshButton.setEnabled(true);
                                        tree.repaint();
                                    });
                            return null;
                        });
    }

    private void reloadWorkspace() {
        saveTreeState();
        root.removeAllChildren();
        root.add(new DefaultMutableTreeNode(LOADING));
        ((DefaultTreeModel) tree.getModel()).reload(root);
        loadChildren(root);
    }

    private void saveTreeState() {
        final Set<Path> expanded = new HashSet<>();
        final var paths = tree.getExpandedDescendants(new TreePath(root.getPath()));
        if (paths != null) {
            while (paths.hasMoreElements()) {
                final Object value = paths.nextElement().getLastPathComponent();
                if (value instanceof DefaultMutableTreeNode node
                        && node.getUserObject() instanceof Path path) {
                    expanded.add(path);
                }
            }
        }
        expandedPaths = Set.copyOf(expanded);
        final Object selected = tree.getLastSelectedPathComponent();
        selectedPath =
                selected instanceof DefaultMutableTreeNode node
                                && node.getUserObject() instanceof Path path
                        ? path
                        : null;
    }

    private DefaultMutableTreeNode node(final Path path) {
        final DefaultMutableTreeNode node = new DefaultMutableTreeNode(path);
        if (directory(path)) {
            node.add(new DefaultMutableTreeNode(LOADING));
        }
        return node;
    }

    private void loadChildren(final DefaultMutableTreeNode parent) {
        final Object value = parent.getUserObject();
        if (!(value instanceof Path directory)) {
            return;
        }
        final boolean changedOnly = changedOnlyButton.isSelected();
        BackgroundTasks.submit(
                "Workspace",
                "load-files",
                () -> {
                    final List<Path> children;
                    try {
                        children = workspaceFiles.children(directory);
                    } catch (IOException failure) {
                        SwingUtilities.invokeLater(
                                () -> {
                                    parent.removeAllChildren();
                                    parent.add(new DefaultMutableTreeNode("Unavailable"));
                                    ((DefaultTreeModel) tree.getModel()).reload(parent);
                                });
                        return;
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    final List<DefaultMutableTreeNode> childNodes =
                            children.stream()
                                    .filter(path -> !changedOnly || changed(path))
                                    .map(this::node)
                                    .toList();
                    SwingUtilities.invokeLater(
                            () -> {
                                parent.removeAllChildren();
                                if (childNodes.isEmpty()) {
                                    parent.add(new DefaultMutableTreeNode(EMPTY));
                                } else {
                                    childNodes.forEach(parent::add);
                                }
                                ((DefaultTreeModel) tree.getModel()).reload(parent);
                                tree.expandPath(new TreePath(parent.getPath()));
                                restoreTreeState();
                            });
                });
    }

    private void restoreTreeState() {
        for (final Path path : expandedPaths) {
            final DefaultMutableTreeNode node = findNode(root, path);
            if (node != null) {
                tree.expandPath(new TreePath(node.getPath()));
            }
        }
        if (selectedPath != null) {
            final DefaultMutableTreeNode node = findNode(root, selectedPath);
            if (node != null) {
                tree.setSelectionPath(new TreePath(node.getPath()));
                selectedPath = null;
            }
        }
    }

    private DefaultMutableTreeNode findNode(final DefaultMutableTreeNode parent, final Path path) {
        if (path.equals(parent.getUserObject())) {
            return parent;
        }
        for (int index = 0; index < parent.getChildCount(); index++) {
            final DefaultMutableTreeNode node =
                    findNode((DefaultMutableTreeNode) parent.getChildAt(index), path);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    private void openSelected(final Path path) {
        if (directory(path)) {
            return;
        }
        openFile.accept(path, changedOnlyButton.isSelected());
    }

    private void openInEditor(final Path path) {
        if (directory(path)) {
            return;
        }
        final Path pathParent = path.getParent();
        final Path parent = pathParent == null ? path : pathParent;
        final var tools = actionContext.appState().appSettings().tools();
        if (tools.isEmpty()) {
            OpenDirectoryAction.open(parent.toString(), actionContext.window());
            return;
        }
        final var editor = tools.getFirst();
        RunCommandAction.run(
                editor.command() + " " + PlatformCommands.shellQuote(path.toString()),
                parent.toString(),
                editor.label(),
                actionContext.window());
    }

    private void showMenu(final MouseEvent event) {
        final TreePath treePath = tree.getPathForLocation(event.getX(), event.getY());
        if (treePath == null) {
            return;
        }
        showMenuAt(treePath, event.getX(), event.getY());
    }

    private boolean directory(final Path path) {
        return Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS);
    }

    private boolean changed(final Path path) {
        final String relative =
                workspace
                        .relativize(path.toAbsolutePath().normalize())
                        .toString()
                        .replace(java.io.File.separatorChar, '/');
        return statuses.containsKey(relative)
                || statuses.keySet().stream().anyMatch(value -> value.startsWith(relative + "/"));
    }

    private Path parentOrSelf(final Path path) {
        final Path parent = path.getParent();
        return parent == null ? path : parent;
    }

    private final class WorkspaceTree extends JTree {
        private WorkspaceTree() {
            super(new DefaultTreeModel(root));
            setRootVisible(true);
            setShowsRootHandles(true);
            setCellRenderer(new WorkspaceRenderer());
            getAccessibleContext().setAccessibleName("Workspace files");
            getInputMap(WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open-selected-file");
            getActionMap()
                    .put(
                            "open-selected-file",
                            new AbstractAction() {
                                @Override
                                public void actionPerformed(
                                        final java.awt.event.ActionEvent event) {
                                    openSelectedPath();
                                }
                            });
            getInputMap(WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0), "show-context-menu");
            getInputMap(WHEN_FOCUSED)
                    .put(
                            KeyStroke.getKeyStroke(KeyEvent.VK_F10, KeyEvent.SHIFT_DOWN_MASK),
                            "show-context-menu");
            getActionMap()
                    .put(
                            "show-context-menu",
                            new AbstractAction() {
                                @Override
                                public void actionPerformed(
                                        final java.awt.event.ActionEvent event) {
                                    showKeyboardMenu();
                                }
                            });
            installExpansionListener();
            installMouseListener();
        }

        private void openSelectedPath() {
            final TreePath selected = getSelectionPath();
            if (selected == null) {
                return;
            }
            final Object value =
                    ((DefaultMutableTreeNode) selected.getLastPathComponent()).getUserObject();
            if (value instanceof Path path) {
                openSelected(path);
            }
        }

        private void showKeyboardMenu() {
            final TreePath selected = getSelectionPath();
            if (selected == null) {
                return;
            }
            final java.awt.Rectangle bounds = getPathBounds(selected);
            if (bounds != null) {
                showMenuAt(selected, bounds.x, bounds.y + bounds.height);
            }
        }

        private void installExpansionListener() {
            addTreeWillExpandListener(
                    new javax.swing.event.TreeWillExpandListener() {
                        @Override
                        public void treeWillExpand(
                                final javax.swing.event.TreeExpansionEvent event) {
                            final DefaultMutableTreeNode node =
                                    (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                            if (node.getChildCount() == 1
                                    && LOADING.equals(
                                            ((DefaultMutableTreeNode) node.getChildAt(0))
                                                    .getUserObject())) {
                                loadChildren(node);
                            }
                        }

                        @Override
                        public void treeWillCollapse(
                                final javax.swing.event.TreeExpansionEvent event) {}
                    });
        }

        private void installMouseListener() {
            addMouseListener(
                    new MouseAdapter() {
                        @Override
                        public void mouseClicked(final MouseEvent event) {
                            if (event.getButton() == MouseEvent.BUTTON1
                                    && event.getClickCount() == 2) {
                                final TreePath path =
                                        getPathForLocation(event.getX(), event.getY());
                                if (path != null) {
                                    final Object value =
                                            ((DefaultMutableTreeNode) path.getLastPathComponent())
                                                    .getUserObject();
                                    if (value instanceof Path selected) {
                                        openSelected(selected);
                                    }
                                }
                            }
                        }

                        @Override
                        public void mousePressed(final MouseEvent event) {
                            if (event.isPopupTrigger()) {
                                showMenu(event);
                            }
                        }

                        @Override
                        public void mouseReleased(final MouseEvent event) {
                            if (event.isPopupTrigger()) {
                                showMenu(event);
                            }
                        }
                    });
        }
    }

    private void showMenuAt(final TreePath treePath, final int x, final int y) {
        tree.setSelectionPath(treePath);
        final Object value =
                ((DefaultMutableTreeNode) treePath.getLastPathComponent()).getUserObject();
        if (!(value instanceof Path path)) {
            return;
        }
        final JPopupMenu menu = new JPopupMenu();
        if (!directory(path)) {
            final JMenuItem open = new JMenuItem("Open in editor");
            open.addActionListener(ignored -> openInEditor(path));
            menu.add(open);
        }
        final JMenuItem reveal = new JMenuItem("Reveal in file manager");
        reveal.addActionListener(
                ignored ->
                        OpenDirectoryAction.open(
                                directory(path) ? path.toString() : parentOrSelf(path).toString(),
                                actionContext.window()));
        menu.add(reveal);
        final JMenuItem terminal = new JMenuItem("Open terminal here");
        terminal.addActionListener(
                ignored -> openTerminal.accept(directory(path) ? path : parentOrSelf(path)));
        menu.add(terminal);
        final JMenuItem copy = new JMenuItem("Copy path");
        copy.addActionListener(ignored -> CopyPathAction.copy(path.toAbsolutePath().toString()));
        menu.add(copy);
        UiFactory.showPopupMenu(menu, tree, x, y);
    }

    private final class WorkspaceRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(
                final JTree tree,
                final Object value,
                final boolean selected,
                final boolean expanded,
                final boolean leaf,
                final int row,
                final boolean focused) {
            final Component component =
                    super.getTreeCellRendererComponent(
                            tree, value, selected, expanded, leaf, row, focused);
            final Object item = ((DefaultMutableTreeNode) value).getUserObject();
            if (item instanceof Path path) {
                final String status = statusCode(path);
                setText(fileName(path) + (status == null ? "" : " [" + status.trim() + "]"));
                setToolTipText(path.toString());
                if (!selected && status != null) {
                    setForeground(statusColor(status));
                }
            } else if (LOADING.equals(item)) {
                setText(LOADING);
                setIcon(UiIcons.activity());
            }
            return component;
        }

        private String statusCode(final Path path) {
            final String relative =
                    workspace
                            .relativize(path.toAbsolutePath().normalize())
                            .toString()
                            .replace(java.io.File.separatorChar, '/');
            final String code = statuses.get(relative);
            if (code != null) {
                return code;
            }
            return changed(path) ? " M" : null;
        }

        private Color statusColor(final String code) {
            if (code.contains("D") || code.contains("U")) {
                return Theme.dangerColor();
            }
            if (code.contains("A") || code.contains("?")) {
                return Theme.successColor();
            }
            return Theme.warningColor();
        }
    }

    private String fileName(final Path path) {
        final Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }
}
