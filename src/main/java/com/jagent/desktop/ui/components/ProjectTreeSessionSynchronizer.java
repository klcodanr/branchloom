package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

final class ProjectTreeSessionSynchronizer {
    private final Map<SessionId, DefaultMutableTreeNode> sessionNodes = new HashMap<>();
    private final BiConsumer<Project, Session> loadPullRequestStatus;

    public ProjectTreeSessionSynchronizer(
            final BiConsumer<Project, Session> loadPullRequestStatus) {
        this.loadPullRequestStatus = loadPullRequestStatus;
    }

    public DefaultMutableTreeNode sessionNode(final SessionId sessionId) {
        return sessionNodes.get(sessionId);
    }

    public void synchronize(
            final Project project,
            final DefaultMutableTreeNode projectNode,
            final Map<SessionId, Session> sessions,
            final DefaultTreeModel model,
            final String filter) {
        final Set<SessionId> desiredIds =
                project.sessionIds().stream()
                        .filter(
                                id -> {
                                    final Session session = sessions.get(id);
                                    return session != null && matches(project, session, filter);
                                })
                        .collect(java.util.stream.Collectors.toSet());
        removeStaleSessions(projectNode, desiredIds, sessions, model);
        for (final SessionId id : project.sessionIds()) {
            final Session session = sessions.get(id);
            if (session != null && matches(project, session, filter)) {
                synchronizeSession(project, projectNode, id, session, model);
            }
        }
    }

    public static boolean matches(
            final Project project, final Map<SessionId, Session> sessions, final String filter) {
        if (filter == null
                || filter.isBlank()
                || project.name().toLowerCase(Locale.ROOT).contains(filter)) {
            return true;
        }
        return project.sessionIds().stream()
                .map(sessions::get)
                .filter(session -> session != null)
                .anyMatch(session -> session.name().toLowerCase(Locale.ROOT).contains(filter));
    }

    private static boolean matches(
            final Project project, final Session session, final String filter) {
        return filter == null
                || filter.isBlank()
                || project.name().toLowerCase(Locale.ROOT).contains(filter)
                || session.name().toLowerCase(Locale.ROOT).contains(filter);
    }

    public void removeAll(final DefaultMutableTreeNode projectNode, final DefaultTreeModel model) {
        while (projectNode.getChildCount() > 0) {
            removeSession((DefaultMutableTreeNode) projectNode.getChildAt(0), model);
        }
    }

    private void removeStaleSessions(
            final DefaultMutableTreeNode projectNode,
            final Set<SessionId> desiredIds,
            final Map<SessionId, Session> sessions,
            final DefaultTreeModel model) {
        for (int index = projectNode.getChildCount() - 1; index >= 0; index--) {
            final DefaultMutableTreeNode sessionNode =
                    (DefaultMutableTreeNode) projectNode.getChildAt(index);
            final Object item = sessionNode.getUserObject();
            if (isStale(item, desiredIds, sessions)) {
                removeSession(sessionNode, model);
            }
        }
    }

    private boolean isStale(
            final Object item,
            final Set<SessionId> desiredIds,
            final Map<SessionId, Session> sessions) {
        return !(item instanceof Map.Entry<?, ?> entry)
                || !(entry.getKey() instanceof SessionId id)
                || !desiredIds.contains(id)
                || !sessions.containsKey(id);
    }

    private void synchronizeSession(
            final Project project,
            final DefaultMutableTreeNode projectNode,
            final SessionId id,
            final Session session,
            final DefaultTreeModel model) {
        final DefaultMutableTreeNode sessionNode =
                sessionNodes.computeIfAbsent(id, ignored -> new DefaultMutableTreeNode());
        final Map.Entry<SessionId, Session> entry = Map.entry(id, session);
        final Object previous = sessionNode.getUserObject();
        sessionNode.setUserObject(entry);
        if (!entry.equals(previous)) {
            model.nodeChanged(sessionNode);
            loadPullRequestStatus.accept(project, session);
        }
        if (sessionNode.getParent() == null || !sessionNode.getParent().equals(projectNode)) {
            if (sessionNode.getParent() != null) {
                model.removeNodeFromParent(sessionNode);
            }
            model.insertNodeInto(sessionNode, projectNode, projectNode.getChildCount());
        }
    }

    private void removeSession(
            final DefaultMutableTreeNode sessionNode, final DefaultTreeModel model) {
        final Object item = sessionNode.getUserObject();
        if (item instanceof Map.Entry<?, ?> entry
                && entry.getKey() instanceof SessionId id
                && sessionNodes.get(id) != null
                && sessionNodes.get(id).equals(sessionNode)) {
            sessionNodes.remove(id);
        }
        model.removeNodeFromParent(sessionNode);
    }
}
