package com.jagent.desktop.ui.components.tree;

import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.AppState.Action;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.ui.components.ProjectActions;
import com.jagent.desktop.ui.components.SessionActions;
import com.jagent.desktop.ui.components.tree.items.GroupTreeItem;
import com.jagent.desktop.ui.components.tree.items.ProjectTreeItem;
import com.jagent.desktop.ui.components.tree.items.SessionTreeItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

public final class ProjectsTree {

    private final ViewController viewController;
    private final AppState appState;
    private final DialogService dialogService;
    private final SessionActions sessionActions;
    private final FilteredTreeModel filteredTreeModel;
    private final ProjectActions projectActions;
    private final JTree tree;

    private final Map<ProjectId, DefaultMutableTreeNode> projectNodes = new HashMap<>();
    private final Map<SessionId, DefaultMutableTreeNode> sessionNodes = new HashMap<>();
    private final Map<String, DefaultMutableTreeNode> groupNodes = new HashMap<>();

    @SuppressWarnings("PMD.CognitiveComplexity")
    public ProjectsTree(
            final ViewController viewController,
            final AppState appState,
            final DialogService dialogService,
            final ProjectActions projectActions,
            final SessionActions sessionActions,
            final FilteredTreeModel filteredTreeModel,
            final JTree tree) {
        this.viewController = viewController;
        this.appState = appState;
        this.dialogService = dialogService;
        this.projectActions = projectActions;
        this.sessionActions = sessionActions;
        this.filteredTreeModel = filteredTreeModel;
        this.tree = tree;
        rebuild();
        appState.addEventListener(
                event -> {
                    if (event.target() instanceof ProjectId
                            || event.target() instanceof SessionId) {
                        SwingUtilities.invokeLater(
                                () -> {
                                    if (event.target() instanceof ProjectId projectId) {
                                        if (event.action() == Action.ADD) {
                                            addProject(projectId);
                                        } else if (event.action() == Action.REMOVE) {
                                            removeProject(projectId);
                                        } else if (event.action() == Action.UPDATE
                                                && projectNodes.containsKey(projectId)) {
                                            this.filteredTreeModel.nodeChanged(
                                                    projectNodes.get(projectId));
                                        }
                                    } else if (event.target() instanceof SessionId sessionId) {
                                        if (event.action() == Action.ADD) {
                                            addSession(sessionId);
                                        } else if (event.action() == Action.REMOVE) {
                                            removeSession(sessionId);
                                        } else if (event.action() == Action.UPDATE
                                                && sessionNodes.containsKey(sessionId)) {
                                            this.filteredTreeModel.nodeChanged(
                                                    sessionNodes.get(sessionId));
                                        }
                                    }
                                });
                    }
                });
    }

    private void addProject(final ProjectId projectId) {
        final var project = appState.projects().get(projectId);
        if (project == null) {
            return;
        }
        final var groupName = project.group();
        var groupNode = groupNodes.get(groupName);
        if (groupNode == null) {
            final var newGroupNode =
                    new DefaultMutableTreeNode(
                            new GroupTreeItem(viewController, appState, dialogService, groupName));
            groupNodes.put(groupName, newGroupNode);
            final var root = (DefaultMutableTreeNode) filteredTreeModel.getRoot();
            filteredTreeModel.insertNodeInto(newGroupNode, root, root.getChildCount());
            groupNode = newGroupNode;
        }
        if (projectNodes.get(projectId) == null) {
            final DefaultMutableTreeNode newProjectNode =
                    new DefaultMutableTreeNode(
                            new ProjectTreeItem(
                                    viewController, appState, projectActions, projectId));
            project.sessionIds()
                    .forEach(
                            sid -> {
                                final var session = appState.sessions().get(sid);
                                if (session != null) {
                                    final DefaultMutableTreeNode sessionNode =
                                            new DefaultMutableTreeNode(
                                                    new SessionTreeItem(
                                                            viewController,
                                                            appState,
                                                            sessionActions,
                                                            sid));
                                    newProjectNode.add(sessionNode);
                                    this.sessionNodes.put(sid, sessionNode);
                                }
                            });
            filteredTreeModel.insertNodeInto(newProjectNode, groupNode, groupNode.getChildCount());
            projectNodes.put(projectId, newProjectNode);
            this.selectNode(newProjectNode);
        }
    }

    private void addSession(final SessionId sessionId) {
        if (sessionNodes.containsKey(sessionId)) {
            return;
        }
        final var session = appState.sessions().get(sessionId);
        if (session == null) {
            return;
        }
        final DefaultMutableTreeNode projectNode = projectNodes.get(session.projectId());
        if (projectNode == null) {
            return;
        }
        final DefaultMutableTreeNode sessionNode =
                new DefaultMutableTreeNode(
                        new SessionTreeItem(viewController, appState, sessionActions, sessionId));
        filteredTreeModel.insertNodeInto(sessionNode, projectNode, projectNode.getChildCount());
        sessionNodes.put(sessionId, sessionNode);
        this.selectNode(sessionNode);
    }

    public List<DefaultMutableTreeNode> getGroupNodes() {
        return new ArrayList<>(groupNodes.values());
    }

    private void removeProject(final ProjectId projectId) {
        final DefaultMutableTreeNode projectNode = projectNodes.remove(projectId);
        if (projectNode == null) {
            return;
        }
        projectNode
                .children()
                .asIterator()
                .forEachRemaining(
                        child -> {
                            final DefaultMutableTreeNode sessionNode =
                                    (DefaultMutableTreeNode) child;
                            if (sessionNode.getUserObject() instanceof SessionTreeItem session) {
                                sessionNodes.remove(session.getSessionId());
                            }
                        });
        final DefaultMutableTreeNode groupNode = (DefaultMutableTreeNode) projectNode.getParent();
        filteredTreeModel.removeNodeFromParent(projectNode);
        if (groupNode != null && groupNode.getChildCount() == 0) {
            groupNodes.values().remove(groupNode);
            filteredTreeModel.removeNodeFromParent(groupNode);
        }
    }

    private void removeSession(final SessionId sessionId) {
        final DefaultMutableTreeNode sessionNode = sessionNodes.remove(sessionId);
        if (sessionNode != null) {
            filteredTreeModel.removeNodeFromParent(sessionNode);
        }
    }

    private void selectNode(final DefaultMutableTreeNode node) {
        final var path = new TreePath(node.getPath());
        tree.scrollPathToVisible(path);
        tree.setSelectionPath(path);
    }

    public void rebuild() {

        groupNodes.clear();
        projectNodes.clear();
        sessionNodes.clear();

        final List<ProjectTreeItem> unmappedProjects =
                new ArrayList<>(
                        appState.projects().entrySet().stream()
                                .filter(
                                        entry ->
                                                Optional.ofNullable(entry.getValue().group())
                                                        .map(String::isEmpty)
                                                        .orElse(true))
                                .map(
                                        entry ->
                                                new ProjectTreeItem(
                                                        viewController,
                                                        appState,
                                                        projectActions,
                                                        entry.getKey()))
                                .toList());

        final List<Map.Entry<String, List<ProjectTreeItem>>> sorted = new ArrayList<>();
        // Populate the sorted list with group and project mappings
        Optional.ofNullable(appState.appSettings().projectGroups())
                .orElse(List.of())
                .forEach(
                        group -> {
                            final List<ProjectTreeItem> projectsInGroup =
                                    appState.projects().entrySet().stream()
                                            .filter(entry -> group.equals(entry.getValue().group()))
                                            .map(
                                                    entry ->
                                                            new ProjectTreeItem(
                                                                    viewController,
                                                                    appState,
                                                                    projectActions,
                                                                    entry.getKey()))
                                            .toList();
                            sorted.add(Map.entry(group, projectsInGroup));
                        });

        if (!unmappedProjects.isEmpty()) {
            sorted.add(0, Map.entry("Default", unmappedProjects));
        }

        sorted.forEach(
                e -> {
                    final var groupName = e.getKey();
                    final var projectItems = e.getValue();
                    final var groupNode =
                            new DefaultMutableTreeNode(
                                    new GroupTreeItem(
                                            viewController, appState, dialogService, groupName));
                    projectItems.forEach(
                            pi -> {
                                final var projectNode = new DefaultMutableTreeNode(pi);
                                projectNodes.put(pi.getProjectId(), projectNode);
                                pi.getProject()
                                        .sessionIds()
                                        .forEach(
                                                sid -> {
                                                    final var session =
                                                            appState.sessions().get(sid);
                                                    if (session != null) {
                                                        final var sessionNode =
                                                                new DefaultMutableTreeNode(
                                                                        new SessionTreeItem(
                                                                                viewController,
                                                                                appState,
                                                                                sessionActions,
                                                                                sid));
                                                        projectNode.add(sessionNode);
                                                        sessionNodes.put(sid, sessionNode);
                                                    }
                                                });

                                groupNode.add(projectNode);
                            });
                    groupNodes.put(groupName, groupNode);
                });
    }
}
