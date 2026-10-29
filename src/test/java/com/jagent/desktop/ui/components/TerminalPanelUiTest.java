package com.jagent.desktop.ui.components.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.terminal.TerminalRuntime;
import com.jagent.desktop.services.terminal.TerminalState;
import com.jagent.desktop.test.SwingTestSupport;
import com.jagent.desktop.ui.components.Theme;
import com.jediterm.terminal.TtyConnector;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JScrollBar;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TerminalPanelUiTest {
    private static final String TRUE_COMMAND = "true";
    private static final Path TEMP_DIRECTORY = Path.of(System.getProperty("java.io.tmpdir"));

    @BeforeAll
    static void configureTheme() {
        Theme.apply(Theme.FlatLafTheme.LIGHT);
    }

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
                0,
                panel.getInsets().left,
                "terminal should align with the tab content on the left");
        assertEquals(
                0,
                panel.getInsets().right,
                "terminal should align with the tab content on the right");
        assertTrue(
                findScrollBar(panel).getPreferredSize().width == 0,
                "terminal should not reserve space for an unused scrollbar");
        assertEquals(
                TerminalState.STARTING,
                panel.state(),
                "new terminal should start in STARTING state");
        panel.dispose();
    }

    private static JScrollBar findScrollBar(final java.awt.Container container) {
        for (final java.awt.Component component : container.getComponents()) {
            if (component instanceof JScrollBar scrollBar) {
                return scrollBar;
            }
            if (component instanceof java.awt.Container child) {
                final JScrollBar scrollBar = findScrollBar(child);
                if (scrollBar != null) {
                    return scrollBar;
                }
            }
        }
        return null;
    }

    private static boolean containsLabel(final java.awt.Container container, final String text) {
        for (final java.awt.Component component : container.getComponents()) {
            if (component instanceof JLabel label && text.equals(label.getText())) {
                return true;
            }
            if (component instanceof java.awt.Container child && containsLabel(child, text)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void reportsStartupFailureAndCanBeDisposed() throws InterruptedException {
        final var state = new AtomicReference<TerminalState>();
        final var panel =
                GuiActionRunner.execute(
                        () -> new TerminalPanel(new FakeTerminalRuntime(), state::set));

        GuiActionRunner.execute(panel::start);
        waitForState(panel, TerminalState.FAILED);
        SwingTestSupport.await(
                () -> containsLabel(panel, "Could not start terminal: startup failed"),
                "terminal failure message did not render");

        assertEquals(TerminalState.FAILED, panel.state(), "invalid terminal directory should fail");
        assertEquals(TerminalState.FAILED, state.get(), "failure should notify the state listener");
        assertTrue(
                containsLabel(panel, "Could not start terminal: startup failed"),
                "failure should replace the terminal with a visible status message");
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
        TerminalPanel.pruneRetainedPanels(Set.of(id));
        assertEquals(panel, TerminalPanel.existing(id), "reconcile should preserve active panels");

        TerminalPanel.pruneRetainedPanels(Set.of());
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

    @Test
    void forwardsApplicationTitleChangesToListener() {
        final var title = new AtomicReference<String>();
        final TerminalPanel panel =
                GuiActionRunner.execute(
                        () -> new TerminalPanel(new FakeTerminalRuntime(), ignored -> {}));

        GuiActionRunner.execute(() -> panel.setTitleChanged(title::set));
        GuiActionRunner.execute(() -> panel.applicationTitleChanged("  Build Output  "));
        GuiActionRunner.execute(() -> {});

        assertEquals("Build Output", title.get(), "title callback should receive trimmed title");
        panel.dispose();
    }

    @Test
    void ignoresBlankOrNullApplicationTitleChanges() {
        final var title = new AtomicReference<String>("Original");
        final TerminalPanel panel =
                GuiActionRunner.execute(
                        () -> new TerminalPanel(new FakeTerminalRuntime(), ignored -> {}));

        GuiActionRunner.execute(() -> panel.setTitleChanged(title::set));
        GuiActionRunner.execute(() -> panel.applicationTitleChanged("   "));
        GuiActionRunner.execute(() -> panel.applicationTitleChanged(null));
        GuiActionRunner.execute(() -> {});

        assertEquals("Original", title.get(), "blank or null titles should be ignored");
        panel.dispose();
    }

    @Test
    void terminalSettingsUseThemeDefaultsForColorsAndStyles() {
        final AppTerminalSettings settings = new AppTerminalSettings();

        final var defaultStyle = settings.getDefaultStyle();
        assertEquals(
                settings.getDefaultForeground(),
                defaultStyle.getForeground(),
                "default style should use configured foreground");
        assertEquals(
                settings.getDefaultBackground(),
                defaultStyle.getBackground(),
                "default style should use configured background");

        final var selectionStyle = settings.getSelectionColor();
        assertTrue(
                selectionStyle.getForeground() != null,
                "selection color should define a foreground");
        assertTrue(
                selectionStyle.getBackground() != null,
                "selection color should define a background");
        assertTrue(
                !settings.useInverseSelectionColor(),
                "terminal selection should use configured colors instead of inversion");

        final var foundPatternStyle = settings.getFoundPatternColor();
        assertTrue(
                foundPatternStyle.getForeground() != null,
                "find highlight should define a foreground");
        assertTrue(
                foundPatternStyle.getBackground() != null,
                "find highlight should define a background");

        assertEquals(
                com.jediterm.terminal.HyperlinkStyle.HighlightMode.ALWAYS,
                settings.getHyperlinkHighlightingMode(),
                "hyperlinks should always be visibly styled");
        assertEquals(
                650, settings.caretBlinkingMs(), "cursor blinking should use the tuned period");
    }

    @Test
    void terminalSettingsUseJediTermAnsiPalette() {
        final AppTerminalSettings settings = new AppTerminalSettings();
        final var palette = settings.getTerminalColorPalette();

        for (int index = 0; index <= 15; index++) {
            final var color =
                    com.jediterm.terminal.emulator.ColorPalette.getIndexedTerminalColor(index);
            assertNotNull(color, "indexed terminal color should resolve: " + index);
            assertEquals(
                    com.jediterm.terminal.emulator.ColorPaletteImpl.XTERM_PALETTE.getForeground(
                            color),
                    palette.getForeground(color),
                    "foreground should use Jediterm ANSI palette: " + index);
            assertEquals(
                    com.jediterm.terminal.emulator.ColorPaletteImpl.XTERM_PALETTE.getBackground(
                            color),
                    palette.getBackground(color),
                    "background should use Jediterm ANSI palette: " + index);
        }
    }

    private static void waitForState(final TerminalPanel panel, final TerminalState expected)
            throws InterruptedException {
        SwingTestSupport.await(
                () -> panel.state() == expected,
                "terminal did not reach expected state: " + expected);
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
            failed.accept(new IllegalStateException("startup failed"));
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
