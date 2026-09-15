package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundJobs;
import com.jagent.desktop.services.SessionCreationService;
import com.jagent.desktop.services.terminal.TerminalRuntime;
import com.jagent.desktop.services.terminal.TerminalState;
import com.jagent.desktop.test.AsyncTestSupport;
import com.jagent.desktop.test.TestAppState;
import com.jediterm.terminal.TtyConnector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionLauncherTest {
    private final AtomicReference<TerminalPanel> lastPanel = new AtomicReference<>();

    @AfterEach
    void disposeTerminal() {
        final TerminalPanel panel = lastPanel.getAndSet(null);
        if (panel != null) {
            GuiActionRunner.execute(panel::dispose);
        }
    }

    @Test
    void launchesSessionTerminalImmediatelyWhenNoStartupCommandsExist(@TempDir final Path directory)
            throws IOException, InterruptedException {
        final Fixture fixture = fixture(directory, List.of());

        fixture.launcher().launch(fixture.project(), fixture.created());

        assertEquals(
                ViewId.SESSION,
                fixture.context().viewCoordinator().currentViewId(),
                "session view should open");
        assertEquals(
                fixture.sessionId(),
                fixture.state().currentSessionId(),
                "session should be selected");
        assertEquals(
                fixture.terminalId(),
                fixture.state().currentTerminalId(),
                "terminal should be selected");
        assertNotNull(fixture.panel(), "terminal panel should be created");
        AsyncTestSupport.await(
                () -> fixture.runtime().state() != TerminalState.STARTING,
                "terminal should finish starting");
        assertEquals(1, fixture.runtime().startCount(), "terminal should start immediately");
    }

    @Test
    void runsStartupCommandsInOrderAndStartsTerminalAfterCompletion(@TempDir final Path directory)
            throws IOException, InterruptedException {
        final Fixture fixture =
                fixture(
                        directory,
                        List.of("printf first >> setup.log", "printf second >> setup.log"));

        fixture.launcher().launch(fixture.project(), fixture.created());

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
                () -> fixture.runtime().state() != TerminalState.STARTING,
                "terminal should finish starting");
        assertEquals(
                1,
                fixture.runtime().startCount(),
                "terminal should start after setup commands complete");
    }

    @Test
    void stopsStartupCommandsAfterFailure(@TempDir final Path directory)
            throws IOException, InterruptedException {
        final Fixture fixture =
                fixture(directory, List.of("printf failed-output; exit 7", "touch skipped"));

        fixture.launcher().launch(fixture.project(), fixture.created());

        AsyncTestSupport.await(
                () ->
                        fixture.context().viewCoordinator().backgroundJobs().jobs().stream()
                                .anyMatch(job -> job.status() == BackgroundJobs.Status.FAILED),
                "startup job should fail");
        final BackgroundJobs.Job job =
                fixture.context().viewCoordinator().backgroundJobs().jobs().getFirst();
        assertEquals("failed-output", job.message(), "command output should explain the failure");
        assertFalse(Files.exists(directory.resolve("skipped")), "later commands should not run");
        assertEquals(
                0,
                fixture.runtime().startCount(),
                "terminal should not auto-start when setup fails in headless tests");
    }

    @Test
    void reportsFallbackMessageForFailureWithoutOutput(@TempDir final Path directory)
            throws IOException, InterruptedException {
        final Fixture fixture = fixture(directory, List.of("exit 7"));

        fixture.launcher().launch(fixture.project(), fixture.created());

        AsyncTestSupport.await(
                () ->
                        fixture.context().viewCoordinator().backgroundJobs().jobs().stream()
                                .anyMatch(job -> job.status() == BackgroundJobs.Status.FAILED),
                "startup job should fail");
        assertEquals(
                "Setup command failed.",
                fixture.context().viewCoordinator().backgroundJobs().jobs().getFirst().message(),
                "blank command output should use the fallback message");
        assertEquals(
                0,
                fixture.runtime().startCount(),
                "terminal should not auto-start when setup fails in headless tests");
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
        final TerminalId terminalId =
                state.addTerminal(sessionId, new Terminal(sessionId, "Shell", "true"));
        final ActionContext context = TestAppState.context(state);
        final AtomicReference<FakeTerminalRuntime> runtime = new AtomicReference<>();
        final AtomicReference<TerminalPanel> panel = new AtomicReference<>();
        final SessionLauncher launcher =
                new SessionLauncher(
                        context,
                        (id, definition, panelDirectory, resourceName) -> {
                            final FakeTerminalRuntime terminalRuntime =
                                    new FakeTerminalRuntime(panelDirectory);
                            runtime.set(terminalRuntime);
                            final TerminalPanel createdPanel =
                                    GuiActionRunner.execute(
                                            () ->
                                                    new TerminalPanel(
                                                            terminalRuntime, ignored -> {}));
                            panel.set(createdPanel);
                            lastPanel.set(createdPanel);
                            return createdPanel;
                        });
        assertTrue(runtime.get() == null, "runtime should be created on launch");
        return new Fixture(
                state,
                project,
                sessionId,
                terminalId,
                new SessionCreationService.CreatedSession(
                        session, sessionId, terminalId, directory.toString()),
                context,
                launcher,
                runtime,
                panel);
    }

    private record Fixture(
            AppState state,
            Project project,
            com.jagent.desktop.models.SessionId sessionId,
            TerminalId terminalId,
            SessionCreationService.CreatedSession created,
            ActionContext context,
            SessionLauncher launcher,
            AtomicReference<FakeTerminalRuntime> runtimeRef,
            AtomicReference<TerminalPanel> panelRef) {

        private TerminalPanel panel() {
            return panelRef.get();
        }

        private FakeTerminalRuntime runtime() {
            return runtimeRef.get();
        }
    }

    private static final class FakeTerminalRuntime extends TerminalRuntime {
        private final AtomicInteger startCount = new AtomicInteger();
        private Consumer<TerminalState> listener = ignored -> {};
        private TerminalState state = TerminalState.STARTING;

        private FakeTerminalRuntime(final Path directory) {
            super("fake", directory, directory.resolve("fake-history").toString());
        }

        @Override
        public void start(final Consumer<TtyConnector> attach, final Consumer<Exception> failed) {
            startCount.incrementAndGet();
            state = TerminalState.WORKING;
            listener.accept(state);
        }

        @Override
        public void onStateChanged(final Consumer<TerminalState> stateListener) {
            listener = stateListener == null ? ignored -> {} : stateListener;
            listener.accept(state);
        }

        @Override
        public TerminalState state() {
            return state;
        }

        @Override
        public void stop() {
            state = TerminalState.STOPPED;
            listener.accept(state);
        }

        private int startCount() {
            return startCount.get();
        }
    }
}
