package com.jagent.desktop.ui.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.test.TestAppState;
import com.jagent.desktop.ui.components.Theme;
import java.io.InvalidObjectException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NavigationActionTest {
    private static final String ASSERTION_MESSAGE = "action behavior should match";
    private static final String PROJECT_NAME = "Demo";
    private static final String SESSION_NAME = "Feature";
    @TempDir private Path tempDirectory;

    @BeforeAll
    static void configureTheme() {
        Theme.apply(Theme.FlatLafTheme.LIGHT);
    }

    @Test
    void navigationActionsUpdateTheExpectedViews() throws InvalidObjectException {
        final AppState state = TestAppState.empty();
        final var projectId =
                state.addProject(Project.create(PROJECT_NAME, tempDirectory.toString(), null));
        final var sessionId =
                state.addSession(
                        projectId,
                        Session.create(projectId, SESSION_NAME, "agent", "prompt", null));
        final var coordinator = TestAppState.controller(state);
        coordinator.selectProject(projectId);
        coordinator.selectSession(projectId, sessionId);

        new ProblemsAction(coordinator).execute();
        assertEquals(ViewId.PROBLEMS, coordinator.currentViewId(), ASSERTION_MESSAGE);
        new ResourceUsageAction(coordinator).execute();
        assertEquals(ViewId.RESOURCE_USAGE, coordinator.currentViewId(), ASSERTION_MESSAGE);
        new OpenProjectAction(coordinator).execute();
        assertEquals(ViewId.PROJECT, coordinator.currentViewId(), ASSERTION_MESSAGE);
        new OpenProjectSettingsAction(coordinator).execute();
        assertEquals(ViewId.PROJECT_SETTINGS, coordinator.currentViewId(), ASSERTION_MESSAGE);
        new OpenSessionAction(coordinator).execute();
        assertEquals(ViewId.PROJECT_SETTINGS, coordinator.currentViewId(), ASSERTION_MESSAGE);
    }

    @Test
    void selectionDependentActionsReportEnablement() throws InvalidObjectException {
        final AppState state = TestAppState.empty();
        final var coordinator = TestAppState.controller(state);
        final var dialogService = TestAppState.dialogService();
        final var projectAction = new OpenProjectAction(coordinator);
        final var sessionAction = new OpenSessionAction(coordinator);
        final var terminalAction = new CreateTerminalAction(coordinator, state, "Shell", "sh");
        final var createSession = new CreateSessionAction(coordinator, state, null, dialogService);
        final var copyPath = new CopyPathAction(coordinator, state);
        final var copyBranch = new CopyBranchAction(coordinator, state);
        final var rename = new RenameSessionAction(coordinator, state, dialogService);
        final var remove =
                new RemoveSessionAction(
                        TestAppState.backgroundJobs(state), coordinator, state, dialogService);

        assertFalse(projectAction.enabled(), ASSERTION_MESSAGE);
        assertFalse(sessionAction.enabled(), ASSERTION_MESSAGE);
        assertFalse(terminalAction.enabled(), ASSERTION_MESSAGE);
        assertFalse(createSession.enabled(), ASSERTION_MESSAGE);
        assertFalse(copyPath.enabled(), ASSERTION_MESSAGE);
        assertFalse(copyBranch.enabled(), ASSERTION_MESSAGE);
        assertFalse(rename.enabled(), ASSERTION_MESSAGE);
        assertFalse(remove.enabled(), ASSERTION_MESSAGE);

        final var projectId =
                state.addProject(Project.create(PROJECT_NAME, tempDirectory.toString(), null));
        coordinator.selectProject(projectId);
        assertTrue(projectAction.enabled(), ASSERTION_MESSAGE);
        assertTrue(terminalAction.enabled(), ASSERTION_MESSAGE);
        assertTrue(createSession.enabled(), ASSERTION_MESSAGE);
        assertTrue(copyPath.enabled(), ASSERTION_MESSAGE);
        assertFalse(sessionAction.enabled(), ASSERTION_MESSAGE);

        final var sessionId =
                state.addSession(
                        projectId,
                        Session.create(
                                projectId,
                                SESSION_NAME,
                                "agent",
                                "prompt",
                                tempDirectory.toString()));
        coordinator.selectSession(projectId, sessionId);
        assertTrue(sessionAction.enabled(), ASSERTION_MESSAGE);
        assertTrue(copyBranch.enabled(), ASSERTION_MESSAGE);
        assertTrue(rename.enabled(), ASSERTION_MESSAGE);
        assertTrue(remove.enabled(), ASSERTION_MESSAGE);
    }

    @Test
    void importActionsRequireASelectedProject() {
        final AppState state = TestAppState.empty();
        final var coordinator = TestAppState.controller(state);
        coordinator.updateView(ViewId.HOME, ViewController.ViewState.reset());
        final var importBranch =
                new ImportBranchAction(coordinator, state, TestAppState.dialogService());
        final var importWorktree =
                new ImportWorktreeAction(coordinator, state, TestAppState.dialogService());
        final var importProject =
                new ImportProjectAction(coordinator, state, TestAppState.dialogService());

        assertEquals("import-project", importProject.id(), ASSERTION_MESSAGE);
        assertTrue(importProject.enabled(), ASSERTION_MESSAGE);
        assertFalse(importBranch.enabled(), ASSERTION_MESSAGE);
        assertFalse(importWorktree.enabled(), ASSERTION_MESSAGE);
        importBranch.execute();
        importWorktree.execute();
        assertEquals(ViewId.HOME, coordinator.currentViewId(), ASSERTION_MESSAGE);
    }

    @Test
    void missingSelectionsDoNotOpenUi() {
        final AppState state = TestAppState.empty();
        final var coordinator = TestAppState.controller(state);
        coordinator.updateView(ViewId.HOME, ViewController.ViewState.reset());

        new OpenProjectAction(coordinator).execute();
        new OpenSessionAction(coordinator).execute();
        new RenameSessionAction(coordinator, state, TestAppState.dialogService()).execute();
        new RemoveProjectAction(coordinator, state, TestAppState.dialogService()).execute();
        new RemoveSessionAction(
                        TestAppState.backgroundJobs(state),
                        coordinator,
                        state,
                        TestAppState.dialogService())
                .execute();
        new CopyBranchAction(coordinator, state).execute();

        assertEquals(ViewId.HOME, coordinator.currentViewId(), ASSERTION_MESSAGE);
        assertTrue(state.projects().isEmpty(), ASSERTION_MESSAGE);
        assertTrue(state.sessions().isEmpty(), ASSERTION_MESSAGE);
    }

    @Test
    void projectAndSessionActionsUseSelectedState() throws InvalidObjectException {
        final AppState state = TestAppState.empty();
        final var projectId =
                state.addProject(Project.create(PROJECT_NAME, tempDirectory.toString(), null));
        final var sessionId =
                state.addSession(
                        projectId,
                        Session.create(
                                projectId,
                                SESSION_NAME,
                                "agent",
                                "prompt",
                                tempDirectory.toString()));
        final var coordinator = TestAppState.controller(state);
        coordinator.selectProject(projectId);
        coordinator.selectSession(projectId, sessionId);
        new OpenProjectAction(coordinator).execute();
        assertEquals(ViewId.PROJECT, coordinator.currentViewId(), ASSERTION_MESSAGE);
        assertEquals(projectId, coordinator.currentProjectId(), ASSERTION_MESSAGE);
        assertNull(coordinator.currentSessionId(), ASSERTION_MESSAGE);
        new OpenSessionAction(coordinator).execute();
        assertEquals(ViewId.PROJECT, coordinator.currentViewId(), ASSERTION_MESSAGE);
    }

    @Test
    void createTerminalNavigatesForProjectAndSession() throws InvalidObjectException {
        final AppState state = TestAppState.empty();
        final var projectId =
                state.addProject(Project.create(PROJECT_NAME, tempDirectory.toString(), null));
        final var coordinator = TestAppState.controller(state);
        coordinator.selectProject(projectId);
        final var action = new CreateTerminalAction(coordinator, state, "Shell", "sh");

        action.execute();
        assertEquals(ViewId.PROJECT, coordinator.currentViewId(), ASSERTION_MESSAGE);
        assertEquals(1, state.terminals().size(), ASSERTION_MESSAGE);
        assertEquals(1, coordinator.currentTerminalId() == null ? 0 : 1, ASSERTION_MESSAGE);

        final var sessionId =
                state.addSession(
                        projectId, Session.create(projectId, SESSION_NAME, null, null, null));
        coordinator.selectSession(projectId, sessionId);
        action.execute();
        assertEquals(ViewId.SESSION, coordinator.currentViewId(), ASSERTION_MESSAGE);
        assertEquals(2, state.terminals().size(), ASSERTION_MESSAGE);
    }
}
