package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.*;

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
        final ProjectId projectId = state.addProject(Project.create(DEMO, DEMO_PATH, null));

        final Session session = Session.create(projectId, FEATURE, AGENT, PROMPT, WORKTREE);
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
        final Project project = Project.create(DEMO, DEMO_PATH, null);
        state.addProject(project);

        final var snapshot = state.snapshotForPersistence();

        assertNotNull(snapshot, "changed state should produce a snapshot");
        assertEquals(1, snapshot.projects().size(), "snapshot should contain the project");
        assertNull(state.snapshotForPersistence(), "snapshot should clear pending changes");
    }

    @Test
    void projectAndTerminalUpdatesCascadeOnRemoval() throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ProjectId projectId = state.addProject(Project.create(DEMO, DEMO_PATH, null));
        final SessionId sessionId =
                state.addSession(
                        projectId, Session.create(projectId, FEATURE, AGENT, PROMPT, WORKTREE));
        final var terminalId =
                state.addTerminal(
                        sessionId,
                        com.jagent.desktop.models.Terminal.create(sessionId, SHELL, "sh"));

        state.updateTerminal(
                terminalId,
                com.jagent.desktop.models.Terminal.create(sessionId, "Updated", "bash"));
        state.updateSession(sessionId, state.sessions().get(sessionId).withName("Renamed"));
        state.updateProject(projectId, state.projects().get(projectId).withName("Renamed project"));
        state.addTerminal(com.jagent.desktop.models.Terminal.create(projectId, "Project", "sh"));

        state.removeProject(projectId);

        assertTrue(state.projects().isEmpty(), "project should be removed");
        assertTrue(state.sessions().isEmpty(), "project sessions should be removed");
        assertTrue(state.terminals().isEmpty(), "project terminals should be removed");
    }

    @Test
    void settingsUpdatesAreRuntimeOnly() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        state.updateAppSettings(Defaults.appSettings());

        assertNull(state.snapshotForPersistence(), "settings update should not be persisted");

        state.addProject(Project.create(DEMO, DEMO_PATH, null));
        final var snapshot = state.snapshotForPersistence();
        assertTrue(snapshot.projectsUpdated(), "project update should be persisted");
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
                                projectId, Session.create(projectId, "Missing", null, null, null)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        state.addTerminal(
                                sessionId,
                                com.jagent.desktop.models.Terminal.create(sessionId, SHELL, "sh")));
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
        final ProjectId projectId = state.addProject(Project.create(DEMO, DEMO_PATH, null));
        final SessionId sessionId =
                state.addSession(
                        projectId, Session.create(projectId, FEATURE, AGENT, PROMPT, WORKTREE));
        final var terminalId =
                state.addTerminal(
                        sessionId,
                        com.jagent.desktop.models.Terminal.create(sessionId, SHELL, "sh"));

        final var snapshot = state.snapshotForPersistence();

        assertEquals(
                List.of(new AppState.TerminalEvent(AppState.Action.ADD, terminalId)),
                snapshot.terminalEvents(),
                "terminal additions should be persisted as events");

        state.restorePersistenceUpdates(
                false, List.of(new AppState.TerminalEvent(AppState.Action.REMOVE, terminalId)));
        final var restored = state.snapshotForPersistence();
        assertEquals(
                List.of(new AppState.TerminalEvent(AppState.Action.REMOVE, terminalId)),
                restored.terminalEvents(),
                "restored terminal events should remain pending");
    }

    @Test
    void removingProjectRemovesItsEntities() throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ProjectId projectId = state.addProject(Project.create(DEMO, DEMO_PATH, null));
        final SessionId sessionId =
                state.addSession(
                        projectId, Session.create(projectId, FEATURE, AGENT, PROMPT, WORKTREE));
        final var terminalId =
                state.addTerminal(
                        sessionId,
                        com.jagent.desktop.models.Terminal.create(sessionId, SHELL, "sh"));
        state.removeProject(projectId);

        assertFalse(state.projects().containsKey(projectId), "project should be removed");
        assertFalse(state.sessions().containsKey(sessionId), "session should be removed");
        assertFalse(state.terminals().containsKey(terminalId), "terminal should be removed");
    }
}
