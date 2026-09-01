package com.jagent.desktop.test;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.Defaults;
import java.io.InvalidObjectException;
import java.nio.file.Path;
import java.util.Map;

/** Common application-state fixtures for action and view tests. */
@SuppressWarnings("PMD.TestClassWithoutTestCases")
public final class TestAppState {
    private TestAppState() {}

    public static AppState empty() {
        return new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
    }

    public static ProjectId addProject(final AppState state, final Path directory) {
        return state.addProject(new Project("Demo", directory.toString(), null));
    }

    public static SessionId addSession(final AppState state, final ProjectId projectId)
            throws InvalidObjectException {
        return state.addSession(
                projectId, new Session(projectId, "Feature", "agent", "prompt", null));
    }

    public static ActionContext context(final AppState state) {
        return new ActionContext(new ViewCoordinator(state), state, null);
    }
}
