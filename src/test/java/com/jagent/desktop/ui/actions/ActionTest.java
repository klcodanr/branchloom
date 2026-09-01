package com.jagent.desktop.ui.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.api.Action;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.test.TestAppState;
import java.io.InvalidObjectException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActionTest {
    private static final String ASSERTION_MESSAGE = "action behavior should match";
    private static final String PROJECT_NAME = "Demo";
    private static final String SESSION_NAME = "Feature";
    @TempDir private Path tempDirectory;

    @Test
    void navigationActionsUpdateTheExpectedViews() throws InvalidObjectException {
        final AppState state = TestAppState.empty();
        final var projectId =
                state.addProject(new Project(PROJECT_NAME, tempDirectory.toString(), null));
        final var sessionId =
                state.addSession(
                        projectId, new Session(projectId, SESSION_NAME, "agent", "prompt", null));
        final var coordinator = new ViewCoordinator(state);
        final var context = new ActionContext(coordinator, state, null);

        new OpenSettingsAction(context).execute();
        assertEquals(ViewId.SETTINGS, coordinator.currentViewId(), ASSERTION_MESSAGE);
        new ProblemsAction(context).execute();
        assertEquals(ViewId.PROBLEMS, coordinator.currentViewId(), ASSERTION_MESSAGE);
        new ResourceUsageAction(context).execute();
        assertEquals(ViewId.RESOURCE_USAGE, coordinator.currentViewId(), ASSERTION_MESSAGE);

        state.updateCurrentProject(projectId);
        state.updateCurrentSession(sessionId);
        new OpenProjectAction(context).execute();
        assertEquals(ViewId.PROJECT, coordinator.currentViewId(), ASSERTION_MESSAGE);
        new OpenProjectSettingsAction(context).execute();
        assertEquals(ViewId.PROJECT_SETTINGS, coordinator.currentViewId(), ASSERTION_MESSAGE);
        state.updateCurrentSession(sessionId);
        new OpenSessionAction(context).execute();
        assertEquals(ViewId.SESSION, coordinator.currentViewId(), ASSERTION_MESSAGE);
    }

    @Test
    void selectionDependentActionsReportEnablement() throws InvalidObjectException {
        final AppState state = TestAppState.empty();
        final var coordinator = new ViewCoordinator(state);
        final var context = new ActionContext(coordinator, state, null);
        final var projectAction = new OpenProjectAction(context);
        final var sessionAction = new OpenSessionAction(context);
        final var terminalAction = new CreateTerminalAction(context, "Shell", "sh");
        final var createSession = new CreateSessionAction(context);
        final var copyPath = new CopyPathAction(context);
        final var copyBranch = new CopyBranchAction(context);
        final var rename = new RenameSessionAction(context);
        final var remove = new RemoveSessionAction(context);

        assertFalse(projectAction.enabled(), ASSERTION_MESSAGE);
        assertFalse(sessionAction.enabled(), ASSERTION_MESSAGE);
        assertFalse(terminalAction.enabled(), ASSERTION_MESSAGE);
        assertFalse(createSession.enabled(), ASSERTION_MESSAGE);
        assertFalse(copyPath.enabled(), ASSERTION_MESSAGE);
        assertFalse(copyBranch.enabled(), ASSERTION_MESSAGE);
        assertFalse(rename.enabled(), ASSERTION_MESSAGE);
        assertFalse(remove.enabled(), ASSERTION_MESSAGE);

        final var projectId =
                state.addProject(new Project(PROJECT_NAME, tempDirectory.toString(), null));
        state.updateCurrentProject(projectId);
        assertTrue(projectAction.enabled(), ASSERTION_MESSAGE);
        assertTrue(terminalAction.enabled(), ASSERTION_MESSAGE);
        assertTrue(createSession.enabled(), ASSERTION_MESSAGE);
        assertTrue(copyPath.enabled(), ASSERTION_MESSAGE);
        assertFalse(sessionAction.enabled(), ASSERTION_MESSAGE);
    }

    @Test
    void selectionActionsIgnoreMissingSelectionsWithoutOpeningUi() {
        final AppState state = TestAppState.empty();
        final var coordinator = new ViewCoordinator(state);
        final var context = new ActionContext(coordinator, state, null);

        coordinator.updateView(ViewId.HOME, ViewCoordinator.ViewState.reset());
        new OpenProjectAction(context).execute();
        new OpenSessionAction(context).execute();
        new RenameSessionAction(context).execute();
        new RemoveProjectAction(context).execute();
        new RemoveSessionAction(context).execute();
        new CopyBranchAction(context).execute();

        assertEquals(ViewId.HOME, coordinator.currentViewId(), ASSERTION_MESSAGE);
        assertTrue(state.projects().isEmpty(), ASSERTION_MESSAGE);
        assertTrue(state.sessions().isEmpty(), ASSERTION_MESSAGE);
    }

    @Test
    void navigationActionsUseSelectedStateAndTemporaryProjectPath() throws InvalidObjectException {
        final AppState state = TestAppState.empty();
        final var projectId =
                state.addProject(new Project(PROJECT_NAME, tempDirectory.toString(), null));
        final var sessionId =
                state.addSession(
                        projectId,
                        new Session(
                                projectId,
                                SESSION_NAME,
                                "agent",
                                "prompt",
                                tempDirectory.toString()));
        final var coordinator = new ViewCoordinator(state);
        final var context = new ActionContext(coordinator, state, null);

        state.updateCurrentProject(projectId);
        new OpenProjectAction(context).execute();
        assertEquals(ViewId.PROJECT, coordinator.currentViewId(), ASSERTION_MESSAGE);
        assertEquals(projectId, state.currentProjectId(), ASSERTION_MESSAGE);

        state.updateCurrentSession(sessionId);
        new OpenSessionAction(context).execute();
        assertEquals(ViewId.SESSION, coordinator.currentViewId(), ASSERTION_MESSAGE);
        assertEquals(sessionId, state.currentSessionId(), ASSERTION_MESSAGE);
    }

    @Test
    void createTerminalAddsProjectAndSessionTerminalsWithNavigationState()
            throws InvalidObjectException {
        final AppState state = TestAppState.empty();
        final var projectId =
                state.addProject(new Project(PROJECT_NAME, tempDirectory.toString(), null));
        state.updateCurrentProject(projectId);
        final var coordinator = new ViewCoordinator(state);
        final var context = new ActionContext(coordinator, state, null);
        final var action = new CreateTerminalAction(context, "Shell", "sh");

        action.execute();

        assertEquals(ViewId.PROJECT, coordinator.currentViewId(), ASSERTION_MESSAGE);
        assertEquals(1, state.terminals().size(), ASSERTION_MESSAGE);
        final var terminalId = state.terminals().keySet().iterator().next();
        assertEquals(
                terminalId,
                coordinator.currentViewId() == ViewId.PROJECT ? state.currentTerminalId() : null,
                ASSERTION_MESSAGE);

        final var sessionId =
                state.addSession(projectId, new Session(projectId, SESSION_NAME, null, null, null));
        state.updateCurrentSession(sessionId);
        action.execute();
        assertEquals(ViewId.SESSION, coordinator.currentViewId(), ASSERTION_MESSAGE);
        assertEquals(2, state.terminals().size(), ASSERTION_MESSAGE);
    }

    @Test
    void actionMetadataIsStable() {
        final AppState state = TestAppState.empty();
        final var context = new ActionContext(new ViewCoordinator(state), state, null);
        final List<Action> actions =
                List.of(
                        new CreateProjectAction(context),
                        new FindAction(context),
                        new AboutAction(context),
                        new ShortcutsAction(context),
                        new OpenSettingsAction(context),
                        new ProblemsAction(context),
                        new ResourceUsageAction(context),
                        new CopyPathAction(context),
                        new CopyBranchAction(context),
                        new RunCommandAction(context, "Run", "printf test"),
                        new OpenDirectoryAction(context),
                        new ImportBranchAction(context),
                        new ImportWorktreeAction(context),
                        new RenameSessionAction(context),
                        new RemoveProjectAction(context),
                        new RemoveSessionAction(context));

        for (final Action action : actions) {
            assertFalse(action.id().isBlank(), ASSERTION_MESSAGE);
            assertFalse(action.label().isBlank(), ASSERTION_MESSAGE);
        }
        assertEquals("run-command-run", actions.get(9).id(), ASSERTION_MESSAGE);
    }

    @Test
    void commandAndDirectoryActionsUseProjectThenSessionPaths() throws InvalidObjectException {
        final AppState state = TestAppState.empty();
        final var projectId =
                state.addProject(new Project(PROJECT_NAME, tempDirectory.toString(), null));
        state.updateCurrentProject(projectId);
        final var coordinator = new ViewCoordinator(state);
        final var context = new ActionContext(coordinator, state, null);
        final var command = new RunCommandAction(context, "Run", "printf test");
        final var directory = new OpenDirectoryAction(context);

        assertTrue(command.enabled(), ASSERTION_MESSAGE);
        assertTrue(directory.enabled(), ASSERTION_MESSAGE);

        final var sessionId =
                state.addSession(
                        projectId,
                        new Session(projectId, SESSION_NAME, null, null, tempDirectory.toString()));
        state.updateCurrentSession(sessionId);
        assertTrue(command.enabled(), ASSERTION_MESSAGE);
        assertTrue(directory.enabled(), ASSERTION_MESSAGE);
        command.execute();
    }
}
