package com.jagent.desktop.models;

import com.jagent.desktop.models.github.Credential;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.ui.Defaults;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.jetbrains.annotations.Nullable;

public record Project(
        String name,
        String path,
        @Nullable String group,
        @Nullable Credential credential,
        @Nullable String worktreeTemplate,
        @Nullable String worktreeCommand,
        List<String> startupCommands,
        List<SessionId> sessionIds,
        @Nullable String agentContextPath,
        @Nullable String agentContextText) {

    public Project {
        sessionIds = sessionIds == null ? List.of() : List.copyOf(sessionIds);
    }

    public Project(final String name, final String path, final Credential credential) {
        this(
                name,
                path,
                Defaults.DEFAULT_GROUP,
                credential,
                null,
                null,
                List.of(),
                List.of(),
                null,
                null);
    }

    public List<Entry<SessionId, Session>> projectSessions(final AppState appState) {
        return this.sessionIds().stream()
                .map((si) -> Map.entry(si, appState.sessions().get(si)))
                .toList();
    }

    public Project withName(final String name) {
        return new Project(
                name,
                this.path,
                this.group,
                this.credential,
                this.worktreeTemplate,
                this.worktreeCommand,
                this.startupCommands,
                this.sessionIds,
                this.agentContextPath,
                this.agentContextText);
    }

    public Project withGroup(final String group) {
        return new Project(
                this.name,
                this.path,
                group,
                this.credential,
                this.worktreeTemplate,
                this.worktreeCommand,
                this.startupCommands,
                this.sessionIds,
                this.agentContextPath,
                this.agentContextText);
    }

    public Project withNewSession(final SessionId sessionId) {
        final var newSessions = new ArrayList<SessionId>(this.sessionIds);
        newSessions.add(sessionId);
        return new Project(
                this.name,
                this.path,
                this.group,
                this.credential,
                this.worktreeTemplate,
                this.worktreeCommand,
                this.startupCommands,
                List.copyOf(newSessions),
                this.agentContextPath,
                this.agentContextText);
    }

    public Project withRemovedSession(final SessionId sessionId) {
        return new Project(
                this.name,
                this.path,
                this.group,
                this.credential,
                this.worktreeTemplate,
                this.worktreeCommand,
                this.startupCommands,
                this.sessionIds.stream().filter(id -> !id.equals(sessionId)).toList(),
                this.agentContextPath,
                this.agentContextText);
    }
}
