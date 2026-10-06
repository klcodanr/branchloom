package com.jagent.desktop.ui.components;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;

/** Provides close actions for the tabs owned by one workspace. */
public final class WorkspaceTabMenu {
    private final JTabbedPane tabs;
    private final WorkspaceTerminalTabs terminalTabs;

    public WorkspaceTabMenu(final JTabbedPane tabs, final WorkspaceTerminalTabs terminalTabs) {
        this.tabs = tabs;
        this.terminalTabs = terminalTabs;
        tabs.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(final MouseEvent event) {
                        show(event);
                    }

                    @Override
                    public void mouseReleased(final MouseEvent event) {
                        show(event);
                    }
                });
    }

    public void closeAll() {
        closeExcept(-1);
    }

    public void closeOthers() {
        closeExcept(tabs.getSelectedIndex());
    }

    private void closeExcept(final int retainedIndex) {
        for (int index = tabs.getTabCount() - 1; index >= 0; index--) {
            if (index != retainedIndex && closable(index)) {
                close(index);
            }
        }
    }

    private void show(final MouseEvent event) {
        if (!event.isPopupTrigger()) {
            return;
        }
        final int index = tabs.indexAtLocation(event.getX(), event.getY());
        if (!closable(index)) {
            return;
        }
        tabs.setSelectedIndex(index);
        final JPopupMenu menu = new JPopupMenu();
        final JMenuItem close = new JMenuItem("Close");
        close.addActionListener(ignored -> close(index));
        menu.add(close);
        final JMenuItem closeOthers = new JMenuItem("Close Others");
        closeOthers.addActionListener(ignored -> closeOthers());
        menu.add(closeOthers);
        final JMenuItem closeAll = new JMenuItem("Close All");
        closeAll.addActionListener(ignored -> closeAll());
        menu.add(closeAll);
        if (tabs.getComponentAt(index) instanceof TerminalPanel) {
            menu.addSeparator();
            final JMenuItem rename = new JMenuItem("Rename terminal");
            rename.addActionListener(ignored -> terminalTabs.renameActive(tabs));
            menu.add(rename);
        }
        menu.show(tabs, event.getX(), event.getY());
    }

    private boolean closable(final int index) {
        return index >= 0
                && index < tabs.getTabCount()
                && Boolean.TRUE.equals(
                        ((JComponent) tabs.getComponentAt(index))
                                .getClientProperty("JTabbedPane.tabClosable"));
    }

    private void close(final int index) {
        if (!closable(index)) {
            return;
        }
        if (tabs.getComponentAt(index) instanceof TerminalPanel) {
            terminalTabs.close(index);
        } else {
            tabs.removeTabAt(index);
        }
    }
}
