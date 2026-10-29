package com.jagent.desktop.ui.actions;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.test.TestAppState;
import java.io.InvalidObjectException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActionEdgeCaseTest {
    private static final String ASSERTION_MESSAGE = "action behavior should match";
    @TempDir private Path tempDirectory;

    @Test
    void commandAndDirectoryActionsUseProjectThenSessionPaths() throws InvalidObjectException {
        final AppState state = TestAppState.empty();
        final var projectId = state.addProject(new Project("Demo", tempDirectory.toString(), null));
        final var coordinator = TestAppState.controller(state);
        coordinator.selectProject(projectId);
        final var context = new ActionContext(coordinator, state, null, null, null);
        final var command = new RunCommandAction(context, "Run", "printf test");
        final var directory = new OpenDirectoryAction(context);
        assertTrue(command.enabled(), ASSERTION_MESSAGE);
        assertTrue(directory.enabled(), ASSERTION_MESSAGE);

        final var sessionId =
                state.addSession(
                        projectId,
                        new Session(projectId, "Feature", null, null, tempDirectory.toString()));
        coordinator.selectSession(projectId, sessionId);
        assertTrue(command.enabled(), ASSERTION_MESSAGE);
        assertTrue(directory.enabled(), ASSERTION_MESSAGE);
        command.execute();
    }

    @Test
    void actionsWithNoSelectionLeaveHomeViewUnchanged() {
        final AppState state = TestAppState.empty();
        final var coordinator = TestAppState.controller(state);
        coordinator.updateView(ViewId.HOME, ViewController.ViewState.reset());
        final var context = new ActionContext(coordinator, state, null, null, null);
        new BulkCreateSessionsAction(context).execute();
        new CreateSessionAction(context).execute();
        new ImportBranchAction(context).execute();
        new ImportWorktreeAction(context).execute();
        new OpenProjectAction(context).execute();
        new OpenSessionAction(context).execute();
        new PasteSessionsAction(context).execute();
        new RemoveProjectAction(context).execute();
        new RemoveSessionAction(context).execute();
        new RunCommandAction(context, "Run", "printf test").execute();
        new UpdateBranchAction(context).execute();
        assertEquals(ViewId.HOME, coordinator.currentViewId(), ASSERTION_MESSAGE);
        assertTrue(state.projects().isEmpty(), ASSERTION_MESSAGE);
        assertTrue(state.sessions().isEmpty(), ASSERTION_MESSAGE);
    }

    @Test
    void projectDependentActionsReportSelectionState() {
        final AppState state = TestAppState.empty();
        final var coordinator = TestAppState.controller(state);
        final var context = new ActionContext(coordinator, state, null, null, null);
        final var bulk = new BulkCreateSessionsAction(context);
        final var paste = new PasteSessionsAction(context);
        final var update = new UpdateBranchAction(context);
        assertFalse(bulk.enabled(), ASSERTION_MESSAGE);
        assertFalse(paste.enabled(), ASSERTION_MESSAGE);
        assertFalse(update.enabled(), ASSERTION_MESSAGE);

        final var projectId = state.addProject(new Project("Demo", tempDirectory.toString(), null));
        coordinator.selectProject(projectId);
        coordinator.updateView(ViewId.PROJECT, ViewController.ViewState.project(projectId));
        assertTrue(bulk.enabled(), ASSERTION_MESSAGE);
        assertTrue(paste.enabled(), ASSERTION_MESSAGE);
        assertTrue(update.enabled(), ASSERTION_MESSAGE);
        paste.execute();
        assertEquals(ViewId.PROJECT, coordinator.currentViewId(), ASSERTION_MESSAGE);
    }

    @Test
    void refreshSettingsInvokesCallback() {
        final AppState state = TestAppState.empty();
        final AtomicInteger refreshes = new AtomicInteger();
        final var context =
                new ActionContext(
                        TestAppState.controller(state),
                        state,
                        null,
                        null,
                        refreshes::incrementAndGet);
        new RefreshSettingsAction(context).execute();
        assertEquals(1, refreshes.get(), ASSERTION_MESSAGE);
    }

    @Test
    void refreshSettingsReportsIllegalStateFromCallback() {
        final AppState state = TestAppState.empty();
        final var context =
                new ActionContext(
                        TestAppState.controller(state),
                        state,
                        null,
                        null,
                        () -> {
                            throw new IllegalStateException("settings are invalid");
                        });
        assertDoesNotThrow(() -> new RefreshSettingsAction(context).execute());
    }

    @Test
    void openSettingsReturnsWhenNoEditorIsConfigured() {
        final AppState state = TestAppState.empty();
        final var context =
                new ActionContext(TestAppState.controller(state), state, null, tempDirectory, null);
        assertDoesNotThrow(() -> new OpenSettingsAction(context).execute());
    }

    @Test
    void runCommandReportsInvalidPath() {
        assertDoesNotThrow(() -> RunCommandAction.run("printf test", "\0", "Run", null));
    }
}
