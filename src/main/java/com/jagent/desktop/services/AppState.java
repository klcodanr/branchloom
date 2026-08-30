package com.jagent.desktop.services;

import com.jagent.desktop.models.AppSettings;
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
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

public class AppState {
    private boolean appSettingsUpdated;
    private AppSettings appSettings;
    private boolean projectsUpdated;
    private ProjectId currentProject;
    private SessionId currentSession;
    private TerminalId currentTerminal;
    private final Map<ProjectId, Project> projects;
    private final Map<SessionId, Session> sessions;
    private final Map<TerminalId, Terminal> terminals;
    private final List<TerminalEvent> terminalEvents = new ArrayList<>();

    public enum TerminalAction {
        ADD,
        REMOVE
    }

    public record TerminalEvent(TerminalAction action, TerminalId terminalId) {}

    public record PersistenceSnapshot(
            boolean appSettingsUpdated,
            boolean projectsUpdated,
            AppSettings appSettings,
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
                        .map(
                                (e) ->
                                        Map.entry(
                                                new ProjectId(UUID.fromString(e.getKey())),
                                                e.getValue()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        this.sessions =
                sessions.entrySet().stream()
                        .map(
                                (e) ->
                                        Map.entry(
                                                new SessionId(UUID.fromString(e.getKey())),
                                                e.getValue()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        this.terminals =
                terminals.entrySet().stream()
                        .map(
                                (e) ->
                                        Map.entry(
                                                new TerminalId(UUID.fromString(e.getKey())),
                                                e.getValue()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public AppSettings appSettings() {
        return this.appSettings;
    }

    public @Nullable ProjectId currentProjectId() {
        return this.currentProject;
    }

    public @Nullable Project currentProject() {
        return this.currentProject == null ? null : this.projects.get(this.currentProject);
    }

    public @Nullable SessionId currentSessionId() {
        return this.currentSession;
    }

    public @Nullable Session currentSession() {
        return this.currentSession == null ? null : this.sessions.get(this.currentSession);
    }

    public @Nullable TerminalId currentTerminalId() {
        return this.currentTerminal;
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
        this.appSettingsUpdated = true;
    }

    public PersistenceSnapshot snapshotForPersistence() {
        // AppState is owned by Swing; persistence requests this snapshot on the EDT.
        if (!appSettingsUpdated && !projectsUpdated) {
            return null;
        }
        final PersistenceSnapshot snapshot =
                new PersistenceSnapshot(
                        appSettingsUpdated,
                        projectsUpdated,
                        appSettings,
                        Map.copyOf(projects),
                        Map.copyOf(sessions),
                        Map.copyOf(terminals),
                        List.copyOf(terminalEvents));
        appSettingsUpdated = false;
        projectsUpdated = false;
        terminalEvents.clear();
        return snapshot;
    }

    public void restorePersistenceUpdates(
            final boolean appSettingsUpdated,
            final boolean projectsUpdated,
            final List<TerminalEvent> terminalEvents) {
        this.appSettingsUpdated |= appSettingsUpdated;
        this.projectsUpdated |= projectsUpdated;
        this.terminalEvents.addAll(0, terminalEvents);
    }

    public void updateCurrentProject(final ProjectId currentProject) {
        this.currentProject = currentProject;
    }

    public void updateCurrentSession(final SessionId currentSession) {
        this.currentSession = currentSession;
    }

    public void updateCurrentTerminal(final TerminalId currentTerminal) {
        this.currentTerminal = currentTerminal;
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
        if (projectId.equals(this.currentProject)) {
            updateCurrentProject(null);
        }

        terminals.entrySet().stream()
                .filter(entry -> projectId.equals(entry.getValue().projectId()))
                .map(Map.Entry::getKey)
                .toList()
                .forEach(this::removeTerminal);

        this.projectsUpdated = true;
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
        if (sessionId.equals(this.currentSession)) {
            updateCurrentSession(null);
        }
        this.projectsUpdated = true;
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
        final var terminalId = TerminalId.create();
        this.terminals.put(terminalId, terminal);
        this.updateSession(sessionId, session.withNewTerminal(terminalId));
        this.projectsUpdated = true;
        this.terminalEvents.add(new TerminalEvent(TerminalAction.ADD, terminalId));
        return terminalId;
    }

    public TerminalId addTerminal(final Terminal terminal) {
        if (terminal.projectId() == null || !this.projects.containsKey(terminal.projectId())) {
            throw new IllegalArgumentException("Project terminal must have a project ID.");
        }
        final var terminalId = TerminalId.create();
        this.terminals.put(terminalId, terminal);
        this.projectsUpdated = true;
        this.terminalEvents.add(new TerminalEvent(TerminalAction.ADD, terminalId));
        return terminalId;
    }

    public void removeTerminal(final TerminalId terminalId) {
        final var terminal = this.terminals.get(terminalId);
        if (terminal == null) {
            return;
        }

        terminals.remove(terminalId);
        this.terminalEvents.add(new TerminalEvent(TerminalAction.REMOVE, terminalId));
        final var session = this.sessions.get(terminal.sessionId());
        if (session != null) {
            this.updateSession(terminal.sessionId(), session.withRemovedTerminal(terminalId));
        }
        this.projectsUpdated = true;
    }

    public void updateTerminal(final TerminalId terminalId, final Terminal terminal) {
        this.terminals.put(terminalId, terminal);
        this.projectsUpdated = true;
    }
}
