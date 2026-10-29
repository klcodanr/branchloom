package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.terminal.TerminalRuntime;
import com.jagent.desktop.services.terminal.TerminalState;
import com.jagent.desktop.test.TestAppState;
import com.jagent.desktop.ui.components.terminal.TerminalPanel;
import com.jagent.desktop.ui.components.terminal.TerminalPanelTestSupport;
import com.jediterm.terminal.TtyConnector;
import java.awt.IllegalComponentStateException;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class WorkspaceTabMenuTest {
    private static final String SUMMARY_TAB = "Summary";
    private static final String FIRST_TAB = "First";
    private static final String SECOND_TAB = "Second";

    @Test
    void closesOtherClosableTabsAndKeepsTheSelectedTab() {
        final JTabbedPane tabs = GuiActionRunner.execute(() -> new JTabbedPane());
        final WorkspaceTerminalTabs terminalTabs =
                GuiActionRunner.execute(
                        () ->
                                new WorkspaceTerminalTabs(
                                        tabs,
                                        (panel, id) -> {},
                                        (panel, title) -> {},
                                        new com.jagent.desktop.services.dialog
                                                .HeadlessDialogService()));
        final WorkspaceTabMenu menu =
                GuiActionRunner.execute(() -> new WorkspaceTabMenu(tabs, terminalTabs));
        GuiActionRunner.execute(
                () -> {
                    tabs.addTab(SUMMARY_TAB, new JPanel());
                    addClosableTab(tabs, FIRST_TAB);
                    addClosableTab(tabs, SECOND_TAB);
                    tabs.setSelectedIndex(2);
                    menu.closeOthers();
                });

        assertEquals(2, tabs.getTabCount(), "close others should preserve the default tab");
        assertEquals(
                SECOND_TAB,
                tabs.getTitleAt(tabs.getSelectedIndex()),
                "close others should preserve the selected tab");
    }

    @Test
    void closesAllClosableTabsAndKeepsDefaultTabs() {
        final JTabbedPane tabs = GuiActionRunner.execute(() -> new JTabbedPane());
        final WorkspaceTerminalTabs terminalTabs =
                GuiActionRunner.execute(
                        () ->
                                new WorkspaceTerminalTabs(
                                        tabs,
                                        (panel, id) -> {},
                                        (panel, title) -> {},
                                        new com.jagent.desktop.services.dialog
                                                .HeadlessDialogService()));
        final WorkspaceTabMenu menu =
                GuiActionRunner.execute(() -> new WorkspaceTabMenu(tabs, terminalTabs));
        GuiActionRunner.execute(
                () -> {
                    tabs.addTab(SUMMARY_TAB, new JPanel());
                    addClosableTab(tabs, FIRST_TAB);
                    addClosableTab(tabs, SECOND_TAB);
                    menu.closeAll();
                });

        assertEquals(1, tabs.getTabCount(), "close all should preserve the default tab");
        assertEquals(SUMMARY_TAB, tabs.getTitleAt(0), "default tab should remain open");
    }

    @Test
    void ignoresNonPopupMouseEventsAndShowsMenuForClosableTab() {
        final JTabbedPane tabs = GuiActionRunner.execute(() -> new JTabbedPane());
        final WorkspaceTerminalTabs terminalTabs = terminalTabs(tabs);
        GuiActionRunner.execute(() -> new WorkspaceTabMenu(tabs, terminalTabs));
        GuiActionRunner.execute(
                () -> {
                    addClosableTab(tabs, FIRST_TAB);
                    tabs.setSize(300, 100);
                    tabs.doLayout();
                });

        final MouseEvent normal = mouseEvent(tabs, false);
        final MouseEvent popup = mouseEvent(tabs, true);
        assertThrows(
                IllegalComponentStateException.class,
                () ->
                        GuiActionRunner.execute(
                                () -> {
                                    for (final var listener : tabs.getMouseListeners()) {
                                        listener.mousePressed(normal);
                                        listener.mousePressed(popup);
                                    }
                                }),
                "popup menu display should reach Swing's headless display boundary");
        assertEquals(0, tabs.getSelectedIndex(), "popup tab should be selected before display");
        assertEquals(1, tabs.getTabCount(), "popup handling should not close the tab");
    }

    @Test
    void buildsTerminalPopupPathAndClosesTerminalThroughCloseAll() {
        final JTabbedPane tabs = GuiActionRunner.execute(() -> new JTabbedPane());
        final CountingTerminalRuntime runtime = new CountingTerminalRuntime();
        final TerminalPanel panel =
                GuiActionRunner.execute(
                        () ->
                                TerminalPanelTestSupport.create(
                                        runtime, ignored -> {}, TestAppState.dialogService()));
        final WorkspaceTerminalTabs terminalTabs = terminalTabs(tabs);
        GuiActionRunner.execute(
                () -> terminalTabs.mount("Terminal", TerminalId.create(), panel, false));
        final WorkspaceTabMenu menu =
                GuiActionRunner.execute(() -> new WorkspaceTabMenu(tabs, terminalTabs));
        GuiActionRunner.execute(
                () -> {
                    tabs.setSize(300, 100);
                    tabs.doLayout();
                });

        assertThrows(
                IllegalComponentStateException.class,
                () ->
                        GuiActionRunner.execute(
                                () -> {
                                    final MouseEvent popup = mouseEvent(tabs, true);
                                    for (final var listener : tabs.getMouseListeners()) {
                                        listener.mouseReleased(popup);
                                    }
                                }),
                "terminal popup path should construct its menu before display");
        menu.closeAll();
        assertEquals(0, tabs.getTabCount(), "close all should close terminal tabs");
    }

    @Test
    void closeOthersAndCloseAllActionsOperateOnMixedTabs() {
        final JTabbedPane tabs = GuiActionRunner.execute(() -> new JTabbedPane());
        final WorkspaceTerminalTabs terminalTabs = terminalTabs(tabs);
        final WorkspaceTabMenu menu =
                GuiActionRunner.execute(() -> new WorkspaceTabMenu(tabs, terminalTabs));

        GuiActionRunner.execute(
                () -> {
                    tabs.addTab(SUMMARY_TAB, new JPanel());
                    addClosableTab(tabs, FIRST_TAB);
                    addClosableTab(tabs, SECOND_TAB);
                    tabs.setSelectedIndex(1);
                    menu.closeOthers();
                });
        assertEquals(2, tabs.getTabCount(), "close others should retain the selected closable tab");
        assertEquals(
                FIRST_TAB, tabs.getTitleAt(1), "selected tab should remain after close others");

        GuiActionRunner.execute(menu::closeAll);
        assertEquals(1, tabs.getTabCount(), "close all should leave the non-closable summary tab");
    }

    private static WorkspaceTerminalTabs terminalTabs(final JTabbedPane tabs) {
        return GuiActionRunner.execute(
                () ->
                        new WorkspaceTerminalTabs(
                                tabs,
                                (terminal, id) -> {},
                                (t, title) -> {},
                                new com.jagent.desktop.services.dialog.HeadlessDialogService()));
    }

    private static MouseEvent mouseEvent(final JTabbedPane tabs, final boolean popup) {
        return new MouseEvent(
                tabs,
                MouseEvent.MOUSE_PRESSED,
                1,
                popup ? MouseEvent.BUTTON3_DOWN_MASK : 0,
                10,
                10,
                1,
                popup,
                popup ? MouseEvent.BUTTON3 : MouseEvent.BUTTON1);
    }

    private static final class CountingTerminalRuntime extends TerminalRuntime {
        private CountingTerminalRuntime() {
            super("fake", Path.of(System.getProperty("java.io.tmpdir")), "fake-history");
        }

        @Override
        public void start(final Consumer<TtyConnector> attach, final Consumer<Exception> failed) {}

        @Override
        public void onStateChanged(final Consumer<TerminalState> listener) {}

        @Override
        public TerminalState state() {
            return TerminalState.STARTING;
        }

        @Override
        public void stop() {}
    }

    private static void addClosableTab(final JTabbedPane tabs, final String title) {
        final JPanel panel = new JPanel();
        tabs.addTab(title, panel);
        panel.putClientProperty("JTabbedPane.tabClosable", true);
    }
}
