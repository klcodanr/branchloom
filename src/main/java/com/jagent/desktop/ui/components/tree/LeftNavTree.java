package com.jagent.desktop.ui.components.tree;

import com.jagent.desktop.models.AppSettingsId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.ui.components.ProjectActions;
import com.jagent.desktop.ui.components.SessionActions;
import com.jagent.desktop.ui.components.UiPopupMenus;
import com.jagent.desktop.ui.components.tree.items.DefaultTreeItem;
import com.jagent.desktop.ui.components.tree.items.GroupTreeItem;
import com.jagent.desktop.ui.components.tree.items.PullRequestsItem;
import com.jagent.desktop.ui.components.tree.items.TreeItem;
import com.jediterm.core.compatibility.Point;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

public final class LeftNavTree extends JTree {
    private final Consumer<String> onSearch;
    private final JTree tree = this;
    private final DefaultMutableTreeNode root;
    private final DefaultMutableTreeNode pullRequestsNode;
    private final transient BoardsTree boardsTree;
    private final transient ProjectsTree projectsTree;

    private final FilteredTreeModel filteredTreeModel;

    private DefaultMutableTreeNode preFilterSelectedNode;

    @SuppressWarnings("PMD.CognitiveComplexity")
    public LeftNavTree(
            final Consumer<String> onSearch,
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService,
            final SessionActions sessionActions,
            final ProjectActions projectActions) {
        super(new FilteredTreeModel(new DefaultMutableTreeNode("Projects")));
        final var root = (DefaultMutableTreeNode) getModel().getRoot();
        final var filteredTreeModel = (FilteredTreeModel) getModel();
        this.onSearch = onSearch;
        this.filteredTreeModel = filteredTreeModel;
        this.root = root;
        setRootVisible(false);
        setShowsRootHandles(true);
        final var defaultRenderer = new DefaultTreeCellRenderer();

        setCellRenderer(
                (tree, value, selected, expanded, leaf, row, focused) -> {
                    final Component component =
                            defaultRenderer.getTreeCellRendererComponent(
                                    tree, value, selected, expanded, leaf, row, focused);
                    final Object item = ((DefaultMutableTreeNode) value).getUserObject();

                    if (item instanceof TreeItem treeItem) {
                        final var contents = treeItem.getCellRenderContents();
                        defaultRenderer.setText(contents.text());
                        defaultRenderer.setIcon(contents.icon());
                    }

                    return component;
                });
        getAccessibleContext().setAccessibleName("Projects and sessions");
        ToolTipManager.sharedInstance().registerComponent(this);
        getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        getInputMap(WHEN_FOCUSED)
                .put(
                        KeyStroke.getKeyStroke(KeyEvent.VK_F10, KeyEvent.SHIFT_DOWN_MASK),
                        "show-viewController, appState, window, dataDirectory, refreshSettings, githubProvider, dialogService-menu");
        getInputMap(WHEN_FOCUSED)
                .put(
                        KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0),
                        "show-viewController, appState, window, dataDirectory, refreshSettings, githubProvider, dialogService-menu");
        getActionMap()
                .put(
                        "show-viewController, appState, window, dataDirectory, refreshSettings, githubProvider, dialogService-menu",
                        new AbstractAction() {
                            @Override
                            public void actionPerformed(final ActionEvent event) {
                                final TreePath path = tree.getLeadSelectionPath();
                                if (path == null) {
                                    return;
                                }
                                final Rectangle bounds = tree.getPathBounds(path);
                                if (bounds != null) {
                                    final Point location =
                                            new Point(bounds.x, bounds.y + bounds.height);
                                    treeItem(path)
                                            .ifPresent(
                                                    treeItem ->
                                                            UiPopupMenus.show(
                                                                    treeItem.getMenu(),
                                                                    tree,
                                                                    location.x,
                                                                    location.y));
                                }
                            }
                        });
        addTreeSelectionListener(
                event -> {
                    final TreePath path = event.getNewLeadSelectionPath();
                    if (path == null) {
                        return;
                    }

                    treeItem(path).ifPresent(TreeItem::select);

                    if (this.preFilterSelectedNode != null) {
                        final Object component = path.getLastPathComponent();
                        if (component instanceof DefaultMutableTreeNode) {
                            this.preFilterSelectedNode = null;
                        }
                    }
                });
        addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(final MouseEvent event) {
                        this.handleEvent(event);
                    }

                    @Override
                    public void mouseReleased(final MouseEvent event) {
                        this.handleEvent(event);
                    }

                    private void handleEvent(final MouseEvent event) {
                        if (event.isPopupTrigger()) {
                            final TreePath path = contextMenuPath(event.getX(), event.getY());
                            if (path == null) {
                                return;
                            }
                            tree.setSelectionPath(path);
                            final var item = treeItem(path);
                            if (item.isPresent()) {
                                UiPopupMenus.show(
                                        item.get().getMenu(), tree, event.getX(), event.getY());
                            } else {
                                UiPopupMenus.show(
                                        new DefaultTreeItem(viewController, appState, dialogService)
                                                .getMenu(),
                                        tree,
                                        event.getX(),
                                        event.getY());
                            }
                        }
                    }

                    private TreePath contextMenuPath(final int x, final int y) {
                        TreePath path = tree.getPathForLocation(x, y);
                        if (path != null) {
                            return path;
                        }
                        path = tree.getClosestPathForLocation(0, y);
                        final Rectangle bounds = path == null ? null : tree.getPathBounds(path);
                        return bounds != null && y >= bounds.y && y < bounds.y + bounds.height
                                ? path
                                : null;
                    }
                });

        pullRequestsNode = new DefaultMutableTreeNode(new PullRequestsItem(viewController));
        root.add(pullRequestsNode);

        boardsTree = new BoardsTree(viewController, appState);
        root.add(boardsTree.getNode());

        projectsTree =
                new ProjectsTree(
                        viewController,
                        appState,
                        dialogService,
                        projectActions,
                        sessionActions,
                        filteredTreeModel,
                        this);
        projectsTree.getGroupNodes().forEach(root::add);

        this.filteredTreeModel.reload();

        expandNodes(false);

        appState.addEventListener(
                event -> {
                    if (event.target() == AppSettingsId.CURRENT) {
                        this.rebuild();
                    }
                });
    }

    @Override
    protected void processKeyEvent(final KeyEvent event) {
        if (event.getID() == KeyEvent.KEY_TYPED) {
            if (!Character.isISOControl(event.getKeyChar())) {
                this.preFilterSelectedNode =
                        (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
                onSearch.accept(String.valueOf(event.getKeyChar()));
            }
            event.consume();
            return;
        }
        super.processKeyEvent(event);
    }

    private Optional<TreeItem> treeItem(final TreePath path) {
        if (path == null) {
            return Optional.empty();
        }
        final Object component = path.getLastPathComponent();
        if (!(component instanceof DefaultMutableTreeNode node)) {
            return Optional.empty();
        }
        final Object userObject = node.getUserObject();
        return userObject instanceof TreeItem treeItem ? Optional.of(treeItem) : Optional.empty();
    }

    private void expandNodes(final boolean includeProjects) {

        // Expand all root nodes
        this.setExpandedState(new TreePath(root.getPath()), true);
        this.setExpandedState(new TreePath(boardsTree.getNode().getPath()), true);
        projectsTree
                .getGroupNodes()
                .forEach(
                        gn -> {
                            final var path = new TreePath(gn.getPath());
                            this.setExpandedState(path, true);
                            if (includeProjects) {
                                gn.children()
                                        .asIterator()
                                        .forEachRemaining(
                                                child -> {
                                                    final var childPath =
                                                            new TreePath(
                                                                    ((DefaultMutableTreeNode) child)
                                                                            .getPath());
                                                    this.setExpandedState(childPath, true);
                                                });
                            }
                        });
    }

    public void rebuild() {
        SwingUtilities.invokeLater(
                () -> {
                    boardsTree.rebuild();

                    final List<DefaultMutableTreeNode> nodesToRemove = new ArrayList<>();
                    root.children()
                            .asIterator()
                            .forEachRemaining(
                                    node -> {
                                        if (node instanceof DefaultMutableTreeNode treeNode
                                                && treeNode.getUserObject()
                                                        instanceof GroupTreeItem) {
                                            nodesToRemove.add(treeNode);
                                        }
                                    });
                    nodesToRemove.forEach(root::remove);
                    projectsTree.rebuild();
                    projectsTree.getGroupNodes().forEach(root::add);
                    this.filteredTreeModel.reload();
                    expandNodes(false);
                });
    }

    @Override
    public String getToolTipText(final MouseEvent event) {
        final TreePath path = getPathForLocation(event.getX(), event.getY());
        if (path == null) {
            return null;
        }
        return treeItem(path).map(TreeItem::getTooltip).orElse(null);
    }

    public void setSearchQuery(final String term) {
        filteredTreeModel.setTerm(term);

        if (term != null && !term.isEmpty()) {
            expandNodes(true);
        } else if (this.preFilterSelectedNode != null) {
            expandNodes(false);
            final var path = new TreePath(this.preFilterSelectedNode.getPath());
            this.setSelectionPath(path);
            this.preFilterSelectedNode = null;
        }
    }
}
