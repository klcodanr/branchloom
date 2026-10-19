package com.jagent.desktop.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

class WorkspaceTabMenuTest {
    @Test
    void closesOtherClosableTabsAndKeepsTheSelectedTab() {
        final JTabbedPane tabs = GuiActionRunner.execute(() -> new JTabbedPane());
        final WorkspaceTerminalTabs terminalTabs =
                GuiActionRunner.execute(
                        () ->
                                new WorkspaceTerminalTabs(
                                        tabs, (panel, id) -> {}, (panel, title) -> {}));
        final WorkspaceTabMenu menu =
                GuiActionRunner.execute(() -> new WorkspaceTabMenu(tabs, terminalTabs));
        GuiActionRunner.execute(
                () -> {
                    tabs.addTab("Summary", new JPanel());
                    addClosableTab(tabs, "First");
                    addClosableTab(tabs, "Second");
                    tabs.setSelectedIndex(2);
                    menu.closeOthers();
                });

        assertEquals(2, tabs.getTabCount(), "close others should preserve the default tab");
        assertEquals(
                "Second",
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
                                        tabs, (panel, id) -> {}, (panel, title) -> {}));
        final WorkspaceTabMenu menu =
                GuiActionRunner.execute(() -> new WorkspaceTabMenu(tabs, terminalTabs));
        GuiActionRunner.execute(
                () -> {
                    tabs.addTab("Summary", new JPanel());
                    addClosableTab(tabs, "First");
                    addClosableTab(tabs, "Second");
                    menu.closeAll();
                });

        assertEquals(1, tabs.getTabCount(), "close all should preserve the default tab");
        assertEquals("Summary", tabs.getTitleAt(0), "default tab should remain open");
    }

    private static void addClosableTab(final JTabbedPane tabs, final String title) {
        final JPanel panel = new JPanel();
        tabs.addTab(title, panel);
        panel.putClientProperty("JTabbedPane.tabClosable", true);
    }
}
