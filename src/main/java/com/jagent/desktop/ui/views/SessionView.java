package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.terminal.TerminalState;
import com.jagent.desktop.ui.components.SessionActions;
import com.jagent.desktop.ui.components.SessionSummary;
import com.jagent.desktop.ui.components.TabBody;
import com.jagent.desktop.ui.components.TerminalPanel;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiFactory;
import com.jagent.desktop.ui.components.UiIcons;
import com.jagent.desktop.ui.components.WorkspaceTreePanel;
import com.jagent.desktop.ui.dialogs.MissingWorktreeRecovery;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;

public final class SessionView extends JPanel implements View {

    private final transient ActionContext actionContext;
    private final transient Project project;
    private final transient Session session;
    private final JTabbedPane terminals = new JTabbedPane();
    private final Map<TerminalPanel, TerminalState> terminalStates = new IdentityHashMap<>();
    private final transient Map<TerminalPanel, TerminalId> terminalIds = new IdentityHashMap<>();
    private final JLabel sessionTitle = UiFactory.label("", Theme.FontSize.XXL);
    private final JLabel sessionStatus = UiFactory.label("Stopped", Theme.FontSize.SM);
    private final JLabel gitStatus = UiFactory.label("", Theme.FontSize.SM);
    private int terminalNumber;

    public SessionView(final ActionContext actionContext) {
        super();
        final var state = actionContext.appState();
        this.actionContext = actionContext;
        this.project = state.projects().get(state.currentProjectId());
        this.session = state.sessions().get(state.currentSessionId());
        if (project == null || session == null) {
            throw new IllegalStateException("A project and session must be selected.");
        }
        MissingWorktreeRecovery.check(actionContext, project, session);
        setLayout(new BorderLayout(0, 16));
        add(topbar(actionContext), BorderLayout.NORTH);
        terminals.putClientProperty("JTabbedPane.scrollButtonsPolicy", "asNeeded");
        terminals.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        add(terminals, BorderLayout.CENTER);
        terminals.addTab("Files", TabBody.wrap(workspace()));
        addSummary();
        terminalNumber = session.terminalIds().size();
        for (final TerminalId terminalId : session.terminalIds()) {
            final Terminal terminal = state.terminals().get(terminalId);
            if (terminal != null) {
                addTerminal(terminalId, terminal);
            }
        }
        terminals.addChangeListener(event -> updateCurrentTerminal());
        if (!selectCurrentTerminal()) {
            openSummary();
        }
    }

    @Override
    public ViewId id() {
        return ViewId.SESSION;
    }

    @Override
    public String title() {
        return session.name();
    }

    @Override
    public JPanel render() {
        selectCurrentTerminal();
        return this;
    }

    private void addSummary() {
        final JScrollPane summary = new JScrollPane(new SessionSummary(project, session));
        summary.setBorder(null);
        summary.getVerticalScrollBar().setUnitIncrement(14);
        terminals.addTab("Summary", summary);
    }

    private JPanel workspace() {
        final JPanel workspace = new JPanel(new BorderLayout());
        workspace.setOpaque(false);
        workspace.add(
                new WorkspaceTreePanel(
                        actionContext,
                        sessionWorkspace(),
                        path -> addTerminal("Terminal", PlatformCommands.userShell())),
                BorderLayout.CENTER);
        return workspace;
    }

    private Path sessionWorkspace() {
        final String worktreePath = session.worktreePath();
        if (worktreePath != null && !worktreePath.isBlank()) {
            return Path.of(worktreePath);
        }
        return Path.of(project.path());
    }

    public void createTerminal() {
        addTerminal("Terminal", PlatformCommands.userShell());
    }

    public void selectTerminal(final int index) {
        if (index < 1) {
            return;
        }
        int terminalIndex = 0;
        for (int i = 1; i < terminals.getTabCount(); i++) {
            if (!(terminals.getComponentAt(i) instanceof TerminalPanel)) {
                continue;
            }
            terminalIndex++;
            if (terminalIndex == index) {
                terminals.setSelectedIndex(i);
                return;
            }
        }
    }

    public void openSummary() {
        terminals.setSelectedIndex(terminals.indexOfTab("Summary"));
    }

    public void closeActiveTerminal() {
        final int index = terminals.getSelectedIndex();
        if (index <= 0 || !(terminals.getComponentAt(index) instanceof TerminalPanel terminal)) {
            return;
        }
        terminals.removeTabAt(index);
        final TerminalId terminalId = terminalIds.remove(terminal);
        if (terminalId != null) {
            actionContext.appState().removeTerminal(terminalId);
        }
        terminalStates.remove(terminal);
        terminal.dispose();
        updateStatus();
        updateCurrentTerminal();
    }

    public void renameActiveTerminal() {
        final int index = terminals.getSelectedIndex();
        if (index <= 0 || !(terminals.getComponentAt(index) instanceof TerminalPanel)) {
            return;
        }
        final String updated =
                (String)
                        JOptionPane.showInputDialog(
                                this,
                                "Terminal tab name:",
                                "Rename terminal tab",
                                JOptionPane.PLAIN_MESSAGE,
                                null,
                                null,
                                terminals.getTitleAt(index));
        if (updated != null && !updated.isBlank()) {
            final String title = updated.trim();
            terminals.setTitleAt(index, title);
            if (!(terminals.getComponentAt(index) instanceof TerminalPanel terminal)) {
                return;
            }
            final TerminalId terminalId = terminalIds.get(terminal);
            if (terminalId == null) {
                return;
            }
            final Terminal current = actionContext.appState().terminals().get(terminalId);
            if (current == null) {
                return;
            }
            actionContext
                    .appState()
                    .updateTerminal(
                            terminalId,
                            new Terminal(current.sessionId(), title, current.command()));
        }
    }

    public void renameSession() {
        final String updated =
                (String)
                        JOptionPane.showInputDialog(
                                this,
                                "Session name:",
                                "Rename session",
                                JOptionPane.PLAIN_MESSAGE,
                                null,
                                null,
                                session.name());
        if (updated == null || updated.isBlank()) {
            return;
        }
        sessionTitle.setText(updated.trim());
        actionContext
                .appState()
                .updateSession(
                        actionContext.appState().currentSessionId(),
                        new Session(
                                session.projectId(),
                                updated.trim(),
                                session.agent(),
                                session.prompt(),
                                session.worktreePath(),
                                session.created(),
                                session.terminalIds()));
    }

    public void dispose() {
        for (final Component component : terminals.getComponents()) {
            if (component instanceof TerminalPanel terminal) {
                terminal.dispose();
            }
        }
    }

    private void addTerminal(final String title, final String command) {
        final String tabTitle = title + " " + (++terminalNumber);
        final TerminalId terminalId =
                actionContext
                        .appState()
                        .addTerminal(
                                actionContext.appState().currentSessionId(),
                                new Terminal(
                                        actionContext.appState().currentSessionId(),
                                        tabTitle,
                                        command));
        addTerminal(terminalId, actionContext.appState().terminals().get(terminalId));
    }

    private void addTerminal(final TerminalId terminalId, final Terminal persistedTerminal) {
        if (terminalId == null
                || persistedTerminal == null
                || session.worktreePath() == null
                || !Files.isDirectory(Path.of(session.worktreePath()))) {
            return;
        }
        final Path worktree = Path.of(session.worktreePath()).toAbsolutePath().normalize();
        final TerminalPanel terminal =
                TerminalPanel.retained(
                        terminalId,
                        persistedTerminal,
                        worktree,
                        project.name()
                                + " > "
                                + session.name()
                                + " > "
                                + persistedTerminal.title());
        if (terminal.getParent() != null) {
            terminal.getParent().remove(terminal);
        }
        terminal.setStateChanged(
                state -> {
                    terminalStates.put(terminal, state);
                    updateStatus();
                });
        terminalIds.put(terminal, terminalId);
        terminalStates.put(terminal, TerminalState.STARTING);
        terminals.addTab(persistedTerminal.title(), terminal);
        terminal.putClientProperty("JTabbedPane.tabClosable", true);
        terminal.putClientProperty(
                "JTabbedPane.tabCloseCallback",
                (java.util.function.IntConsumer)
                        index -> {
                            if (index < 0
                                    || index >= terminals.getTabCount()
                                    || !(terminals.getComponentAt(index)
                                            instanceof TerminalPanel closed)) {
                                return;
                            }
                            terminals.removeTabAt(index);
                            final TerminalId closedId = terminalIds.remove(closed);
                            terminalStates.remove(closed);
                            if (closedId != null) {
                                actionContext.appState().removeTerminal(closedId);
                            }
                            closed.dispose();
                            updateCurrentTerminal();
                        });
        terminals.setSelectedComponent(terminal);
        terminal.start();
    }

    private boolean selectCurrentTerminal() {
        final TerminalId currentTerminal = actionContext.appState().currentTerminalId();
        if (currentTerminal == null) {
            return false;
        }
        for (final Map.Entry<TerminalPanel, TerminalId> entry : terminalIds.entrySet()) {
            if (entry.getValue().equals(currentTerminal)) {
                terminals.setSelectedComponent(entry.getKey());
                return true;
            }
        }
        return false;
    }

    private void updateCurrentTerminal() {
        final int selectedIndex = terminals.getSelectedIndex();
        TerminalId currentTerminal = null;
        if (selectedIndex > 0
                && terminals.getComponentAt(selectedIndex) instanceof TerminalPanel terminal) {
            currentTerminal = terminalIds.get(terminal);
        }
        actionContext.appState().updateCurrentTerminal(currentTerminal);
    }

    private JPanel topbar(final ActionContext actionContext) {
        final JPanel bar = new JPanel(new BorderLayout(12, 0));
        final JPanel title = new JPanel();
        title.setOpaque(false);
        title.setLayout(new BoxLayout(title, BoxLayout.Y_AXIS));
        title.setMinimumSize(new Dimension(0, title.getMinimumSize().height));
        sessionTitle.setText(session.name());
        title.add(sessionTitle);
        title.add(sessionStatus);
        title.add(gitStatus);
        bar.add(title, BorderLayout.CENTER);
        final JButton menuButton = UiFactory.iconButton(UiIcons.ellipsis());
        menuButton.setToolTipText("Session actions");
        menuButton.getAccessibleContext().setAccessibleName("Session actions");
        menuButton.addActionListener(event -> showActions(menuButton, actionContext));
        bar.add(menuButton, BorderLayout.EAST);
        return bar;
    }

    private void updateStatus() {
        final TerminalState status =
                terminalStates.values().stream()
                        .filter(state -> state == TerminalState.FAILED)
                        .findFirst()
                        .orElse(
                                terminalStates.isEmpty()
                                        ? TerminalState.STOPPED
                                        : TerminalState.IDLE);
        sessionStatus.setText(status.label());
    }

    public void showActions(final JButton button, final ActionContext actionContext) {
        SessionActions.menu(actionContext, actionContext.appState().currentSessionId())
                .show(button, 0, button.getHeight());
    }
}
