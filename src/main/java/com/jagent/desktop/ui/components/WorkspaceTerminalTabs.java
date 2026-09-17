package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.TerminalId;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;

/** Owns the shared terminal tab lifecycle for workspace views. */
public final class WorkspaceTerminalTabs {
    private static final int MAX_TAB_TITLE_LENGTH = 64;
    private static final String ELLIPSIS = "...";
    private final JTabbedPane tabs;
    private final BiConsumer<TerminalPanel, TerminalId> closed;
    private final BiConsumer<TerminalPanel, String> renamed;
    private final Collection<TerminalPanel> titleSyncing =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<TerminalPanel, TerminalId> ids = new IdentityHashMap<>();
    private final Set<TerminalPanel> started = Collections.newSetFromMap(new IdentityHashMap<>());

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
        tabs.addChangeListener(ignored -> startSelected());
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
        installTitleSync(terminal);
        tabs.addTab(title, terminal);
        terminal.putClientProperty("JTabbedPane.tabClosable", true);
        terminal.putClientProperty(
                "JTabbedPane.tabCloseCallback", (java.util.function.IntConsumer) this::close);
        if (selected) {
            tabs.setSelectedComponent(terminal);
            start(terminal);
        }
    }

    public void detach() {
        for (final TerminalPanel terminal : ids.keySet()) {
            terminal.putClientProperty("JTabbedPane.tabCloseCallback", null);
            terminal.setTitleChanged(null);
        }
        ids.clear();
        started.clear();
        titleSyncing.clear();
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
        final String title = normalizeTitle(updated);
        if (title == null) {
            return;
        }
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
        started.remove(terminal);
        titleSyncing.remove(terminal);
        terminal.setTitleChanged(null);
        terminal.dispose();
        closed.accept(terminal, terminalId);
        ids.remove(terminal);
    }

    private void startSelected() {
        final int index = tabs.getSelectedIndex();
        if (index < 0 || !(tabs.getComponentAt(index) instanceof TerminalPanel terminal)) {
            return;
        }
        start(terminal);
    }

    private void start(final TerminalPanel terminal) {
        if (!started.add(terminal)) {
            return;
        }
        terminal.start();
    }

    private void installTitleSync(final TerminalPanel terminal) {
        if (!titleSyncing.add(terminal)) {
            return;
        }
        terminal.setTitleChanged(updatedTitle -> updateTerminalTitle(terminal, updatedTitle));
    }

    private void updateTerminalTitle(final TerminalPanel terminal, final String updatedTitle) {
        final String title = normalizeTitle(updatedTitle);
        if (title == null) {
            return;
        }
        for (int index = 0; index < tabs.getTabCount(); index++) {
            if (tabs.getComponentAt(index) instanceof TerminalPanel existing
                    && terminal.equals(existing)) {
                tabs.setTitleAt(index, title);
                renamed.accept(terminal, title);
                return;
            }
        }
    }

    private static String normalizeTitle(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() <= MAX_TAB_TITLE_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_TAB_TITLE_LENGTH - ELLIPSIS.length()) + ELLIPSIS;
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
