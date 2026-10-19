package com.jagent.desktop.ui.components;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.board.BoardDefinition;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.actions.CreateProjectAction;
import com.jagent.desktop.ui.actions.ImportProjectAction;
import com.jagent.desktop.ui.actions.OpenProjectAction;
import com.jagent.desktop.ui.actions.OpenSessionAction;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.Map;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

final class ProjectTreeInteraction {
    private final ActionContext context;
    private final JTree tree;
    private final DefaultMutableTreeNode root;

    public ProjectTreeInteraction(
            final ActionContext context, final JTree tree, final DefaultMutableTreeNode root) {
        this.context = context;
        this.tree = tree;
        this.root = root;
    }

    public void select(final Object selectedPathComponent) {
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedPathComponent;
        if (node == null) {
            return;
        }
        final Object item = node.getUserObject();
        if (item instanceof Map.Entry<?, ?> entry && entry.getValue() instanceof Project) {
            context.appState().updateCurrentProject((ProjectId) entry.getKey());
            context.appState().updateCurrentSession(null);
            new OpenProjectAction(context).execute();
        } else if (item instanceof Map.Entry<?, ?> entry
                && entry.getValue() instanceof Session session) {
            context.appState().updateCurrentProject(session.projectId());
            context.appState().updateCurrentSession((SessionId) entry.getKey());
            new OpenSessionAction(context).execute();
        } else if (item == ProjectTreePanel.MyPullRequestsNode.INSTANCE) {
            context.viewCoordinator()
                    .updateView(ViewId.MY_PULL_REQUESTS, ViewCoordinator.ViewState.reset());
        } else if (item instanceof BoardDefinition board) {
            context.viewCoordinator()
                    .updateView(ViewId.BOARD, ViewCoordinator.ViewState.board(board.name()));
        }
    }

    public void showContextMenu(final MouseEvent event) {
        final TreePath path = contextMenuPath(event.getX(), event.getY());
        if (path == null) {
            showAddProjectMenu(event.getPoint());
            return;
        }
        showContextMenu(path, event.getPoint());
    }

    public void showKeyboardContextMenu() {
        final TreePath path = tree.getLeadSelectionPath();
        if (path == null) {
            return;
        }
        final Rectangle bounds = tree.getPathBounds(path);
        if (bounds != null) {
            showContextMenu(path, new Point(bounds.x, bounds.y + bounds.height));
        }
    }

    private void showContextMenu(final TreePath path, final Point point) {
        tree.setSelectionPath(path);
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (node.getUserObject() instanceof Map.Entry<?, ?> entry
                && entry.getValue() instanceof Project) {
            showProjectMenu(entry, point);
        } else if (node.getUserObject() instanceof Map.Entry<?, ?> entry
                && entry.getValue() instanceof Session) {
            showSessionMenu(entry, point);
        } else if (node.getUserObject() instanceof String && node.getParent().equals(root)) {
            showGroupMenu(node, point);
        } else {
            showAddProjectMenu(point);
        }
    }

    @SuppressWarnings("unchecked")
    private void showProjectMenu(final Map.Entry<?, ?> entry, final Point point) {
        ProjectActions.show(context, ((Map.Entry<ProjectId, Project>) entry).getKey(), tree, point);
    }

    private void showSessionMenu(final Map.Entry<?, ?> entry, final Point point) {
        final Map.Entry<SessionId, Session> session = (Map.Entry<SessionId, Session>) entry;
        context.appState().updateCurrentProject(session.getValue().projectId());
        context.appState().updateCurrentSession(session.getKey());
        UiPopupMenus.show(SessionActions.menu(context, session.getKey()), tree, point.x, point.y);
    }

    private void showGroupMenu(final DefaultMutableTreeNode groupNode, final Point point) {
        final JPopupMenu menu = new JPopupMenu();
        final String group = (String) groupNode.getUserObject();
        final JMenuItem addLocal = new JMenuItem("Add local project to " + group);
        addLocal.addActionListener(event -> new CreateProjectAction(context, group).execute());
        menu.add(addLocal);
        final JMenuItem cloneRemote = new JMenuItem("Clone remote project to " + group);
        cloneRemote.addActionListener(event -> new ImportProjectAction(context, group).execute());
        menu.add(cloneRemote);
        menu.addSeparator();
        final JMenuItem moveUp = new JMenuItem("Move up");
        moveUp.setEnabled(groupIndex(groupNode) > 0);
        moveUp.addActionListener(event -> moveGroup(groupNode, groupIndex(groupNode) - 1));
        menu.add(moveUp);
        final JMenuItem moveDown = new JMenuItem("Move down");
        moveDown.setEnabled(groupIndex(groupNode) < groupCount() - 1);
        moveDown.addActionListener(event -> moveGroup(groupNode, groupIndex(groupNode) + 1));
        menu.add(moveDown);
        UiPopupMenus.show(menu, tree, point.x, point.y);
    }

    private void moveGroup(final DefaultMutableTreeNode groupNode, final int targetIndex) {
        final int targetRootIndex = rootIndexForGroup(targetIndex);
        root.remove(groupNode);
        root.insert(groupNode, targetRootIndex);
        ((DefaultTreeModel) tree.getModel()).reload();
        tree.expandPath(new TreePath(groupNode.getPath()));
    }

    private int groupCount() {
        int count = 0;
        for (int index = 0; index < root.getChildCount(); index++) {
            if (((DefaultMutableTreeNode) root.getChildAt(index)).getUserObject()
                    instanceof String) {
                count++;
            }
        }
        return count;
    }

    private int groupIndex(final DefaultMutableTreeNode node) {
        int index = 0;
        for (int childIndex = 0; childIndex < root.getChildCount(); childIndex++) {
            final DefaultMutableTreeNode child =
                    (DefaultMutableTreeNode) root.getChildAt(childIndex);
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
        for (int index = 0; index < root.getChildCount(); index++) {
            if (((DefaultMutableTreeNode) root.getChildAt(index)).getUserObject()
                    instanceof String) {
                if (seen == groupIndex) {
                    return index;
                }
                seen++;
            }
        }
        return root.getChildCount();
    }

    private TreePath contextMenuPath(final int x, final int y) {
        TreePath path = tree.getPathForLocation(x, y);
        if (path != null) {
            return path;
        }
        path = tree.getClosestPathForLocation(0, y);
        final Rectangle bounds = path == null ? null : tree.getPathBounds(path);
        return bounds != null && y >= bounds.y && y < bounds.y + bounds.height ? path : null;
    }

    private void showAddProjectMenu(final Point point) {
        final JPopupMenu menu = new JPopupMenu();
        final JMenuItem create = new JMenuItem("Add local project");
        create.addActionListener(event -> new CreateProjectAction(context).execute());
        menu.add(create);
        final JMenuItem importProject = new JMenuItem("Clone remote project");
        importProject.addActionListener(event -> new ImportProjectAction(context).execute());
        menu.add(importProject);
        UiPopupMenus.show(menu, tree, point.x, point.y);
    }
}
