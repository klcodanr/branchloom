package com.jagent.desktop.services.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.SessionId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectsJsonParserTest {
    private static final String PROJECT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String SESSION_ID = "22222222-2222-2222-2222-222222222222";
    private static final String TERMINAL_ID = "33333333-3333-3333-3333-333333333333";

    @Test
    void parsesLegacyIdsAndSeparateTerminalOwnerFields() {
        final ProjectsJsonParser.PersistedProjects projects =
                new ProjectsJsonParser()
                        .parse(
                                """
                                 {%n\
                                   "projects": {%n\
                                     "%s": {"name":"Demo","path":"/tmp/demo"}%n\
                                   },%n\
                                   "sessions": {%n\
                                     "%s": {"projectId":{"value":"%s"},"name":"Feature"}%n\
                                   },%n\
                                   "terminals": {%n\
                                     "%s": {"sessionId":{"value":"%s"},"title":"Shell","command":"sh"}%n\
                                   }%n\
                                 }%n\
                                 """
                                        .formatted(
                                                PROJECT_ID,
                                                SESSION_ID,
                                                PROJECT_ID,
                                                TERMINAL_ID,
                                                SESSION_ID));

        assertEquals(
                new ProjectId(UUID.fromString(PROJECT_ID)),
                projects.projects().get(PROJECT_ID).id(),
                "project ID should be parsed");
        assertEquals(
                new SessionId(UUID.fromString(SESSION_ID)),
                projects.sessions().get(SESSION_ID).id(),
                "session ID should be parsed");
        assertEquals(
                new SessionId(UUID.fromString(SESSION_ID)),
                projects.terminals().get(TERMINAL_ID).owner(),
                "terminal owner should be parsed");
        assertEquals(
                UUID.fromString(TERMINAL_ID),
                projects.terminals().get(TERMINAL_ID).id().value(),
                "terminal ID should be parsed");
    }

    @Test
    void serializesTypedTerminalOwnerAndRoundTrips() {
        final ProjectsJsonParser.PersistedProjects original =
                new ProjectsJsonParser()
                        .parse(
                                """
                                 {%n\
                                   "projects": {"%s": {"id":{"value":"%s"},"name":"Demo","path":"/tmp/demo"}},%n\
                                   "sessions": {},%n\
                                   "terminals": {"%s": {"id":{"value":"%s"},"owner":{"value":"%s","type":"ProjectId"},"title":"Shell","command":"sh"}}%n\
                                 }%n\
                                 """
                                        .formatted(
                                                PROJECT_ID,
                                                PROJECT_ID,
                                                TERMINAL_ID,
                                                TERMINAL_ID,
                                                PROJECT_ID));

        final String serialized = new ProjectsJsonParser().serialize(original);
        assertTrue(
                serialized.contains("\"type\": \"ProjectId\""),
                "terminal owner type should be serialized");

        final ProjectsJsonParser.PersistedProjects roundTripped =
                new ProjectsJsonParser().parse(serialized);
        assertEquals(
                new ProjectId(UUID.fromString(PROJECT_ID)),
                roundTripped.terminals().get(TERMINAL_ID).owner(),
                "typed terminal owner should survive round trip");
    }
}
