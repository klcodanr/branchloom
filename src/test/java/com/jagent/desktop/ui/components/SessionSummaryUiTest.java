package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.test.TestAppState;
import org.junit.jupiter.api.Test;

class SessionSummaryUiTest {
    @Test
    void constructsWithCurrentSessionStateApi() throws java.io.InvalidObjectException {
        final AppState state = TestAppState.empty();
        final ProjectId projectId = state.addProject(new Project("Demo", "/tmp/demo", null));
        final var sessionId =
                state.addSession(projectId, new Session(projectId, "Feature", null, null, null));
        assertNotNull(sessionId, "session should be added to the application state");
    }
}
