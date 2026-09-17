package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.terminal.TerminalRuntime;
import com.jagent.desktop.services.terminal.TerminalState;
import com.jediterm.terminal.TtyConnector;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class WorkspaceTerminalTabsUiTest {
    private static final Path TEMP_DIRECTORY = Path.of(System.getProperty("java.io.tmpdir"));
    private static final String SUMMARY_TAB = "Summary";
    private static final String TERMINAL_TAB = "Terminal";
    private static final String LONG_TITLE =
            "12345678901234567890123456789012345678901234567890123456789012345";
    private static final String TRUNCATED_LONG_TITLE =
            "1234567890123456789012345678901234567890123456789012345678901...";

    @Test
    void mountsWithoutStartingUnselectedTerminal() {
        final CountingTerminalRuntime firstRuntime = new CountingTerminalRuntime();
        final CountingTerminalRuntime secondRuntime = new CountingTerminalRuntime();
        final TerminalPanel firstPanel =
                GuiActionRunner.execute(() -> new TerminalPanel(firstRuntime, ignored -> {}));
        final TerminalPanel secondPanel =
                GuiActionRunner.execute(() -> new TerminalPanel(secondRuntime, ignored -> {}));
        final JTabbedPane tabs = GuiActionRunner.execute(() -> new JTabbedPane());
        final WorkspaceTerminalTabs terminalTabs =
                GuiActionRunner.execute(
                        () -> new WorkspaceTerminalTabs(tabs, (terminal, id) -> {}, (t, n) -> {}));

        GuiActionRunner.execute(() -> tabs.addTab(SUMMARY_TAB, new JPanel()));
        GuiActionRunner.execute(
                () ->
                        terminalTabs.mount(
                                TERMINAL_TAB + " 1", TerminalId.create(), firstPanel, false));
        GuiActionRunner.execute(
                () ->
                        terminalTabs.mount(
                                TERMINAL_TAB + " 2", TerminalId.create(), secondPanel, true));

        assertEquals(0, firstRuntime.startCount(), "unselected tab should not auto-start");
        assertEquals(1, secondRuntime.startCount(), "selected tab should start once");

        GuiActionRunner.execute(firstPanel::dispose);
        GuiActionRunner.execute(secondPanel::dispose);
    }

    @Test
    void startsWhenTabBecomesSelectedAndOnlyStartsOnce() {
        final CountingTerminalRuntime runtime = new CountingTerminalRuntime();
        final TerminalPanel panel =
                GuiActionRunner.execute(() -> new TerminalPanel(runtime, ignored -> {}));
        final JTabbedPane tabs = GuiActionRunner.execute(() -> new JTabbedPane());
        final WorkspaceTerminalTabs terminalTabs =
                GuiActionRunner.execute(
                        () -> new WorkspaceTerminalTabs(tabs, (terminal, id) -> {}, (t, n) -> {}));

        GuiActionRunner.execute(() -> tabs.addTab(SUMMARY_TAB, new JPanel()));
        GuiActionRunner.execute(
                () -> terminalTabs.mount(TERMINAL_TAB, TerminalId.create(), panel, false));
        assertEquals(0, runtime.startCount(), "tab should not start before being selected");

        GuiActionRunner.execute(() -> tabs.setSelectedComponent(panel));
        GuiActionRunner.execute(() -> tabs.setSelectedIndex(0));
        GuiActionRunner.execute(() -> tabs.setSelectedComponent(panel));

        assertEquals(1, runtime.startCount(), "tab should only start once");

        GuiActionRunner.execute(panel::dispose);
    }

    @Test
    void updatesTabTitleAndNotifiesRenameCallbackWhenTerminalTitleChanges() {
        final CountingTerminalRuntime runtime = new CountingTerminalRuntime();
        final AtomicReference<String> renamedTitle = new AtomicReference<>();
        final TerminalPanel panel =
                GuiActionRunner.execute(() -> new TerminalPanel(runtime, ignored -> {}));
        final JTabbedPane tabs = GuiActionRunner.execute(() -> new JTabbedPane());
        final WorkspaceTerminalTabs terminalTabs =
                GuiActionRunner.execute(
                        () ->
                                new WorkspaceTerminalTabs(
                                        tabs,
                                        (terminal, id) -> {},
                                        (terminal, title) -> renamedTitle.set(title)));

        GuiActionRunner.execute(() -> tabs.addTab(SUMMARY_TAB, new JPanel()));
        GuiActionRunner.execute(
                () -> terminalTabs.mount(TERMINAL_TAB, TerminalId.create(), panel, false));
        GuiActionRunner.execute(() -> panel.applicationTitleChanged("Build Logs"));
        GuiActionRunner.execute(() -> {});

        assertEquals("Build Logs", tabs.getTitleAt(1), "terminal title should update tab title");
        assertEquals(
                "Build Logs",
                renamedTitle.get(),
                "title updates should flow through the rename callback");

        GuiActionRunner.execute(panel::dispose);
    }

    @Test
    void clearsTitleListenerWhenDetachingTabs() {
        final CountingTerminalRuntime runtime = new CountingTerminalRuntime();
        final AtomicReference<String> renamedTitle = new AtomicReference<>();
        final TerminalPanel panel =
                GuiActionRunner.execute(() -> new TerminalPanel(runtime, ignored -> {}));
        final JTabbedPane tabs = GuiActionRunner.execute(() -> new JTabbedPane());
        final WorkspaceTerminalTabs terminalTabs =
                GuiActionRunner.execute(
                        () ->
                                new WorkspaceTerminalTabs(
                                        tabs,
                                        (terminal, id) -> {},
                                        (terminal, title) -> renamedTitle.set(title)));

        GuiActionRunner.execute(() -> tabs.addTab(SUMMARY_TAB, new JPanel()));
        GuiActionRunner.execute(
                () -> terminalTabs.mount(TERMINAL_TAB, TerminalId.create(), panel, false));
        GuiActionRunner.execute(terminalTabs::detach);
        GuiActionRunner.execute(() -> panel.applicationTitleChanged("Detached"));
        GuiActionRunner.execute(() -> {});

        assertTrue(renamedTitle.get() == null, "detached tabs should not receive title updates");

        GuiActionRunner.execute(panel::dispose);
    }

    @Test
    void truncatesLongRenamedTerminalTitle() {
        final CountingTerminalRuntime runtime = new CountingTerminalRuntime();
        final AtomicReference<String> renamedTitle = new AtomicReference<>();
        final TerminalPanel panel =
                GuiActionRunner.execute(() -> new TerminalPanel(runtime, ignored -> {}));
        final JTabbedPane tabs = GuiActionRunner.execute(() -> new JTabbedPane());
        final WorkspaceTerminalTabs terminalTabs =
                GuiActionRunner.execute(
                        () ->
                                new WorkspaceTerminalTabs(
                                        tabs,
                                        (terminal, id) -> {},
                                        (terminal, title) -> renamedTitle.set(title)));

        GuiActionRunner.execute(() -> tabs.addTab(SUMMARY_TAB, new JPanel()));
        GuiActionRunner.execute(
                () -> terminalTabs.mount(TERMINAL_TAB, TerminalId.create(), panel, true));
        GuiActionRunner.execute(() -> panel.applicationTitleChanged(LONG_TITLE));
        GuiActionRunner.execute(() -> {});

        assertEquals(
                TRUNCATED_LONG_TITLE,
                tabs.getTitleAt(1),
                "long terminal title should be truncated for display");
        assertEquals(
                TRUNCATED_LONG_TITLE,
                renamedTitle.get(),
                "persisted terminal title should use truncated value");

        GuiActionRunner.execute(panel::dispose);
    }

    private static final class CountingTerminalRuntime extends TerminalRuntime {
        private Consumer<TerminalState> listener = ignored -> {};
        private TerminalState state = TerminalState.STARTING;
        private int startCount;

        private CountingTerminalRuntime() {
            super("fake", TEMP_DIRECTORY, TEMP_DIRECTORY.resolve("fake-history").toString());
        }

        @Override
        public void start(final Consumer<TtyConnector> attach, final Consumer<Exception> failed) {
            startCount++;
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
            return startCount;
        }
    }
}
