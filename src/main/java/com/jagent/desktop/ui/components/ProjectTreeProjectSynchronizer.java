package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.ui.Defaults;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

final class ProjectTreeProjectSynchronizer {
    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private final Map<ProjectId, DefaultMutableTreeNode> projectNodes;
    private final ProjectTreeSessionSynchronizer sessions;

    public ProjectTreeProjectSynchronizer(
            final JTree tree,
            final DefaultMutableTreeNode root,
            final Map<ProjectId, DefaultMutableTreeNode> projectNodes,
            final ProjectTreeSessionSynchronizer sessions) {
        this.tree = tree;
        this.root = root;
        this.projectNodes = projectNodes;
        this.sessions = sessions;
    }

    public void synchronize(final AppState state, final DefaultTreeModel model) {
        synchronize(state, model, "");
    }

    public void synchronize(
            final AppState state, final DefaultTreeModel model, final String filter) {
        removeStale(state.projects(), model);
        final Map<String, List<Map.Entry<ProjectId, Project>>> grouped = group(state.projects());
        final Map<String, DefaultMutableTreeNode> nodes = groups(grouped.keySet(), model);
        for (final var entry : grouped.entrySet()) {
            projects(entry.getValue(), nodes.get(entry.getKey()), state.sessions(), model, filter);
        }
        nodes.values().forEach(node -> tree.expandPath(new TreePath(node.getPath())));
    }

    private Map<String, List<Map.Entry<ProjectId, Project>>> group(
            final Map<ProjectId, Project> projects) {
        final Map<String, List<Map.Entry<ProjectId, Project>>> grouped =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (final var entry : projects.entrySet()) {
            grouped.computeIfAbsent(groupName(entry.getValue()), ignored -> new ArrayList<>())
                    .add(entry);
        }
        grouped.values()
                .forEach(
                        values ->
                                values.sort(
                                        Comparator.comparing(
                                                entry -> entry.getValue().name(),
                                                String.CASE_INSENSITIVE_ORDER)));
        return grouped;
    }

    private Map<String, DefaultMutableTreeNode> groups(
            final Set<String> names, final DefaultTreeModel model) {
        final Map<String, DefaultMutableTreeNode> result = existingGroups();
        for (final String name : names) {
            final DefaultMutableTreeNode node =
                    result.computeIfAbsent(name, DefaultMutableTreeNode::new);
            if (node.getParent() == null) {
                model.insertNodeInto(node, root, root.getChildCount());
            }
        }
        for (int index = root.getChildCount() - 1; index >= names.size() + 2; index--) {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode) root.getChildAt(index);
            if (node.getUserObject() instanceof String name && !names.contains(name)) {
                model.removeNodeFromParent(node);
            }
        }
        final List<String> ordered = new ArrayList<>(names);
        for (int index = 0; index < ordered.size(); index++) {
            final DefaultMutableTreeNode node = result.get(ordered.get(index));
            final int target = index + 2;
            if (root.getIndex(node) != target) {
                model.removeNodeFromParent(node);
                model.insertNodeInto(node, root, target);
            }
        }
        return result;
    }

    private Map<String, DefaultMutableTreeNode> existingGroups() {
        final Map<String, DefaultMutableTreeNode> result =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int index = 1; index < root.getChildCount(); index++) {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode) root.getChildAt(index);
            if (node.getUserObject() instanceof String name) {
                result.put(name, node);
            }
        }
        return result;
    }

    private void removeStale(final Map<ProjectId, Project> projects, final DefaultTreeModel model) {
        projectNodes.entrySet().removeIf(entry -> removeIfStale(entry, projects, model));
    }

    private boolean removeIfStale(
            final Map.Entry<ProjectId, DefaultMutableTreeNode> entry,
            final Map<ProjectId, Project> projects,
            final DefaultTreeModel model) {
        if (projects.containsKey(entry.getKey())) {
            return false;
        }
        final DefaultMutableTreeNode node = entry.getValue();
        sessions.removeAll(node, model);
        if (node.getParent() != null) {
            model.removeNodeFromParent(node);
        }
        return true;
    }

    private void projects(
            final List<Map.Entry<ProjectId, Project>> projects,
            final DefaultMutableTreeNode group,
            final Map<SessionId, Session> sessionsById,
            final DefaultTreeModel model,
            final String filter) {
        final Set<ProjectId> desired = new HashSet<>();
        projects.forEach(entry -> desired.add(entry.getKey()));
        for (int index = group.getChildCount() - 1; index >= 0; index--) {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode) group.getChildAt(index);
            if (!(node.getUserObject() instanceof Map.Entry<?, ?> entry)
                    || !(entry.getKey() instanceof ProjectId id)
                    || !desired.contains(id)) {
                model.removeNodeFromParent(node);
            }
        }
        projects.stream()
                .filter(
                        entry ->
                                ProjectTreeSessionSynchronizer.matches(
                                        entry.getValue(), sessionsById, filter))
                .forEach(entry -> project(entry, group, sessionsById, model, filter));
    }

    private void project(
            final Map.Entry<ProjectId, Project> entry,
            final DefaultMutableTreeNode group,
            final Map<SessionId, Session> sessionsById,
            final DefaultTreeModel model,
            final String filter) {
        final DefaultMutableTreeNode node =
                projectNodes.computeIfAbsent(
                        entry.getKey(), ignored -> new DefaultMutableTreeNode());
        final Object previous = node.getUserObject();
        node.setUserObject(entry);
        if (!entry.equals(previous)) {
            model.nodeChanged(node);
        }
        if (node.getParent() == null || !node.getParent().equals(group)) {
            if (node.getParent() != null) {
                model.removeNodeFromParent(node);
            }
            model.insertNodeInto(node, group, group.getChildCount());
        }
        sessions.synchronize(entry.getValue(), node, sessionsById, model, filter);
    }

    private static String groupName(final Project project) {
        final String group = project.group();
        return group == null || group.isBlank() ? Defaults.DEFAULT_GROUP : group;
    }
}
