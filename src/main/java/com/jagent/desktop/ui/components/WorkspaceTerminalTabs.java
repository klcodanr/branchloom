package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.terminal.TerminalState;
import java.awt.Component;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;

/** Owns the shared terminal tab lifecycle for workspace views. */
public final class WorkspaceTerminalTabs {
    private final JTabbedPane tabs;
    private final Consumer<TerminalState> stateChanged;
    private final BiConsumer<TerminalPanel, TerminalId> closed;
    private final BiConsumer<TerminalPanel, String> renamed;
    private final Map<TerminalPanel, TerminalState> states = new IdentityHashMap<>();
    private final Map<TerminalPanel, TerminalId> ids = new IdentityHashMap<>();

    public WorkspaceTerminalTabs(
            final JTabbedPane tabs,
            final Consumer<TerminalState> stateChanged,
            final BiConsumer<TerminalPanel, TerminalId> closed,
            final BiConsumer<TerminalPanel, String> renamed) {
        this.tabs = tabs;
        this.stateChanged = stateChanged;
        this.closed = closed;
        this.renamed = renamed;
    }

    public Map<TerminalPanel, TerminalState> states() {
        return states;
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
        terminal.setStateChanged(
                state -> {
                    states.put(terminal, state);
                    stateChanged.accept(state);
                });
        states.put(terminal, TerminalState.STARTING);
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
        states.remove(terminal);
        ids.remove(terminal);
        terminal.dispose();
        closed.accept(terminal, terminalId);
    }
}
