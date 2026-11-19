package com.jagent.desktop.models;

import com.jagent.desktop.models.github.Credential;
import com.jagent.desktop.ui.Defaults;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public record Project(
        ProjectId id,
        String name,
        String path,
        @Nullable String group,
        @Nullable Credential credential,
        @Nullable String worktreeTemplate,
        @Nullable String worktreeCommand,
        List<String> startupCommands,
        List<SessionId> sessionIds,
        @Nullable String agentContextPath,
        @Nullable String agentContextText,
        @Nullable String environmentVariables) {

    private static final Logger LOG = LoggerFactory.getLogger(Project.class);

    public Project {
        sessionIds = sessionIds == null ? List.of() : List.copyOf(sessionIds);
    }

    public Map<String, String> environment() {
        final Properties properties = new Properties();
        try {
            properties.load(new StringReader(Optional.ofNullable(environmentVariables).orElse("")));
        } catch (IOException exception) {
            LOG.warn("Could not parse project environment variables", exception);
        }
        return properties.stringPropertyNames().stream()
                .collect(
                        Collectors.toUnmodifiableMap(Function.identity(), properties::getProperty));
    }

    public static Project create(
            final String name, final String path, final Credential credential) {
        return new Project(
                ProjectId.create(),
                name,
                path,
                Defaults.DEFAULT_GROUP,
                credential,
                null,
                null,
                List.of(),
                List.of(),
                null,
                null,
                null);
    }

    public Project withName(final String name) {
        return new Project(
                this.id,
                name,
                this.path,
                this.group,
                this.credential,
                this.worktreeTemplate,
                this.worktreeCommand,
                this.startupCommands,
                this.sessionIds,
                this.agentContextPath,
                this.agentContextText,
                this.environmentVariables);
    }

    public Project withGroup(final String group) {
        return new Project(
                this.id,
                this.name,
                this.path,
                group,
                this.credential,
                this.worktreeTemplate,
                this.worktreeCommand,
                this.startupCommands,
                this.sessionIds,
                this.agentContextPath,
                this.agentContextText,
                this.environmentVariables);
    }

    public Project withNewSession(final SessionId sessionId) {
        final var newSessions = new ArrayList<SessionId>(this.sessionIds);
        newSessions.add(sessionId);
        return new Project(
                this.id,
                this.name,
                this.path,
                this.group,
                this.credential,
                this.worktreeTemplate,
                this.worktreeCommand,
                this.startupCommands,
                List.copyOf(newSessions),
                this.agentContextPath,
                this.agentContextText,
                this.environmentVariables);
    }

    public Project withRemovedSession(final SessionId sessionId) {
        return new Project(
                this.id,
                this.name,
                this.path,
                this.group,
                this.credential,
                this.worktreeTemplate,
                this.worktreeCommand,
                this.startupCommands,
                this.sessionIds.stream().filter(id -> !id.equals(sessionId)).toList(),
                this.agentContextPath,
                this.agentContextText,
                this.environmentVariables);
    }
}
