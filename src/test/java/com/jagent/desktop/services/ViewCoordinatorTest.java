package com.jagent.desktop.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.test.TestAppState;
import com.jagent.desktop.ui.Defaults;
import java.io.InvalidObjectException;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ViewControllerTest {
    @Test
    void updateViewUpdatesSelectionAndNotifiesListener() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var changedViews = new ArrayList<ViewId>();
        final var coordinator = TestAppState.controller(state);
        coordinator.onViewChanged(changedViews::add);
        final var projectId = ProjectId.create();

        coordinator.updateView(ViewId.PROJECT, ViewController.ViewState.project(projectId));

        assertEquals(ViewId.PROJECT, coordinator.currentViewId(), "view should be updated");
        assertEquals(projectId, coordinator.currentProjectId(), "project should be selected");
        assertEquals(
                java.util.List.of(ViewId.PROJECT),
                changedViews,
                "listener should receive the updated view");
    }

    @Test
    void updateViewWithoutStateOnlyChangesTheView() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var coordinator = TestAppState.controller(state);

        coordinator.updateView(ViewId.HOME, null);

        assertEquals(ViewId.HOME, coordinator.currentViewId(), "assertion values should match");
        assertNull(coordinator.currentProjectId(), "assertion condition should hold");
        assertNull(coordinator.currentSessionId(), "assertion condition should hold");
        assertNull(coordinator.currentTerminalId(), "assertion condition should hold");
    }

    @Test
    void updateViewAppliesProjectSessionAndTerminalSelection() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var coordinator = TestAppState.controller(state);
        final var projectId = ProjectId.create();
        final var sessionId = SessionId.create();
        final var terminalId = TerminalId.create();

        coordinator.updateView(
                ViewId.SESSION,
                ViewController.ViewState.sessionTerminal(projectId, sessionId, terminalId));

        assertEquals(
                projectId, coordinator.currentProjectId(), "project selection should be applied");
        assertEquals(
                sessionId, coordinator.currentSessionId(), "session selection should be applied");
        assertEquals(
                terminalId,
                coordinator.currentTerminalId(),
                "terminal selection should be applied");
        assertEquals(
                ViewId.SESSION, coordinator.currentViewId(), "view selection should be applied");
    }

    @Test
    void openingAnotherProjectClearsSessionAndTerminalSelection() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var controller = TestAppState.controller(state);
        final var firstProject = ProjectId.create();
        final var secondProject = ProjectId.create();
        final var session = SessionId.create();
        final var terminal = TerminalId.create();

        controller.updateView(
                ViewId.SESSION,
                ViewController.ViewState.sessionTerminal(firstProject, session, terminal));
        controller.openProject(secondProject);

        assertEquals(
                secondProject, controller.currentProjectId(), "new project should be selected");
        assertNull(controller.currentSessionId(), "old session should be cleared");
        assertNull(controller.currentTerminalId(), "old terminal should be cleared");
    }

    @Test
    void openingTheCurrentProjectFromASessionClearsSessionAndTerminalSelection() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var controller = TestAppState.controller(state);
        final var projectId = ProjectId.create();
        final var session = SessionId.create();
        final var terminal = TerminalId.create();

        controller.updateView(
                ViewId.SESSION,
                ViewController.ViewState.sessionTerminal(projectId, session, terminal));
        controller.openProject(projectId);

        assertEquals(projectId, controller.currentProjectId(), "project should remain selected");
        assertNull(controller.currentSessionId(), "session should be cleared");
        assertNull(controller.currentTerminalId(), "session terminal should be cleared");
        assertEquals(ViewId.PROJECT, controller.currentViewId(), "project view should be opened");
    }

    @Test
    void openingTheCurrentProjectAlwaysClearsSessionSelection() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var controller = TestAppState.controller(state);
        final var projectId = ProjectId.create();
        final var session = SessionId.create();

        controller.updateView(ViewId.PROJECT, ViewController.ViewState.session(projectId, session));
        controller.openProject(projectId);

        assertNull(controller.currentSessionId(), "project navigation should clear session");
    }

    @Test
    void selectedTabIsPreservedPerView() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var coordinator = TestAppState.controller(state);

        coordinator.updateSelectedTab(ViewId.HOME, 2);

        assertEquals(2, coordinator.selectedTab(ViewId.HOME), "home tab should be preserved");
        assertEquals(0, coordinator.selectedTab(ViewId.PROJECT), "unknown views should default");
    }

    @Test
    void selectedTabIsPreservedPerSession() throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var coordinator = TestAppState.controller(state);
        final ProjectId projectId = state.addProject(new Project("Project", "/tmp", null));
        final SessionId firstSessionId =
                state.addSession(projectId, new Session(projectId, "First", null, null, null));
        final SessionId secondSessionId =
                state.addSession(projectId, new Session(projectId, "Second", null, null, null));

        coordinator.updateSelectedSessionTab(firstSessionId, 2);
        coordinator.updateSelectedSessionTab(secondSessionId, 1);

        assertEquals(
                2,
                coordinator.selectedSessionTab(firstSessionId),
                "first session tab should be preserved");
        assertEquals(
                1,
                coordinator.selectedSessionTab(secondSessionId),
                "second session tab should be preserved");
        assertEquals(
                0,
                coordinator.selectedSessionTab(SessionId.create()),
                "unknown sessions should default");
    }

    @Test
    void selectedSessionTabIsCleanedUpWhenSessionIsRemoved() throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var coordinator = TestAppState.controller(state);
        final ProjectId projectId = state.addProject(new Project("Project", "/tmp", null));
        final SessionId removedSessionId =
                state.addSession(projectId, new Session(projectId, "Removed", null, null, null));
        final SessionId remainingSessionId =
                state.addSession(projectId, new Session(projectId, "Remaining", null, null, null));

        coordinator.updateSelectedSessionTab(removedSessionId, 2);
        coordinator.updateSelectedSessionTab(remainingSessionId, 1);
        assertTrue(
                coordinator.hasSelectedSessionTab(removedSessionId),
                "removed session should have tracked tab before removal");

        state.removeSession(removedSessionId);

        assertFalse(
                coordinator.hasSelectedSessionTab(removedSessionId),
                "removed session tab state should be cleaned up");
        assertEquals(
                0,
                coordinator.selectedSessionTab(removedSessionId),
                "removed session should fall back to default tab");
        assertTrue(
                coordinator.hasSelectedSessionTab(remainingSessionId),
                "remaining session tab state should stay available");
        assertEquals(
                1,
                coordinator.selectedSessionTab(remainingSessionId),
                "remaining session tab should be preserved");
    }

    @Test
    void viewStateFactoriesPreserveSelections() {
        final ProjectId projectId = ProjectId.create();
        final SessionId sessionId = SessionId.create();
        final TerminalId terminalId = TerminalId.create();

        assertNull(ViewController.ViewState.reset().newProjectId(), "reset should clear project");
        assertEquals(
                projectId,
                ViewController.ViewState.project(projectId).newProjectId(),
                "project state should preserve project id");
        assertEquals(
                terminalId,
                ViewController.ViewState.projectTerminal(projectId, terminalId).newTerminalId(),
                "project terminal state should preserve terminal");
        assertEquals(
                sessionId,
                ViewController.ViewState.session(projectId, sessionId).newSessionId(),
                "session state should preserve session");
        assertEquals(
                terminalId,
                ViewController.ViewState.sessionTerminal(projectId, sessionId, terminalId)
                        .newTerminalId(),
                "session terminal state should preserve terminal");
    }
}
