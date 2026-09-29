package com.jagent.desktop.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.services.AppState;
import com.jagent.desktop.ui.Defaults;
import java.io.InvalidObjectException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProjectSessionModelLogicTest {
    private static final String VALUE_MESSAGE = "core value should match";
    private static final String DEMO_NAME = "Demo";
    private static final String DEMO_PATH = "/tmp/demo";

    @Test
    void projectCanBeAssignedToAGroupWithoutChangingItsConfiguration() {
        final Project project = new Project(DEMO_NAME, DEMO_PATH, null);

        final Project grouped = project.withGroup("Work");

        assertEquals("Work", grouped.group(), "assigned group should be retained");
        assertEquals(project.name(), grouped.name(), "group assignment should preserve the name");
        assertEquals(project.path(), grouped.path(), "group assignment should preserve the path");
        assertEquals(
                project.startupCommands(),
                grouped.startupCommands(),
                "group assignment should preserve project configuration");
    }

    @Test
    void modelValueObjectsValidateAndPreserveRelationships() throws InvalidObjectException {
        assertThrows(IllegalArgumentException.class, () -> new ProjectId(null));
        assertThrows(IllegalArgumentException.class, () -> new SessionId(null));
        assertThrows(IllegalArgumentException.class, () -> new TerminalId(null));

        final Project project = new Project(DEMO_NAME, DEMO_PATH, null);
        final ProjectId sessionProjectId = ProjectId.create();
        final Session session =
                new Session(sessionProjectId, "Feature", "agent", "prompt", "/tmp/work");
        final Session emptySession =
                new Session(sessionProjectId, "Empty", null, null, null, null, null);
        final Session renamed = session.withName("Renamed");
        assertEquals("Renamed", renamed.name(), "session name should change");
        assertEquals(session.created(), renamed.created(), "rename should preserve creation time");
        assertTrue(emptySession.terminalIds().isEmpty(), "null terminals should default to empty");

        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ProjectId storedProjectId = state.addProject(project);
        final SessionId sessionId = state.addSession(storedProjectId, session);
        final var terminalId =
                state.addTerminal(
                        sessionId, new Terminal(sessionId, storedProjectId, "Shell", "sh"));
        assertEquals(
                List.of(terminalId), state.sessions().get(sessionId).terminalIds(), VALUE_MESSAGE);
        state.removeTerminal(terminalId);
        assertTrue(
                state.sessions().get(sessionId).terminalIds().isEmpty(),
                "terminal should be removed");

        final Agent agent = new Agent("Agent", "agent --prompt {prompt}");
        assertEquals("Agent", agent.toString(), VALUE_MESSAGE);
        assertEquals("agent --prompt {prompt}", agent.newSessionCommand, VALUE_MESSAGE);
        assertEquals("agent --prompt ", agent.openCommand, VALUE_MESSAGE);
    }

    @Test
    void relationshipHelpersCoverEmptyAndPopulatedCollections() {
        final ProjectId projectId = ProjectId.create();
        final Project project = new Project(DEMO_NAME, DEMO_PATH, null);
        final Session session = new Session(projectId, "Feature", "agent", "prompt", "/tmp/work");
        final SessionId sessionId = SessionId.create();
        final TerminalId terminalId = TerminalId.create();
        final Project withSession = project.withNewSession(sessionId);
        final Project nullSessions =
                new Project(
                        "Null sessions",
                        "/tmp/null",
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null);
        final Session withTerminal = session.withNewTerminal(terminalId);

        assertEquals(
                List.of(),
                project.projectSessions(
                        new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of())),
                "missing sessions should produce no entries");
        assertEquals(List.of(sessionId), withSession.sessionIds(), VALUE_MESSAGE);
        assertTrue(nullSessions.sessionIds().isEmpty(), "null sessions should default to empty");
        assertEquals(
                List.of(), withSession.withRemovedSession(sessionId).sessionIds(), VALUE_MESSAGE);
        assertEquals(List.of(terminalId), withTerminal.terminalIds(), VALUE_MESSAGE);
        assertEquals(
                List.of(),
                withTerminal.withRemovedTerminal(terminalId).terminalIds(),
                VALUE_MESSAGE);
    }

    @Test
    void projectSessionsResolvesStoredSessionIds() throws InvalidObjectException {
        final Project project = new Project(DEMO_NAME, DEMO_PATH, null);
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ProjectId storedProjectId = state.addProject(project);
        final SessionId sessionId =
                state.addSession(
                        storedProjectId, new Session(storedProjectId, "Stored", null, null, null));
        final Project stored = state.projects().get(storedProjectId);

        assertEquals(
                List.of(Map.entry(sessionId, state.sessions().get(sessionId))),
                stored.projectSessions(state),
                "project session references should resolve through app state");
    }
}
