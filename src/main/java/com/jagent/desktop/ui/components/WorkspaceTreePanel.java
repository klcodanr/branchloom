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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
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
    private final Path workspace;
    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private Map<String, String> statuses = Map.of();
    private JLabel statusLabel;
    private JButton refreshButton;

    public WorkspaceTreePanel(
            final ActionContext actionContext,
            final Path workspace,
            final Consumer<Path> openTerminal) {
        super(new BorderLayout());
        this.actionContext = actionContext;
        this.workspace = workspace.toAbsolutePath().normalize();
        this.workspaceFiles = new WorkspaceFiles(this.workspace);
        this.openTerminal = openTerminal;
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
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        final JLabel pathLabel = UiFactory.label(workspace.toString(), Theme.FontSize.XS);
        pathLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
        pathLabel.setToolTipText(workspace.toString());
        header.add(pathLabel, BorderLayout.WEST);
        statusLabel = UiFactory.label("", Theme.FontSize.XS);
        header.add(statusLabel, BorderLayout.CENTER);
        refreshButton = UiFactory.iconButton(UiIcons.refresh());
        refreshButton.setToolTipText("Refresh Git status");
        refreshButton.getAccessibleContext().setAccessibleName("Refresh Git status");
        refreshButton.addActionListener(ignored -> refreshStatus());
        header.add(refreshButton, BorderLayout.EAST);
        return header;
    }

    private void refreshStatus() {
        refreshButton.setEnabled(false);
        statusLabel.setText("Refreshing Git status...");
        BackgroundTasks.submit(
                        "Workspace",
                        "git-status",
                        () -> {
                            try {
                                return Git.statusFiles(workspace);
                            } catch (IOException failure) {
                                throw new CompletionException(failure);
                            } catch (InterruptedException failure) {
                                Thread.currentThread().interrupt();
                                throw new CompletionException(failure);
                            }
                        })
                .thenAcceptAsync(
                        updated -> {
                            statuses = updated;
                            statusLabel.setText(statuses.size() + " changed");
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
                                        statusLabel.setText("Git status unavailable");
                                        refreshButton.setEnabled(true);
                                        tree.repaint();
                                    });
                            return null;
                        });
    }

    private void reloadWorkspace() {
        root.removeAllChildren();
        root.add(new DefaultMutableTreeNode(LOADING));
        ((DefaultTreeModel) tree.getModel()).reload(root);
        loadChildren(root);
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
                            children.stream().map(this::node).toList();
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
                            });
                });
    }

    private void openSelected(final Path path) {
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
        tree.setSelectionPath(treePath);
        final Object value =
                ((DefaultMutableTreeNode) treePath.getLastPathComponent()).getUserObject();
        if (!(value instanceof Path path)) {
            return;
        }
        final JPopupMenu menu = new JPopupMenu();
        if (!directory(path)) {
            final JMenuItem open = new JMenuItem("Open in editor");
            open.addActionListener(ignored -> openSelected(path));
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
        menu.show(tree, event.getX(), event.getY());
    }

    private boolean directory(final Path path) {
        return Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS);
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
            installExpansionListener();
            installMouseListener();
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
            final String prefix = relative + "/";
            return statuses.keySet().stream().anyMatch(value -> value.startsWith(prefix))
                    ? " M"
                    : null;
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
