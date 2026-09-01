package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.ui.Defaults;
import java.io.InvalidObjectException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppStateTest {
    private static final String DEMO = "Demo";
    private static final String DEMO_PATH = "/tmp/demo";
    private static final String FEATURE = "Feature";
    private static final String AGENT = "agent";
    private static final String PROMPT = "prompt";
    private static final String WORKTREE = "/tmp/worktree";
    private static final String SHELL = "Shell";

    @Test
    void addingAndRemovingSessionKeepsProjectStateInSync() throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ProjectId projectId = state.addProject(new Project(DEMO, DEMO_PATH, null));

        final Session session = new Session(projectId, FEATURE, AGENT, PROMPT, WORKTREE);
        final var sessionId = state.addSession(projectId, session);

        assertEquals(session, state.sessions().get(sessionId), "session should be stored");
        assertEquals(
                List.of(sessionId),
                state.projects().get(projectId).sessionIds(),
                "project should reference the session");

        state.removeSession(sessionId);

        assertNull(state.sessions().get(sessionId), "session should be removed");
        assertEquals(
                List.of(),
                state.projects().get(projectId).sessionIds(),
                "project should no longer reference the session");
    }

    @Test
    void persistenceSnapshotReportsAndClearsUpdates() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final Project project = new Project(DEMO, DEMO_PATH, null);
        state.addProject(project);

        final var snapshot = state.snapshotForPersistence();

        org.junit.jupiter.api.Assertions.assertNotNull(
                snapshot, "changed state should produce a snapshot");
        assertEquals(1, snapshot.projects().size(), "snapshot should contain the project");
        assertNull(state.snapshotForPersistence(), "snapshot should clear pending changes");
    }

    @Test
    void projectAndTerminalUpdatesCascadeOnRemoval() throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ProjectId projectId = state.addProject(new Project(DEMO, DEMO_PATH, null));
        final SessionId sessionId =
                state.addSession(
                        projectId, new Session(projectId, FEATURE, AGENT, PROMPT, WORKTREE));
        final var terminalId =
                state.addTerminal(
                        sessionId, new com.jagent.desktop.models.Terminal(sessionId, SHELL, "sh"));

        state.updateTerminal(
                terminalId, new com.jagent.desktop.models.Terminal(sessionId, "Updated", "bash"));
        state.updateSession(sessionId, state.sessions().get(sessionId).withName("Renamed"));
        state.updateProject(projectId, state.projects().get(projectId).withName("Renamed project"));
        state.addTerminal(new com.jagent.desktop.models.Terminal(null, projectId, "Project", "sh"));

        state.removeProject(projectId);

        assertTrue(state.projects().isEmpty(), "project should be removed");
        assertTrue(state.sessions().isEmpty(), "project sessions should be removed");
        assertTrue(state.terminals().isEmpty(), "project terminals should be removed");
    }

    @Test
    void settingsUpdatesAndRestoreRemainPending() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.updateAppSettings(Defaults.appSettings());
        final var first = state.snapshotForPersistence();
        assertTrue(first.appSettingsUpdated(), "settings update should be persisted");

        state.restorePersistenceUpdates(true, true, List.of());
        final var restored = state.snapshotForPersistence();
        assertTrue(restored.appSettingsUpdated(), "settings update should be restored");
        assertTrue(restored.projectsUpdated(), "project update should be restored");
    }

    @Test
    void missingRelationshipsAreRejectedAndMissingItemsAreIgnored() throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ProjectId projectId = ProjectId.create();
        final SessionId sessionId = SessionId.create();

        assertThrows(
                InvalidObjectException.class,
                () ->
                        state.addSession(
                                projectId, new Session(projectId, "Missing", null, null, null)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        state.addTerminal(
                                sessionId,
                                new com.jagent.desktop.models.Terminal(sessionId, SHELL, "sh")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        state.addTerminal(
                                new com.jagent.desktop.models.Terminal(
                                        null, projectId, "Project", "sh")));

        state.removeProject(projectId);
        state.removeSession(sessionId);
        state.removeTerminal(TerminalId.create());
        assertTrue(state.projects().isEmpty(), "missing removals should be harmless");
    }

    @Test
    void snapshotIncludesTerminalEventsAndRestoresThemInOrder() throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ProjectId projectId = state.addProject(new Project(DEMO, DEMO_PATH, null));
        final SessionId sessionId =
                state.addSession(
                        projectId, new Session(projectId, FEATURE, AGENT, PROMPT, WORKTREE));
        final var terminalId =
                state.addTerminal(
                        sessionId, new com.jagent.desktop.models.Terminal(sessionId, SHELL, "sh"));

        final var snapshot = state.snapshotForPersistence();

        assertEquals(
                List.of(new AppState.TerminalEvent(AppState.TerminalAction.ADD, terminalId)),
                snapshot.terminalEvents(),
                "terminal additions should be persisted as events");

        state.restorePersistenceUpdates(
                false,
                false,
                List.of(new AppState.TerminalEvent(AppState.TerminalAction.REMOVE, terminalId)));
        final var restored = state.snapshotForPersistence();
        assertEquals(
                List.of(new AppState.TerminalEvent(AppState.TerminalAction.REMOVE, terminalId)),
                restored.terminalEvents(),
                "restored terminal events should remain pending");
    }

    @Test
    void currentSelectionsAreClearedWhenTheirEntitiesAreRemoved() throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ProjectId projectId = state.addProject(new Project(DEMO, DEMO_PATH, null));
        final SessionId sessionId =
                state.addSession(
                        projectId, new Session(projectId, FEATURE, AGENT, PROMPT, WORKTREE));
        final var terminalId =
                state.addTerminal(
                        sessionId, new com.jagent.desktop.models.Terminal(sessionId, SHELL, "sh"));
        state.updateCurrentProject(projectId);
        state.updateCurrentSession(sessionId);
        state.updateCurrentTerminal(terminalId);

        state.removeProject(projectId);

        assertNull(state.currentProjectId(), "current project should be cleared");
        assertNull(state.currentSessionId(), "current session should be cleared");
        assertNull(state.currentTerminalId(), "current terminal should be cleared");
    }
}
