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
import com.jagent.desktop.ui.Defaults;
import java.io.InvalidObjectException;
import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ViewCoordinatorTest {
    @Test
    void updateViewUpdatesSelectionAndNotifiesListener() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var changedViews = new ArrayList<ViewId>();
        final var coordinator = new ViewCoordinator(state, changedViews::add);
        final var projectId = ProjectId.create();

        coordinator.updateView(ViewId.PROJECT, ViewCoordinator.ViewState.project(projectId));

        assertEquals(ViewId.PROJECT, coordinator.currentViewId(), "view should be updated");
        assertEquals(projectId, state.currentProjectId(), "project should be selected");
        assertEquals(
                java.util.List.of(ViewId.PROJECT),
                changedViews,
                "listener should receive the updated view");
    }

    @Test
    void updateViewWithoutStateOnlyChangesTheView() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var coordinator = new ViewCoordinator(state);

        coordinator.updateView(ViewId.HOME, null);

        assertEquals(ViewId.HOME, coordinator.currentViewId(), "assertion values should match");
        assertNull(state.currentProjectId(), "assertion condition should hold");
        assertNull(state.currentSessionId(), "assertion condition should hold");
        assertNull(state.currentTerminalId(), "assertion condition should hold");
    }

    @Test
    void updateViewAppliesProjectSessionAndTerminalSelection() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var coordinator = new ViewCoordinator(state);
        final var projectId = ProjectId.create();
        final var sessionId = SessionId.create();
        final var terminalId = TerminalId.create();

        coordinator.updateView(
                ViewId.SESSION,
                ViewCoordinator.ViewState.sessionTerminal(projectId, sessionId, terminalId));

        assertEquals(projectId, state.currentProjectId(), "project selection should be applied");
        assertEquals(sessionId, state.currentSessionId(), "session selection should be applied");
        assertEquals(terminalId, state.currentTerminalId(), "terminal selection should be applied");
        assertEquals(
                ViewId.SESSION, coordinator.currentViewId(), "view selection should be applied");
    }

    @Test
    void selectedTabIsPreservedPerView() {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var coordinator = new ViewCoordinator(state);

        coordinator.updateSelectedTab(ViewId.HOME, 2);
        coordinator.updateSelectedTab(ViewId.SETTINGS, 3);

        assertEquals(2, coordinator.selectedTab(ViewId.HOME), "home tab should be preserved");
        assertEquals(
                3, coordinator.selectedTab(ViewId.SETTINGS), "settings tab should be preserved");
        assertEquals(0, coordinator.selectedTab(ViewId.PROJECT), "unknown views should default");
    }

    @Test
    void selectedTabIsPreservedPerSession() throws InvalidObjectException {
        final AppState state = new AppState(Defaults.appSettings(), Map.of(), Map.of(), Map.of());
        final var coordinator = new ViewCoordinator(state);
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
        final var coordinator = new ViewCoordinator(state);
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
}
