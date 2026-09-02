package com.jagent.desktop.ui.components;

import com.jagent.desktop.api.PullRequestInfo;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.services.BackgroundTasks;
import com.jagent.desktop.services.GitHub;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.services.terminal.TerminalManager;
import com.jagent.desktop.services.terminal.TerminalState;
import com.jagent.desktop.ui.actions.CreateProjectAction;
import com.jagent.desktop.ui.actions.OpenProjectAction;
import com.jagent.desktop.ui.actions.OpenSessionAction;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

public final class ProjectTreePanel extends JPanel {
    private final transient ActionContext actionContext;
    private final JTree tree;
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode("Projects");
    private final transient Map<Session, TerminalState> sessionStates = new HashMap<>();
    private final transient Map<Session, PullRequestInfo> pullRequestStatuses = new HashMap<>();
    private final transient ProjectTreeSynchronizer treeSynchronizer;
    private Map<ProjectId, Project> renderedProjects = Map.of();
    private Map<SessionId, Session> renderedSessions = Map.of();
    private boolean rendered;
    private boolean selectingProgrammatically;

    public ProjectTreePanel(final ActionContext actionContext) {
        super();
        this.actionContext = actionContext;
        setOpaque(false);
        setBorder(Theme.sectionBorder(20, 18, 18, 14));
        setPreferredSize(new Dimension(315, 0));
        setLayout(new BorderLayout(0, 12));

        add(new ProjectHeader(actionContext), BorderLayout.NORTH);
        tree = new ProjectTree();
        treeSynchronizer = new ProjectTreeSynchronizer(tree, root, this::loadPullRequestStatus);
        add(tree, BorderLayout.CENTER);
        add(new SettingsButton(actionContext), BorderLayout.SOUTH);
    }

    private static final class ProjectHeader extends JPanel {
        private ProjectHeader(final ActionContext actionContext) {
            super(new BorderLayout());
            setOpaque(false);
            add(UiFactory.label("PROJECTS", Theme.FontSize.XS), BorderLayout.WEST);
            final JButton add = UiFactory.iconButton(UiIcons.plus());
            add.getAccessibleContext().setAccessibleName("Add project");
            add.setToolTipText("Add project");
            add.addActionListener(e -> new CreateProjectAction(actionContext).execute());
            add(add, BorderLayout.EAST);
        }
    }

    private final class ProjectTree extends JTree {
        private ProjectTree() {
            super(root);
            setOpaque(false);
            setRootVisible(false);
            setShowsRootHandles(true);
            setCellRenderer(new ProjectTreeCellRenderer());
            ToolTipManager.sharedInstance().registerComponent(this);
            getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
            addTreeSelectionListener(
                    event -> {
                        if (!selectingProgrammatically) {
                            final TreePath path = event.getNewLeadSelectionPath();
                            if (path != null) {
                                select(path.getLastPathComponent());
                            }
                        }
                    });
            addMouseListener(
                    new MouseAdapter() {
                        @Override
                        public void mousePressed(final MouseEvent event) {
                            if (event.isPopupTrigger()) {
                                showContextMenu(event);
                            }
                        }

                        @Override
                        public void mouseReleased(final MouseEvent event) {
                            if (event.isPopupTrigger()) {
                                showContextMenu(event);
                            }
                        }
                    });
        }

        @Override
        public String getToolTipText(final MouseEvent event) {
            final TreePath path = getPathForLocation(event.getX(), event.getY());
            if (path == null) {
                return null;
            }
            final Object item =
                    ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
            if (item instanceof Map.Entry<?, ?> entry
                    && entry.getValue() instanceof Project project) {
                return projectTooltip(project);
            }
            if (item instanceof Map.Entry<?, ?> entry
                    && entry.getValue() instanceof Session session) {
                return sessionTooltip(session);
            }
            return null;
        }
    }

    private static final class SettingsButton extends JButton {
        private SettingsButton(final ActionContext actionContext) {
            super("Settings");
            setName("settings-button");
            setIcon(UiIcons.settings());
            setBorderPainted(false);
            setHorizontalAlignment(LEFT);
            addActionListener(
                    e -> actionContext.viewCoordinator().updateView(ViewId.SETTINGS, null));
        }
    }

    private final class ProjectTreeCellRenderer extends DefaultTreeCellRenderer {
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
            if (item instanceof Map.Entry<?, ?> entry) {
                if (entry.getValue() instanceof Project project) {
                    setText(project.name());
                } else if (entry.getValue() instanceof Session session) {
                    setText(session.name());
                    setIcon(StatusDot.terminalIcon(sessionState(session)));
                }
            }
            return component;
        }
    }

    public void refresh(final Project selectedProject, final Session selectedSession) {
        final Map<ProjectId, Project> projects = actionContext.appState().projects();
        final Map<SessionId, Session> sessions = actionContext.appState().sessions();
        if (!rendered || !projects.equals(renderedProjects) || !sessions.equals(renderedSessions)) {
            treeSynchronizer.synchronize(actionContext.appState());
            renderedProjects = projects;
            renderedSessions = sessions;
            rendered = true;
        }
        restoreSelection(selectedProject, selectedSession);
    }

    private void restoreSelection(final Project selectedProject, final Session selectedSession) {
        if (selectedProject == null) {
            selectNode((DefaultMutableTreeNode) root.getFirstChild());
            return;
        }
        final ProjectId projectId = actionContext.appState().currentProjectId();
        final DefaultMutableTreeNode projectNode = treeSynchronizer.projectNode(projectId);
        if (projectNode == null) {
            return;
        }
        final SessionId sessionId = actionContext.appState().currentSessionId();
        final DefaultMutableTreeNode sessionNode = treeSynchronizer.sessionNode(sessionId);
        tree.expandPath(new TreePath(projectNode.getPath()));
        selectNode(selectedSession == null || sessionNode == null ? projectNode : sessionNode);
    }

    public JTree tree() {
        return tree;
    }

    public void updateSessionStatus(final Session session, final TerminalState state) {
        sessionStates.put(session, state);
        tree.repaint();
    }

    private TerminalState sessionState(final Session session) {
        return session.terminalIds().stream()
                .map(TerminalManager.get()::state)
                .filter(state -> state != null)
                .filter(state -> state == TerminalState.FAILED)
                .findFirst()
                .orElseGet(
                        () ->
                                session.terminalIds().stream()
                                        .map(TerminalManager.get()::state)
                                        .filter(state -> state != null)
                                        .findFirst()
                                        .orElse(null));
    }

    private static String projectTooltip(final Project project) {
        return "<html><b>"
                + UiText.escapeHtml(project.name())
                + "</b><br>"
                + UiText.escapeHtml(project.path())
                + "<br>"
                + project.sessionIds().size()
                + (project.sessionIds().size() == 1 ? " session" : " sessions")
                + "</html>";
    }

    private String sessionTooltip(final Session session) {
        final String agent = session.agent();
        final String promptValue = session.prompt();
        final String worktreePath = session.worktreePath();
        String prompt = promptValue == null ? "" : promptValue.trim();
        if (prompt.length() > 140) {
            prompt = prompt.substring(0, 137) + "...";
        }
        final PullRequestInfo pullRequest = pullRequestStatuses.get(session);
        final String pullRequestHtml =
                pullRequest == null ? "" : "<br>" + GitFormatter.statusHtml(pullRequest);
        return "<html><b>"
                + UiText.escapeHtml(session.name())
                + "</b>"
                + (agent == null || agent.isBlank() ? "" : "<br>Agent: " + UiText.escapeHtml(agent))
                + (worktreePath == null || worktreePath.isBlank()
                        ? ""
                        : "<br>Worktree: " + UiText.escapeHtml(worktreePath))
                + (prompt.isBlank() ? "" : "<br><br>" + UiText.escapeHtml(prompt))
                + pullRequestHtml
                + "</html>";
    }

    private void loadPullRequestStatus(final Project project, final Session session) {
        BackgroundTasks.submit(
                "Pull requests",
                "left-nav-pr-status",
                () -> {
                    try {
                        final GitHub.PullRequestDetails details =
                                GitHub.loadCurrent(project, Path.of(session.worktreePath()));
                        final PullRequestInfo status = details;
                        SwingUtilities.invokeLater(
                                () -> {
                                    pullRequestStatuses.put(session, status);
                                    tree.repaint();
                                });
                    } catch (IOException | InterruptedException | RuntimeException ignored) {
                        // A branch without a pull request has no PR tooltip details.
                    }
                });
    }

    private void selectNode(final DefaultMutableTreeNode node) {
        selectingProgrammatically = true;
        try {
            tree.setSelectionPath(new TreePath(node.getPath()));
        } finally {
            selectingProgrammatically = false;
        }
    }

    private void select(final Object selectedPathComponent) {
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedPathComponent;
        if (node == null) {
            return;
        }
        final Object item = node.getUserObject();
        if (item instanceof Map.Entry<?, ?> entry && entry.getValue() instanceof Project) {
            actionContext.appState().updateCurrentProject((ProjectId) entry.getKey());
            actionContext.appState().updateCurrentSession(null);
            new OpenProjectAction(actionContext).execute();
        } else if (item instanceof Map.Entry<?, ?> entry
                && entry.getValue() instanceof Session session) {
            actionContext.appState().updateCurrentProject(session.projectId());
            actionContext.appState().updateCurrentSession((SessionId) entry.getKey());
            new OpenSessionAction(actionContext).execute();
        } else if (item == HomeNode.INSTANCE) {
            actionContext
                    .viewCoordinator()
                    .updateView(ViewId.HOME, ViewCoordinator.ViewState.reset());
        }
    }

    @SuppressWarnings("unchecked")
    private void showContextMenu(final MouseEvent event) {
        final TreePath path = contextMenuPath(event.getX(), event.getY());
        if (path == null) {
            showAddProjectMenu(event.getPoint());
            return;
        }
        tree.setSelectionPath(path);
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (node.getUserObject() instanceof Map.Entry<?, ?> entry
                && entry.getValue() instanceof Project) {
            ProjectActions.show(
                    actionContext,
                    ((Map.Entry<ProjectId, Project>) entry).getKey(),
                    tree,
                    event.getPoint());
        } else if (node.getUserObject() instanceof Map.Entry<?, ?> entry
                && entry.getValue() instanceof Session) {
            showSessionMenu((Map.Entry<SessionId, Session>) entry, event.getPoint());
        } else if (node.getUserObject() instanceof String && node.getParent().equals(root)) {
            showGroupMenu(node, event.getPoint());
        } else {
            showAddProjectMenu(event.getPoint());
        }
    }

    private void showSessionMenu(final Map.Entry<SessionId, Session> entry, final Point point) {
        actionContext.appState().updateCurrentProject(entry.getValue().projectId());
        actionContext.appState().updateCurrentSession(entry.getKey());
        final JPopupMenu menu = SessionActions.menu(actionContext, entry.getKey());
        menu.show(tree, point.x, point.y);
    }

    private void showGroupMenu(final DefaultMutableTreeNode groupNode, final Point point) {
        final JPopupMenu menu = new JPopupMenu();
        final JMenuItem moveUp = new JMenuItem("Move up");
        moveUp.setEnabled(groupIndex(groupNode) > 0);
        moveUp.addActionListener(event -> moveGroup(groupNode, groupIndex(groupNode) - 1));
        menu.add(moveUp);
        final JMenuItem moveDown = new JMenuItem("Move down");
        moveDown.setEnabled(groupIndex(groupNode) < groupCount() - 1);
        moveDown.addActionListener(event -> moveGroup(groupNode, groupIndex(groupNode) + 1));
        menu.add(moveDown);
        menu.show(tree, point.x, point.y);
    }

    private void moveGroup(final DefaultMutableTreeNode groupNode, final int targetIndex) {
        final int targetGroupIndex = targetIndex;
        final int targetRootIndex = rootIndexForGroup(targetGroupIndex);
        root.remove(groupNode);
        root.insert(groupNode, targetRootIndex);
        ((DefaultTreeModel) tree.getModel()).reload();
        tree.expandPath(new TreePath(groupNode.getPath()));
    }

    private int groupCount() {
        return (int)
                java.util.stream.IntStream.range(0, root.getChildCount())
                        .filter(
                                i ->
                                        ((DefaultMutableTreeNode) root.getChildAt(i))
                                                        .getUserObject()
                                                instanceof String)
                        .count();
    }

    private int groupIndex(final DefaultMutableTreeNode node) {
        int index = 0;
        for (int i = 0; i < root.getChildCount(); i++) {
            final DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            if (child.equals(node)) {
                return index;
            }
            if (child.getUserObject() instanceof String) {
                index++;
            }
        }
        return -1;
    }

    private int rootIndexForGroup(final int groupIndex) {
        int seen = 0;
        for (int i = 0; i < root.getChildCount(); i++) {
            if (((DefaultMutableTreeNode) root.getChildAt(i)).getUserObject() instanceof String) {
                if (seen == groupIndex) {
                    return i;
                }
                seen++;
            }
        }
        return root.getChildCount();
    }

    public enum HomeNode {
        INSTANCE;

        @Override
        public String toString() {
            return "Home";
        }
    }

    private TreePath contextMenuPath(final int x, final int y) {
        TreePath path = tree.getPathForLocation(x, y);
        if (path != null) {
            return path;
        }

        // The rendered node does not fill the tree's entire row width.
        path = tree.getClosestPathForLocation(0, y);
        final Rectangle bounds = path == null ? null : tree.getPathBounds(path);
        return bounds != null && y >= bounds.y && y < bounds.y + bounds.height ? path : null;
    }

    private void showAddProjectMenu(final Point point) {
        final JPopupMenu menu = new JPopupMenu();
        final JMenuItem add = new JMenuItem("New project");
        add.addActionListener(event -> new CreateProjectAction(actionContext).execute());
        menu.add(add);
        menu.show(tree, point.x, point.y);
    }
}
