package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.terminal.TerminalRuntime;
import com.jagent.desktop.services.terminal.TerminalState;
import com.jediterm.terminal.TtyConnector;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class TerminalPanelUiTest {
    private static final String TRUE_COMMAND = "true";
    private static final Path TEMP_DIRECTORY = Path.of(System.getProperty("java.io.tmpdir"));

    @Test
    void constructsWithTerminalWidgetAndInitialState() {
        final var panel =
                GuiActionRunner.execute(
                        () -> new TerminalPanel(new FakeTerminalRuntime(), ignored -> {}));

        assertEquals(1, panel.getComponentCount(), "terminal panel should contain one widget");
        assertNotNull(panel.getComponent(0), "terminal widget should be created");
        assertTrue(
                panel.getBorder() instanceof javax.swing.border.EmptyBorder,
                "terminal panel should use an empty border for spacing");
        assertTrue(!panel.isOpaque(), "terminal spacing should be outside the terminal background");
        assertEquals(
                TerminalState.STARTING,
                panel.state(),
                "new terminal should start in STARTING state");
        panel.dispose();
    }

    @Test
    void reportsStartupFailureAndCanBeDisposed() throws InterruptedException {
        final var state = new AtomicReference<TerminalState>();
        final var panel =
                GuiActionRunner.execute(
                        () -> new TerminalPanel(new FakeTerminalRuntime(), state::set));

        GuiActionRunner.execute(panel::start);
        waitForState(panel, TerminalState.FAILED);

        assertEquals(TerminalState.FAILED, panel.state(), "invalid terminal directory should fail");
        assertEquals(TerminalState.FAILED, state.get(), "failure should notify the state listener");
        assertTrue(
                panel.getComponentCount() > 0, "failure should leave a visible status component");
        GuiActionRunner.execute(panel::dispose);
        assertEquals(TerminalState.STOPPED, panel.state(), "disposing should stop the runtime");
    }

    @Test
    void forwardsRuntimeStateChangesToListenerOnEdt() {
        final var state = new AtomicReference<TerminalState>();
        final var runtime = new FakeTerminalRuntime();
        final var panel = GuiActionRunner.execute(() -> new TerminalPanel(runtime, state::set));

        runtime.emit(TerminalState.WORKING);
        GuiActionRunner.execute(() -> {});

        assertEquals(
                TerminalState.WORKING,
                state.get(),
                "runtime state should reach the panel listener");
        panel.dispose();
    }

    @Test
    void retainedPanelCanBeReusedAndReconciled() {
        final TerminalId id = TerminalId.create();
        final Terminal definition = new Terminal(SessionId.create(), "Shell", TRUE_COMMAND);
        final TerminalPanel panel =
                GuiActionRunner.execute(
                        () -> TerminalPanel.retained(id, definition, TEMP_DIRECTORY, "Session"));

        assertEquals(panel, TerminalPanel.existing(id), "retained panel should be reusable");
        assertEquals(
                TerminalState.STARTING,
                TerminalPanel.state(id),
                "retained runtime should begin in starting state");
        panel.setResourceName("Renamed session");
        TerminalPanel.reconcile(Set.of(id));
        assertEquals(panel, TerminalPanel.existing(id), "reconcile should preserve active panels");

        TerminalPanel.reconcile(Set.of());
        assertTrue(TerminalPanel.existing(id) == null, "reconcile should remove stale panels");
    }

    @Test
    void supportsConvenienceConstructors() {
        final TerminalPanel commandOnly = new TerminalPanel(TRUE_COMMAND, TEMP_DIRECTORY);
        final TerminalPanel withListener =
                new TerminalPanel(TRUE_COMMAND, TEMP_DIRECTORY, ignored -> {});
        final TerminalPanel withResource =
                new TerminalPanel(TRUE_COMMAND, TEMP_DIRECTORY, "Session");
        final TerminalPanel withAllArguments =
                new TerminalPanel(TRUE_COMMAND, TEMP_DIRECTORY, "Session", ignored -> {});

        commandOnly.dispose();
        withListener.dispose();
        withResource.dispose();
        withAllArguments.dispose();

        assertEquals(1, commandOnly.getComponentCount(), "convenience panel should be initialized");
    }

    private static void waitForState(final TerminalPanel panel, final TerminalState expected)
            throws InterruptedException {
        final long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (panel.state() == expected) {
                GuiActionRunner.execute(() -> {});
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("terminal did not reach expected state: " + expected);
    }

    private static final class FakeTerminalRuntime extends TerminalRuntime {
        private Consumer<TerminalState> listener = ignored -> {};
        private TerminalState state = TerminalState.STARTING;

        private FakeTerminalRuntime() {
            super("fake", TEMP_DIRECTORY, TEMP_DIRECTORY.resolve("fake-history").toString());
        }

        @Override
        public void start(final Consumer<TtyConnector> attach, final Consumer<Exception> failed) {
            state = TerminalState.FAILED;
            listener.accept(state);
        }

        @Override
        public void onStateChanged(final Consumer<TerminalState> listener) {
            this.listener = listener == null ? ignored -> {} : listener;
            this.listener.accept(state);
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

        private void emit(final TerminalState nextState) {
            state = nextState;
            listener.accept(nextState);
        }
    }
}
