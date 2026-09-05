package com.jagent.desktop.ui.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.test.TestAppState;
import java.io.InvalidObjectException;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CleanupHelpersTest {
    private static final String PROJECT_PATH = "/project";
    private static final String SESSION_NAME = "Feature";

    @Test
    void resolvesCurrentSessionWorktreeBeforeProjectPath() throws InvalidObjectException {
        final AppState state = TestAppState.empty();
        final var projectId = state.addProject(new Project("Demo", PROJECT_PATH, null));
        final var sessionId =
                state.addSession(
                        projectId, new Session(projectId, SESSION_NAME, null, null, "/worktree"));
        state.updateCurrentProject(projectId);
        state.updateCurrentSession(sessionId);

        assertEquals("/worktree", CurrentPath.resolve(state), "session worktree should win");
    }

    @Test
    void resolvesProjectPathWhenSessionHasNoWorktree() throws InvalidObjectException {
        final AppState state = TestAppState.empty();
        final var projectId = state.addProject(new Project("Demo", PROJECT_PATH, null));
        final var sessionId =
                state.addSession(projectId, new Session(projectId, SESSION_NAME, null, null, null));
        state.updateCurrentProject(projectId);
        state.updateCurrentSession(sessionId);

        assertEquals(
                PROJECT_PATH, CurrentPath.resolve(state), "project path should be the fallback");
    }

    @Test
    void createsCaseInsensitiveUniqueSessionNames() {
        final Set<String> names = new HashSet<>();
        names.add("feature");
        names.add("feature-2");

        assertEquals(
                "Feature-3", SessionNames.unique(SESSION_NAME, names), "name should be unique");
    }

    @Test
    void collectsExistingProjectSessionNames() throws InvalidObjectException {
        final AppState state = TestAppState.empty();
        final var projectId = state.addProject(new Project("Demo", PROJECT_PATH, null));
        state.addSession(projectId, new Session(projectId, SESSION_NAME, null, null, null));
        state.updateCurrentProject(projectId);

        assertEquals(
                Set.of("feature"),
                SessionNames.existing(state, state.currentProject()),
                "existing names should be normalized");
    }

    @Test
    void returnsDeepestCauseMessage() {
        final Throwable failure =
                new IllegalStateException("outer", new IllegalArgumentException("inner"));

        assertEquals(
                "inner", ErrorMessages.deepestCause(failure, "fallback"), "deepest cause wins");
    }

    @Test
    void usesFallbackForBlankDeepestCauseMessage() {
        final Throwable failure = new IllegalStateException(new IllegalArgumentException("  "));

        assertEquals(
                "fallback",
                ErrorMessages.deepestCause(failure, "fallback"),
                "blank cause should use fallback");
    }
}
