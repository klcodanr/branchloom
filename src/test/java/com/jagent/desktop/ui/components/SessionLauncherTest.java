package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundJobs;
import com.jagent.desktop.services.SessionCreationService;
import com.jagent.desktop.services.SessionLauncher;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.terminal.TerminalRuntime;
import com.jagent.desktop.services.terminal.TerminalState;
import com.jagent.desktop.test.AsyncTestSupport;
import com.jagent.desktop.test.TestAppState;
import com.jagent.desktop.ui.components.terminal.TerminalPanel;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionLauncherTest {
    private final AtomicReference<TerminalPanel> lastPanel = new AtomicReference<>();

    @BeforeAll
    static void configureTheme() {
        Theme.apply(Theme.FlatLafTheme.LIGHT);
    }

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

        assertEquals(ViewId.SESSION, fixture.context().currentViewId(), "session view should open");
        assertEquals(
                fixture.created().session().id(),
                fixture.context().currentSessionId(),
                "session should be selected");
        assertEquals(
                fixture.terminalId(),
                fixture.context().currentTerminalId(),
                "terminal should be selected");
        assertNotNull(
                TerminalPanel.existing(fixture.terminalId()), "terminal panel should be created");
        assertEquals(
                TerminalState.STARTING,
                TerminalPanel.state(fixture.terminalId()),
                "terminal should remain pending until the view mounts it");
        assertEquals(0, fixture.backgroundJobs().jobs().size(), "no setup job should be created");
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
                        Files.exists(directory.resolve("setup.log"))
                                && "firstsecond"
                                        .equals(readIfPresent(directory.resolve("setup.log"))),
                "startup commands should run");
        assertEquals(
                "firstsecond",
                Files.readString(directory.resolve("setup.log")),
                "startup commands should run in order");
        AsyncTestSupport.await(
                () ->
                        fixture.backgroundJobs().jobs().getFirst().status()
                                == BackgroundJobs.Status.SUCCEEDED,
                "startup job should complete successfully");
        assertNotNull(
                TerminalPanel.existing(fixture.terminalId()),
                "terminal panel should remain retained after setup commands");
    }

    @Test
    void stopsStartupCommandsAfterFailure(@TempDir final Path directory)
            throws IOException, InterruptedException {
        final Fixture fixture =
                fixture(directory, List.of("printf failed-output; exit 7", "touch skipped"));

        fixture.launcher().launch(fixture.project(), fixture.created());

        AsyncTestSupport.await(
                () -> !fixture.backgroundJobs().jobs().isEmpty(), "startup job should be created");
        AsyncTestSupport.await(
                () -> Files.notExists(directory.resolve("skipped")),
                "later commands should not run");
        AsyncTestSupport.await(
                () ->
                        fixture.backgroundJobs().jobs().getFirst().status()
                                == BackgroundJobs.Status.FAILED,
                "startup job should fail");
        assertFalse(Files.exists(directory.resolve("skipped")), "later commands should not run");
        assertNotNull(
                TerminalPanel.existing(fixture.terminalId()),
                "failed setup should retain the terminal panel");
    }

    @Test
    void reportsFallbackMessageForFailureWithoutOutput(@TempDir final Path directory)
            throws IOException, InterruptedException {
        final Fixture fixture = fixture(directory, List.of("exit 7"));

        fixture.launcher().launch(fixture.project(), fixture.created());

        AsyncTestSupport.await(
                () ->
                        fixture.backgroundJobs().jobs().getFirst().status()
                                == BackgroundJobs.Status.FAILED,
                "startup job should fail");
        assertNotNull(
                TerminalPanel.existing(fixture.terminalId()),
                "failed setup should retain the terminal panel");
    }

    private static String readIfPresent(final Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            return "";
        }
    }

    private Fixture fixture(final Path directory, final List<String> startupCommands)
            throws IOException {
        final AppState state = TestAppState.empty();
        final Project project =
                new Project(
                        com.jagent.desktop.models.ProjectId.create(),
                        "Demo",
                        directory.toString(),
                        null,
                        null,
                        null,
                        null,
                        startupCommands,
                        List.of(),
                        null,
                        null);
        final var projectId = state.addProject(project);
        final var session =
                Session.create(projectId, "Feature", "agent", "prompt", directory.toString());
        final var sessionId = state.addSession(projectId, session);
        final TerminalId terminalId =
                state.addTerminal(sessionId, Terminal.create(sessionId, "Shell", "true"));
        final ViewController controller = TestAppState.controller(state);
        final AtomicReference<FakeTerminalRuntime> runtime = new AtomicReference<>();
        final AtomicReference<TerminalPanel> panel = new AtomicReference<>();
        final BackgroundJobs backgroundJobs = TestAppState.backgroundJobs(state);
        final SessionLauncher launcher =
                new SessionLauncher(
                        controller, state, TestAppState.dialogService(), backgroundJobs);
        assertTrue(runtime.get() == null, "runtime should be created on launch");
        return new Fixture(
                state,
                project,
                sessionId,
                terminalId,
                new SessionCreationService.CreatedSession(
                        state.sessions().get(sessionId), terminalId),
                controller,
                launcher,
                runtime,
                panel,
                backgroundJobs);
    }

    private record Fixture(
            AppState state,
            Project project,
            com.jagent.desktop.models.SessionId sessionId,
            TerminalId terminalId,
            SessionCreationService.CreatedSession created,
            ViewController context,
            SessionLauncher launcher,
            AtomicReference<FakeTerminalRuntime> runtimeRef,
            AtomicReference<TerminalPanel> panelRef,
            BackgroundJobs backgroundJobs) {}

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
    }
}
