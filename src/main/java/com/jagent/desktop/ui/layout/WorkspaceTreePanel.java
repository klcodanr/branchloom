package com.jagent.desktop.ui.layout;

import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.git.ChangedFileNode;
import com.jagent.desktop.models.git.ChangedFileTree;
import com.jagent.desktop.models.git.WorktreeStatusSummary;
import com.jagent.desktop.services.EditorCommands;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.services.github.GitHub;
import com.jagent.desktop.ui.actions.OpenDirectoryAction;
import com.jagent.desktop.ui.actions.RunCommandAction;
import com.jagent.desktop.ui.components.GitStatusPanel;
import com.jagent.desktop.ui.components.IconButton;
import com.jagent.desktop.ui.components.SmIconButton;
import com.jagent.desktop.ui.components.UiConstants;
import com.jagent.desktop.ui.components.UiIcons;
import com.jagent.desktop.ui.components.UiPopupMenus;
import com.jagent.desktop.ui.utils.PathUtils;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;

/** Read-only, lazy-loaded workspace navigator. */
@SuppressWarnings("PMD.GodClass")
public final class WorkspaceTreePanel extends JPanel {
    private static final String LOADING = "Loading...";
    private static final String EMPTY = "Empty";
    private final transient ActionContext actionContext;
    private final Path workspace;
    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private volatile Map<String, String> statuses = Map.of();
    private volatile Set<String> ignoredPaths = Set.of();
    private GitStatusPanel statusPanel;
    private SmIconButton refreshButton;
    private SmIconButton changedOnlyButton;
    private SmIconButton comparisonButton;
    private Runnable hideAction = () -> {};
    private boolean compareSourceBranch;
    private Set<Path> expandedPaths = Set.of();
    private Path selectedPath;

    private record DirectoryLoad(boolean unavailable, List<Path> children) {}

    public WorkspaceTreePanel(
            final ActionContext actionContext,
            final Path workspace,
            final Consumer<Path> openTerminal,
            final BiConsumer<Path, Boolean> openFile) {
        super(new BorderLayout());
        this.actionContext = actionContext;
        setOpaque(false);
        this.workspace = workspace.toAbsolutePath().normalize();

        this.root = node(workspace);
        add(header(), BorderLayout.NORTH);
        this.tree =
                new WorkspaceTree(
                        root,
                        workspace,
                        actionContext,
                        new WorkspaceTree.Actions(
                                path -> {
                                    if (!PathUtils.directory(path)) {
                                        openFile.accept(path, changedOnlyButton.isSelected());
                                    }
                                },
                                path -> {
                                    if (PathUtils.directory(path)) {
                                        return;
                                    }
                                    final Path parent = parentOrSelf(path);
                                    final var tools =
                                            actionContext.appState().appSettings().tools();
                                    if (tools.isEmpty()) {
                                        OpenDirectoryAction.open(
                                                parent.toString(), actionContext.window());
                                        return;
                                    }
                                    final var editor = tools.getFirst();
                                    RunCommandAction.run(
                                            EditorCommands.openFile(editor, path, 0, 0),
                                            parent.toString(),
                                            editor.label(),
                                            actionContext.window());
                                },
                                openTerminal::accept,
                                this::statusCode));
        this.tree.addTreeWillExpandListener(
                new TreeWillExpandListener() {
                    @Override
                    public void treeWillExpand(final TreeExpansionEvent event)
                            throws ExpandVetoException {
                        final Object node = event.getPath().getLastPathComponent();
                        if (node instanceof DefaultMutableTreeNode treeNode) {
                            loadChildren(treeNode);
                        }
                    }

                    @Override
                    public void treeWillCollapse(final TreeExpansionEvent event)
                            throws ExpandVetoException {}
                });
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
                                    UiPopupMenus.show(
                                            comparisonMenu(),
                                            comparisonButton,
                                            0,
                                            comparisonButton.getHeight());
                                }));
        final JButton hideButton = new IconButton(UiIcons.chevronRight(), "Hide files");
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
        final ProjectId projectId = actionContext.appState().currentProjectId();
        final Project project =
                projectId == null ? null : actionContext.appState().projects().get(projectId);
        refreshButton.setSelected(false);
        refreshButton.setEnabled(false);
        statusPanel.showRefreshing();
        BackgroundOperations.submit(
                        "Workspace",
                        "git-status",
                        () -> {
                            if (!Files.isDirectory(workspace)) {
                                throw new IOException("Workspace directory is unavailable.");
                            }
                            final String sourceRef =
                                    resolveSourceRef(includeSourceBranch, projectId, project);
                            try (GitRepository repository = GitRepository.open(workspace)) {
                                return repository.workspaceStatus(includeSourceBranch, sourceRef);
                            }
                        })
                .thenAccept(this::applyStatusUpdate)
                .exceptionally(this::handleRefreshFailure);
    }

    private void reloadWorkspace() {
        saveTreeState();
        root.removeAllChildren();
        if (changedOnlyButton.isSelected()) {
            buildChangedTree();
        } else {
            root.add(new DefaultMutableTreeNode(LOADING));
            ((DefaultTreeModel) tree.getModel()).reload(root);
            loadChildren(root);
        }
    }

    private void buildChangedTree() {
        final ChangedFileTree changed = ChangedFileTree.of(statuses.keySet());
        if (changed.children().isEmpty()) {
            root.add(new DefaultMutableTreeNode(EMPTY));
        } else {
            changed.children().forEach(node -> root.add(toTreeNode(node)));
        }
        ((DefaultTreeModel) tree.getModel()).reload(root);
        restoreTreeState();
    }

    private DefaultMutableTreeNode toTreeNode(final ChangedFileNode node) {
        final DefaultMutableTreeNode treeNode =
                new DefaultMutableTreeNode(workspace.resolve(node.path()));
        if (node.directory() && node.children().isEmpty()) {
            treeNode.add(new DefaultMutableTreeNode(EMPTY));
        } else {
            node.children().forEach(child -> treeNode.add(toTreeNode(child)));
        }
        return treeNode;
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
        if (PathUtils.directory(path)) {
            node.add(new DefaultMutableTreeNode(LOADING));
        }
        return node;
    }

    private void loadChildren(final DefaultMutableTreeNode parent) {
        if (!isLoadingPlaceholder(parent)) {
            return;
        }
        final Object value = parent.getUserObject();
        if (!(value instanceof Path directory)) {
            return;
        }
        BackgroundOperations.submit("Workspace", "load-files", () -> loadDirectory(directory))
                .thenAccept(
                        result -> {
                            parent.removeAllChildren();
                            if (result.unavailable()) {
                                parent.add(new DefaultMutableTreeNode("Unavailable"));
                            } else {
                                final List<DefaultMutableTreeNode> childNodes =
                                        result.children().stream().map(this::node).toList();
                                if (childNodes.isEmpty()) {
                                    parent.add(new DefaultMutableTreeNode(EMPTY));
                                } else {
                                    childNodes.forEach(parent::add);
                                }
                            }
                            ((DefaultTreeModel) tree.getModel()).reload(parent);
                            tree.expandPath(new TreePath(parent.getPath()));
                            restoreTreeState();
                        })
                .exceptionally(
                        failure -> {
                            parent.removeAllChildren();
                            parent.add(new DefaultMutableTreeNode("Unavailable"));
                            ((DefaultTreeModel) tree.getModel()).reload(parent);
                            return null;
                        });
    }

    private DirectoryLoad loadDirectory(final Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            final List<Path> children =
                    entries.filter(path -> !".git".equals(fileName(path)))
                            .filter(path -> !isIgnored(path))
                            .sorted(pathOrder())
                            .toList();
            return new DirectoryLoad(false, children);
        } catch (IOException failure) {
            return new DirectoryLoad(true, List.of());
        }
    }

    private Comparator<Path> pathOrder() {
        return Comparator.comparing((Path path) -> !PathUtils.directory(path))
                .thenComparing(this::fileName, String.CASE_INSENSITIVE_ORDER);
    }

    private String fileName(final Path path) {
        final Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    private boolean isIgnored(final Path path) {
        final String relative =
                workspace
                        .relativize(path.toAbsolutePath().normalize())
                        .toString()
                        .replace(java.io.File.separatorChar, '/');
        return ignoredPaths.contains(relative) || hasIgnoredAncestor(relative);
    }

    private boolean hasIgnoredAncestor(final String relative) {
        int separator = relative.lastIndexOf('/');
        while (separator > 0) {
            if (ignoredPaths.contains(relative.substring(0, separator))) {
                return true;
            }
            separator = relative.lastIndexOf('/', separator - 1);
        }
        return false;
    }

    private boolean isLoadingPlaceholder(final DefaultMutableTreeNode parent) {
        if (parent.getChildCount() != 1) {
            return false;
        }
        final Object child = ((DefaultMutableTreeNode) parent.getChildAt(0)).getUserObject();
        return LOADING.equals(child);
    }

    private Void handleRefreshFailure(final Throwable ignored) {
        statuses = Map.of();
        ignoredPaths = Set.of();
        SwingUtilities.invokeLater(() -> statusPanel.showUnavailable("Git status unavailable"));
        refreshButton.setEnabled(true);
        tree.repaint();
        return null;
    }

    private void applyStatusUpdate(final GitRepository.WorkspaceStatus updated) {
        statuses = updated.files();
        ignoredPaths = updated.ignoredPaths();
        final WorktreeStatusSummary summary = updated.summary();
        statusPanel.showStatus(summary);
        refreshButton.setEnabled(true);
        tree.repaint();
        reloadWorkspace();
    }

    private String resolveSourceRef(
            final boolean includeSourceBranch, final ProjectId projectId, final Project project) {
        if (!includeSourceBranch) {
            return null;
        }
        String sourceRef = "HEAD@{upstream}";
        if (project == null || projectId == null) {
            return sourceRef;
        }
        try {
            final String baseBranch =
                    GitHub.forProject(this.actionContext.appState(), projectId)
                            .getPullRequest(workspace)
                            .baseBranch()
                            .trim();
            if (!baseBranch.isBlank()) {
                sourceRef = "origin/" + baseBranch;
            }
        } catch (IOException ignored) {
            // Fall back to the repository's configured comparison source.
        }
        return sourceRef;
    }

    private static Path parentOrSelf(final Path path) {
        final Path parent = path.getParent();
        return parent == null ? path : parent;
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

    private boolean changed(final Path path) {
        final String relative =
                workspace
                        .relativize(path.toAbsolutePath().normalize())
                        .toString()
                        .replace(java.io.File.separatorChar, '/');
        return statuses.containsKey(relative)
                || statuses.keySet().stream().anyMatch(value -> value.startsWith(relative + "/"));
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
        if (changed(path)) {
            return " M";
        }
        return null;
    }
}
