package com.jagent.desktop.services;

import com.jagent.desktop.models.AppSettings;
import com.jagent.desktop.models.IdType;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.ui.Defaults;
import java.io.InvalidObjectException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class AppState {
    private AppSettings appSettings;
    private boolean projectsUpdated;
    private final Map<ProjectId, Project> projects;
    private final Map<SessionId, Session> sessions;
    private final Map<TerminalId, Terminal> terminals;
    private final List<TerminalEvent> terminalEvents = new ArrayList<>();
    private final List<Consumer<StateEvent>> stateEventListeners = new ArrayList<>();

    public enum Action {
        ADD,
        REMOVE
    }

    public record StateEvent(Action action, IdType target) {}

    public record TerminalEvent(Action action, TerminalId terminalId) {}

    public record PersistenceSnapshot(
            boolean projectsUpdated,
            Map<ProjectId, Project> projects,
            Map<SessionId, Session> sessions,
            Map<TerminalId, Terminal> terminals,
            List<TerminalEvent> terminalEvents) {}

    public AppState(
            final AppSettings appSettings,
            final Map<String, Project> projects,
            final Map<String, Session> sessions,
            final Map<String, Terminal> terminals) {
        this.appSettings = appSettings == null ? Defaults.appSettings() : appSettings;
        this.projects =
                projects.entrySet().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        e -> new ProjectId(UUID.fromString(e.getKey())),
                                        Map.Entry::getValue));
        this.sessions =
                sessions.entrySet().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        e -> new SessionId(UUID.fromString(e.getKey())),
                                        Map.Entry::getValue));
        this.terminals =
                terminals.entrySet().stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        e -> new TerminalId(UUID.fromString(e.getKey())),
                                        Map.Entry::getValue));
    }

    public AppSettings appSettings() {
        return this.appSettings;
    }

    public Map<ProjectId, Project> projects() {
        return Map.copyOf(this.projects);
    }

    public Map<SessionId, Session> sessions() {
        return Map.copyOf(this.sessions);
    }

    public Map<TerminalId, Terminal> terminals() {
        return Map.copyOf(this.terminals);
    }

    public void updateAppSettings(final AppSettings appSettings) {
        this.appSettings = appSettings;
    }

    public PersistenceSnapshot snapshotForPersistence() {
        // AppState is owned by Swing; persistence requests this snapshot on the EDT.
        if (!projectsUpdated) {
            return null;
        }
        final PersistenceSnapshot snapshot =
                new PersistenceSnapshot(
                        projectsUpdated,
                        Map.copyOf(projects),
                        Map.copyOf(sessions),
                        Map.copyOf(terminals),
                        List.copyOf(terminalEvents));
        projectsUpdated = false;
        terminalEvents.clear();
        return snapshot;
    }

    public void restorePersistenceUpdates(
            final boolean projectsUpdated, final List<TerminalEvent> terminalEvents) {
        this.projectsUpdated |= projectsUpdated || !terminalEvents.isEmpty();
        this.terminalEvents.addAll(0, terminalEvents);
    }

    public ProjectId addProject(final Project project) {
        final var projectId = ProjectId.create();
        this.projects.put(projectId, project);
        this.projectsUpdated = true;
        return projectId;
    }

    public void removeProject(final ProjectId projectId) {
        final Project project = this.projects.remove(projectId);
        if (project == null) {
            return;
        }
        project.sessionIds().forEach(this::removeSession);
        terminals.entrySet().stream()
                .filter(entry -> projectId.equals(entry.getValue().projectId()))
                .map(Map.Entry::getKey)
                .toList()
                .forEach(this::removeTerminal);

        this.projectsUpdated = true;
        notifyStateEvent(new StateEvent(Action.REMOVE, projectId));
    }

    public void updateProject(final ProjectId projectId, final Project project) {
        this.projects.put(projectId, project);
        this.projectsUpdated = true;
    }

    public SessionId addSession(final ProjectId projectId, final Session session)
            throws InvalidObjectException {
        final Project project = this.projects.get(projectId);
        if (project == null) {
            throw new InvalidObjectException("Project not found: " + projectId);
        }
        final var sessionId = SessionId.create();
        this.sessions.put(sessionId, session);
        this.projects.put(projectId, project.withNewSession(sessionId));
        this.projectsUpdated = true;
        return sessionId;
    }

    public void removeSession(final SessionId sessionId) {
        final Session session = this.sessions.remove(sessionId);
        if (session == null) {
            return;
        }
        session.terminalIds().forEach(this::removeTerminal);
        final var project = this.projects.get(session.projectId());
        if (project != null) {
            this.updateProject(session.projectId(), project.withRemovedSession(sessionId));
        }
        this.projectsUpdated = true;
        notifyStateEvent(new StateEvent(Action.REMOVE, sessionId));
    }

    public void updateSession(final SessionId sessionId, final Session session) {
        this.sessions.put(sessionId, session);
        this.projectsUpdated = true;
    }

    public TerminalId addTerminal(final SessionId sessionId, final Terminal terminal) {
        final Session session = this.sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        return registerTerminal(
                terminal,
                terminalId -> this.updateSession(sessionId, session.withNewTerminal(terminalId)));
    }

    public TerminalId addTerminal(final Terminal terminal) {
        if (terminal.projectId() == null || !this.projects.containsKey(terminal.projectId())) {
            throw new IllegalArgumentException("Project terminal must have a project ID.");
        }
        return registerTerminal(terminal, ignored -> {});
    }

    private TerminalId registerTerminal(
            final Terminal terminal, final Consumer<TerminalId> updateOwner) {
        final var terminalId = TerminalId.create();
        this.terminals.put(terminalId, terminal);
        updateOwner.accept(terminalId);
        this.projectsUpdated = true;
        this.terminalEvents.add(new TerminalEvent(Action.ADD, terminalId));
        return terminalId;
    }

    public void removeTerminal(final TerminalId terminalId) {
        final var terminal = this.terminals.get(terminalId);
        if (terminal == null) {
            return;
        }

        terminals.remove(terminalId);
        this.terminalEvents.add(new TerminalEvent(Action.REMOVE, terminalId));
        final var session = this.sessions.get(terminal.sessionId());
        if (session != null) {
            this.updateSession(terminal.sessionId(), session.withRemovedTerminal(terminalId));
        }
        this.projectsUpdated = true;
        notifyStateEvent(new StateEvent(Action.REMOVE, terminalId));
    }

    public void updateTerminal(final TerminalId terminalId, final Terminal terminal) {
        this.terminals.put(terminalId, terminal);
        this.projectsUpdated = true;
    }

    public void addEventListener(final Consumer<StateEvent> listener) {
        this.stateEventListeners.add(listener);
    }

    private void notifyStateEvent(final StateEvent event) {
        stateEventListeners.forEach(listener -> listener.accept(event));
    }
}
