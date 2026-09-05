package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundJobs;
import com.jagent.desktop.services.SessionCreationService;
import com.jagent.desktop.services.terminal.TerminalState;
import com.jagent.desktop.test.AsyncTestSupport;
import com.jagent.desktop.test.TestAppState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionLauncherTest {
    private TerminalId terminalId;

    @AfterEach
    void disposeTerminal() {
        final TerminalPanel panel = terminalId == null ? null : TerminalPanel.existing(terminalId);
        if (panel != null) {
            panel.dispose();
        }
    }

    @Test
    void launchesSessionTerminalImmediatelyWhenNoStartupCommandsExist(@TempDir final Path directory)
            throws IOException, InterruptedException {
        final Fixture fixture = fixture(directory, List.of());

        new SessionLauncher(fixture.context()).launch(fixture.project(), fixture.created());

        assertEquals(
                ViewId.SESSION,
                fixture.context().viewCoordinator().currentViewId(),
                "session view should open");
        assertEquals(
                fixture.sessionId(),
                fixture.state().currentSessionId(),
                "session should be selected");
        assertEquals(
                terminalId, fixture.state().currentTerminalId(), "terminal should be selected");
        assertNotNull(TerminalPanel.existing(terminalId), "terminal panel should be retained");
        AsyncTestSupport.await(
                () -> TerminalPanel.existing(terminalId).state() != TerminalState.STARTING,
                "terminal should finish starting");
    }

    @Test
    void runsStartupCommandsInOrderAndStartsTerminalAfterCompletion(@TempDir final Path directory)
            throws IOException, InterruptedException {
        final Fixture fixture =
                fixture(
                        directory,
                        List.of("printf first >> setup.log", "printf second >> setup.log"));

        new SessionLauncher(fixture.context()).launch(fixture.project(), fixture.created());

        AsyncTestSupport.await(
                () ->
                        fixture.context().viewCoordinator().backgroundJobs().jobs().stream()
                                .anyMatch(job -> job.status() == BackgroundJobs.Status.SUCCEEDED),
                "startup job should complete");
        assertEquals(
                "firstsecond",
                Files.readString(directory.resolve("setup.log")),
                "startup commands should run in order");
        assertEquals(
                "Complete",
                fixture.context().viewCoordinator().backgroundJobs().jobs().getFirst().message(),
                "startup completion should be reported");
        AsyncTestSupport.await(
                () -> TerminalPanel.existing(terminalId).state() != TerminalState.STARTING,
                "terminal should finish starting");
    }

    @Test
    void stopsStartupCommandsAfterFailure(@TempDir final Path directory)
            throws IOException, InterruptedException {
        final Fixture fixture =
                fixture(directory, List.of("printf failed-output; exit 7", "touch skipped"));

        new SessionLauncher(fixture.context()).launch(fixture.project(), fixture.created());

        AsyncTestSupport.await(
                () ->
                        fixture.context().viewCoordinator().backgroundJobs().jobs().stream()
                                .anyMatch(job -> job.status() == BackgroundJobs.Status.FAILED),
                "startup job should fail");
        final BackgroundJobs.Job job =
                fixture.context().viewCoordinator().backgroundJobs().jobs().getFirst();
        assertEquals("failed-output", job.message(), "command output should explain the failure");
        assertFalse(Files.exists(directory.resolve("skipped")), "later commands should not run");
    }

    @Test
    void reportsFallbackMessageForFailureWithoutOutput(@TempDir final Path directory)
            throws IOException, InterruptedException {
        final Fixture fixture = fixture(directory, List.of("exit 7"));

        new SessionLauncher(fixture.context()).launch(fixture.project(), fixture.created());

        AsyncTestSupport.await(
                () ->
                        fixture.context().viewCoordinator().backgroundJobs().jobs().stream()
                                .anyMatch(job -> job.status() == BackgroundJobs.Status.FAILED),
                "startup job should fail");
        assertEquals(
                "Setup command failed.",
                fixture.context().viewCoordinator().backgroundJobs().jobs().getFirst().message(),
                "blank command output should use the fallback message");
    }

    private Fixture fixture(final Path directory, final List<String> startupCommands)
            throws IOException {
        final AppState state = TestAppState.empty();
        final Project project =
                new Project(
                        "Demo",
                        directory.toString(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        startupCommands,
                        List.of());
        final var projectId = state.addProject(project);
        final var session = new Session(projectId, "Feature", "agent", "prompt", null);
        final var sessionId = state.addSession(projectId, session);
        terminalId = state.addTerminal(sessionId, new Terminal(sessionId, "Shell", "true"));
        return new Fixture(
                state,
                project,
                sessionId,
                new SessionCreationService.CreatedSession(
                        session, sessionId, terminalId, directory.toString()),
                TestAppState.context(state));
    }

    private record Fixture(
            AppState state,
            Project project,
            com.jagent.desktop.models.SessionId sessionId,
            SessionCreationService.CreatedSession created,
            ActionContext context) {}
}
