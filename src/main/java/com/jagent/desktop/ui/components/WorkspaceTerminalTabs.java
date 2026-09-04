package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.TerminalId;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;

/** Owns the shared terminal tab lifecycle for workspace views. */
public final class WorkspaceTerminalTabs {
    private final JTabbedPane tabs;
    private final BiConsumer<TerminalPanel, TerminalId> closed;
    private final BiConsumer<TerminalPanel, String> renamed;
    private final Map<TerminalPanel, TerminalId> ids = new IdentityHashMap<>();

    public WorkspaceTerminalTabs(
            final JTabbedPane tabs,
            final BiConsumer<TerminalPanel, TerminalId> closed,
            final BiConsumer<TerminalPanel, String> renamed) {
        this.tabs = tabs;
        this.closed = closed;
        this.renamed = renamed;
        tabs.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(final MouseEvent event) {
                        showContextMenu(event);
                    }

                    @Override
                    public void mouseReleased(final MouseEvent event) {
                        showContextMenu(event);
                    }
                });
    }

    public Map<TerminalPanel, TerminalId> ids() {
        return ids;
    }

    public void mount(
            final String title,
            final TerminalId terminalId,
            final TerminalPanel terminal,
            final boolean selected) {
        if (terminal.getParent() != null) {
            terminal.getParent().remove(terminal);
        }
        ids.put(terminal, terminalId);
        tabs.addTab(title, terminal);
        terminal.putClientProperty("JTabbedPane.tabClosable", true);
        terminal.putClientProperty(
                "JTabbedPane.tabCloseCallback", (java.util.function.IntConsumer) this::close);
        if (selected) {
            tabs.setSelectedComponent(terminal);
        }
        terminal.start();
    }

    public void detach() {
        for (final TerminalPanel terminal : ids.keySet()) {
            terminal.putClientProperty("JTabbedPane.tabCloseCallback", null);
        }
        ids.clear();
    }

    public void closeActive() {
        close(tabs.getSelectedIndex());
    }

    public void renameActive(final Component parent) {
        final int index = tabs.getSelectedIndex();
        if (index < 0 || !(tabs.getComponentAt(index) instanceof TerminalPanel terminal)) {
            return;
        }
        final String updated =
                (String)
                        JOptionPane.showInputDialog(
                                parent,
                                "Terminal tab name:",
                                "Rename terminal tab",
                                JOptionPane.PLAIN_MESSAGE,
                                null,
                                null,
                                tabs.getTitleAt(index));
        if (updated == null || updated.isBlank()) {
            return;
        }
        final String title = updated.trim();
        tabs.setTitleAt(index, title);
        renamed.accept(terminal, title);
    }

    public void select(final int index) {
        if (index < 1) {
            return;
        }
        int terminalIndex = 0;
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (!(tabs.getComponentAt(i) instanceof TerminalPanel)) {
                continue;
            }
            terminalIndex++;
            if (terminalIndex == index) {
                tabs.setSelectedIndex(i);
                return;
            }
        }
    }

    private void close(final int index) {
        if (index < 0
                || index >= tabs.getTabCount()
                || !(tabs.getComponentAt(index) instanceof TerminalPanel terminal)) {
            return;
        }
        final TerminalId terminalId = ids.get(terminal);
        tabs.removeTabAt(index);
        terminal.dispose();
        closed.accept(terminal, terminalId);
        ids.remove(terminal);
    }

    private void showContextMenu(final MouseEvent event) {
        if (!event.isPopupTrigger()) {
            return;
        }
        final int index = tabs.indexAtLocation(event.getX(), event.getY());
        if (index < 0 || !(tabs.getComponentAt(index) instanceof TerminalPanel)) {
            return;
        }
        tabs.setSelectedIndex(index);
        final JPopupMenu menu = new JPopupMenu();
        final JMenuItem rename = new JMenuItem("Rename terminal");
        rename.addActionListener(ignored -> renameActive(tabs));
        menu.add(rename);
        menu.show(tabs, event.getX(), event.getY());
    }
}
