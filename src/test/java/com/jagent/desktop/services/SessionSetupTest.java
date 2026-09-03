package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.ui.Defaults;
import java.io.InvalidObjectException;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SessionSetupTest {
    @Test
    void setupSessionRemainsInMemoryUntilPromoted() throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final ProjectId projectId = state.addProject(new Project("Demo", "/tmp/demo", null));
        state.snapshotForPersistence();
        final Session draft = new Session(projectId, "Feature", "agent", "prompt", "");
        final SessionSetup setup = new SessionSetup();
        final var sessionId = setup.begin(draft);
        final var updates = new ArrayList<SessionSetup.SetupProgress>();
        setup.listen(sessionId, updates::add);

        setup.update(sessionId, "Creating worktree...");

        assertEquals(draft, setup.session(sessionId), "draft should remain in setup state");
        assertEquals(
                "Creating worktree...",
                updates.getLast().message(),
                "listeners should receive progress updates");
        assertTrue(state.sessions().isEmpty(), "draft should not be persisted");
        assertNull(state.snapshotForPersistence(), "setup progress should not dirty persistence");

        final Session completed =
                new Session(projectId, "Feature", "agent", "prompt", "/tmp/worktree");
        final var persistedId = setup.promote(state, sessionId, completed);

        assertEquals(
                completed,
                state.sessions().get(persistedId),
                "promoted session should be persisted");
        assertEquals(
                1,
                state.projects().get(projectId).sessionIds().size(),
                "project should reference the promoted session");
        assertNull(setup.session(sessionId), "setup session should be removed after promotion");
        assertNull(setup.progress(sessionId), "setup progress should be removed after promotion");
        final var promotedProgress = setup.progress(persistedId);
        assertNotNull(promotedProgress, "promoted session should retain setup progress");
        assertEquals(
                "Creating worktree...",
                promotedProgress.message(),
                "promoted session should retain setup progress");

        setup.update(persistedId, "Starting agent...");
        assertEquals(
                "Starting agent...",
                updates.getLast().message(),
                "promoted session should continue notifying progress listeners");
    }
}
