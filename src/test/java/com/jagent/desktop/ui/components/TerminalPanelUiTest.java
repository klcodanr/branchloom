package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.services.terminal.TerminalState;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class TerminalPanelUiTest {
    private static final Path TEMP_DIRECTORY = Path.of(System.getProperty("java.io.tmpdir"));

    @Test
    void constructsWithTerminalWidgetAndInitialState() {
        final var panel = GuiActionRunner.execute(() -> new TerminalPanel("true", TEMP_DIRECTORY));

        assertEquals(1, panel.getComponentCount(), "terminal panel should contain one widget");
        assertNotNull(panel.getComponent(0), "terminal widget should be created");
        assertTrue(panel.getBorder() instanceof javax.swing.border.EmptyBorder);
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
                        () -> new TerminalPanel("false", TEMP_DIRECTORY, state::set));

        GuiActionRunner.execute(panel::start);
        waitForState(panel, TerminalState.FAILED);

        assertEquals(TerminalState.FAILED, panel.state(), "invalid terminal directory should fail");
        assertEquals(TerminalState.FAILED, state.get(), "failure should notify the state listener");
        assertTrue(
                panel.getComponentCount() > 0, "failure should leave a visible status component");
        GuiActionRunner.execute(panel::dispose);
        assertEquals(TerminalState.STOPPED, panel.state(), "disposing should stop the runtime");
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
}
