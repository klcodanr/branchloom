package com.jagent.desktop.ui.actions;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.api.ViewId;
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
        final var projectId =
                state.addProject(Project.create("Demo", tempDirectory.toString(), null));
        final var coordinator = TestAppState.controller(state);
        coordinator.selectProject(projectId);
        final var command =
                new RunCommandAction(
                        coordinator, state, TestAppState.dialogService(), "Run", "printf test");
        final var directory = new OpenDirectoryAction(state, coordinator);
        assertTrue(command.enabled(), ASSERTION_MESSAGE);
        assertTrue(directory.enabled(), ASSERTION_MESSAGE);

        final var sessionId =
                state.addSession(
                        projectId,
                        Session.create(projectId, "Feature", null, null, tempDirectory.toString()));
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
        new BulkCreateSessionsAction(
                        coordinator,
                        state,
                        TestAppState.dialogService(),
                        TestAppState.bulkSessionCreator(state))
                .execute();
        new CreateSessionAction(
                        coordinator,
                        state,
                        TestAppState.backgroundJobs(state),
                        TestAppState.dialogService())
                .execute();
        new ImportBranchAction(coordinator, state, TestAppState.dialogService()).execute();
        new ImportWorktreeAction(coordinator, state, TestAppState.dialogService()).execute();
        new OpenProjectAction(coordinator).execute();
        new OpenSessionAction(coordinator).execute();
        new PasteSessionsAction(
                        coordinator,
                        state,
                        TestAppState.dialogService(),
                        TestAppState.bulkSessionCreator(state))
                .execute();
        new RemoveProjectAction(coordinator, state, TestAppState.dialogService()).execute();
        new RemoveSessionAction(
                        TestAppState.backgroundJobs(state),
                        coordinator,
                        state,
                        TestAppState.dialogService())
                .execute();
        new RunCommandAction(coordinator, state, TestAppState.dialogService(), "Run", "printf test")
                .execute();
        new UpdateBranchAction(coordinator, TestAppState.dialogService()).execute();
        assertEquals(ViewId.HOME, coordinator.currentViewId(), ASSERTION_MESSAGE);
        assertTrue(state.projects().isEmpty(), ASSERTION_MESSAGE);
        assertTrue(state.sessions().isEmpty(), ASSERTION_MESSAGE);
    }

    @Test
    void projectDependentActionsReportSelectionState() {
        final AppState state = TestAppState.empty();
        final var coordinator = TestAppState.controller(state);
        final var bulk =
                new BulkCreateSessionsAction(
                        coordinator,
                        state,
                        TestAppState.dialogService(),
                        TestAppState.bulkSessionCreator(state));
        final var paste =
                new PasteSessionsAction(
                        coordinator,
                        state,
                        TestAppState.dialogService(),
                        TestAppState.bulkSessionCreator(state));
        final var update = new UpdateBranchAction(coordinator, TestAppState.dialogService());
        assertFalse(bulk.enabled(), ASSERTION_MESSAGE);
        assertFalse(paste.enabled(), ASSERTION_MESSAGE);
        assertFalse(update.enabled(), ASSERTION_MESSAGE);

        final var projectId =
                state.addProject(Project.create("Demo", tempDirectory.toString(), null));
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
        final AtomicInteger refreshes = new AtomicInteger();
        new RefreshSettingsAction(refreshes::incrementAndGet, TestAppState.dialogService())
                .execute();
        assertEquals(1, refreshes.get(), ASSERTION_MESSAGE);
    }

    @Test
    void refreshSettingsReportsIllegalStateFromCallback() {
        assertDoesNotThrow(
                () ->
                        new RefreshSettingsAction(
                                        () -> {
                                            throw new IllegalStateException("settings are invalid");
                                        },
                                        TestAppState.dialogService())
                                .execute());
    }

    @Test
    void openSettingsReturnsWhenNoEditorIsConfigured() {
        final AppState state = TestAppState.empty();
        assertDoesNotThrow(
                () ->
                        new OpenSettingsAction(state, tempDirectory, TestAppState.dialogService())
                                .execute());
    }

    @Test
    void runCommandReportsInvalidPath() {
        assertDoesNotThrow(
                () ->
                        RunCommandAction.run(
                                "printf test",
                                "\0",
                                "Run",
                                new com.jagent.desktop.services.dialog.HeadlessDialogService()));
    }
}
