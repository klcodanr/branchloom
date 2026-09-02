package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.ui.Defaults;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

final class ProjectTreeSynchronizer {
    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private final Map<ProjectId, DefaultMutableTreeNode> projectNodes = new HashMap<>();
    private final ProjectTreeSessionSynchronizer sessionSynchronizer;

    public ProjectTreeSynchronizer(
            final JTree tree,
            final DefaultMutableTreeNode root,
            final BiConsumer<Project, Session> loadPullRequestStatus) {
        this.tree = tree;
        this.root = root;
        sessionSynchronizer = new ProjectTreeSessionSynchronizer(loadPullRequestStatus);
    }

    public DefaultMutableTreeNode projectNode(final ProjectId projectId) {
        return projectNodes.get(projectId);
    }

    public DefaultMutableTreeNode sessionNode(final SessionId sessionId) {
        return sessionSynchronizer.sessionNode(sessionId);
    }

    public void synchronize(final AppState appState) {
        final DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        ensureHome(model);
        removeStaleProjects(appState.projects(), model);
        final Map<String, List<Map.Entry<ProjectId, Project>>> groupedProjects =
                groupedProjects(appState.projects());
        final Map<String, DefaultMutableTreeNode> groups =
                ensureGroups(groupedProjects.keySet(), model);
        for (final var group : groupedProjects.entrySet()) {
            synchronizeProjects(
                    group.getValue(), groups.get(group.getKey()), appState.sessions(), model);
        }
        expandGroups(groups);
    }

    private void ensureHome(final DefaultTreeModel model) {
        if (root.getChildCount() == 0
                || ((DefaultMutableTreeNode) root.getChildAt(0)).getUserObject()
                        != ProjectTreePanel.HomeNode.INSTANCE) {
            model.insertNodeInto(
                    new DefaultMutableTreeNode(ProjectTreePanel.HomeNode.INSTANCE), root, 0);
        }
    }

    private Map<String, List<Map.Entry<ProjectId, Project>>> groupedProjects(
            final Map<ProjectId, Project> projects) {
        final Map<String, List<Map.Entry<ProjectId, Project>>> grouped =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (final var project : projects.entrySet()) {
            grouped.computeIfAbsent(groupName(project.getValue()), ignored -> new ArrayList<>())
                    .add(project);
        }
        return grouped;
    }

    private Map<String, DefaultMutableTreeNode> ensureGroups(
            final Set<String> desiredNames, final DefaultTreeModel model) {
        final Map<String, DefaultMutableTreeNode> groups = existingGroups();
        for (final String name : desiredNames) {
            final DefaultMutableTreeNode group =
                    groups.computeIfAbsent(name, DefaultMutableTreeNode::new);
            if (group.getParent() == null) {
                model.insertNodeInto(group, root, root.getChildCount());
            }
        }
        for (int index = root.getChildCount() - 1; index > 0; index--) {
            final DefaultMutableTreeNode group = (DefaultMutableTreeNode) root.getChildAt(index);
            if (group.getUserObject() instanceof String name && !desiredNames.contains(name)) {
                model.removeNodeFromParent(group);
            }
        }
        final List<String> names = new ArrayList<>(desiredNames);
        for (int index = 0; index < names.size(); index++) {
            final DefaultMutableTreeNode group = groups.get(names.get(index));
            final int targetIndex = index + 1;
            if (root.getIndex(group) != targetIndex) {
                model.removeNodeFromParent(group);
                model.insertNodeInto(group, root, targetIndex);
            }
        }
        return groups;
    }

    private void expandGroups(final Map<String, DefaultMutableTreeNode> groups) {
        for (final DefaultMutableTreeNode group : groups.values()) {
            tree.expandPath(new TreePath(group.getPath()));
        }
    }

    private Map<String, DefaultMutableTreeNode> existingGroups() {
        final Map<String, DefaultMutableTreeNode> groups =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int index = 1; index < root.getChildCount(); index++) {
            final DefaultMutableTreeNode group = (DefaultMutableTreeNode) root.getChildAt(index);
            if (group.getUserObject() instanceof String name) {
                groups.put(name, group);
            }
        }
        return groups;
    }

    private void removeStaleProjects(
            final Map<ProjectId, Project> projects, final DefaultTreeModel model) {
        projectNodes
                .entrySet()
                .removeIf(
                        entry -> {
                            if (projects.containsKey(entry.getKey())) {
                                return false;
                            }
                            final DefaultMutableTreeNode project = entry.getValue();
                            sessionSynchronizer.removeAll(project, model);
                            if (project.getParent() != null) {
                                model.removeNodeFromParent(project);
                            }
                            return true;
                        });
    }

    private void synchronizeProjects(
            final List<Map.Entry<ProjectId, Project>> projects,
            final DefaultMutableTreeNode group,
            final Map<SessionId, Session> sessions,
            final DefaultTreeModel model) {
        final Set<ProjectId> desiredIds = new HashSet<>();
        for (final var project : projects) {
            desiredIds.add(project.getKey());
        }
        for (int index = group.getChildCount() - 1; index >= 0; index--) {
            final DefaultMutableTreeNode project = (DefaultMutableTreeNode) group.getChildAt(index);
            if (!(project.getUserObject() instanceof Map.Entry<?, ?> entry)
                    || !(entry.getKey() instanceof ProjectId id)
                    || !desiredIds.contains(id)) {
                model.removeNodeFromParent(project);
            }
        }
        for (final var entry : projects) {
            synchronizeProject(entry, group, sessions, model);
        }
    }

    private void synchronizeProject(
            final Map.Entry<ProjectId, Project> entry,
            final DefaultMutableTreeNode group,
            final Map<SessionId, Session> sessions,
            final DefaultTreeModel model) {
        final DefaultMutableTreeNode project =
                projectNodes.computeIfAbsent(
                        entry.getKey(), ignored -> new DefaultMutableTreeNode());
        final Object previous = project.getUserObject();
        project.setUserObject(entry);
        if (!entry.equals(previous)) {
            model.nodeChanged(project);
        }
        if (project.getParent() == null || !project.getParent().equals(group)) {
            if (project.getParent() != null) {
                model.removeNodeFromParent(project);
            }
            model.insertNodeInto(project, group, group.getChildCount());
        }
        sessionSynchronizer.synchronize(entry.getValue(), project, sessions, model);
    }

    private static String groupName(final Project project) {
        final String group = project.group();
        return group == null || group.isBlank() ? Defaults.DEFAULT_GROUP : group;
    }
}
