package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.ui.actions.RemoveSessionAction;
import com.jagent.desktop.ui.components.SessionActions;
import com.jagent.desktop.ui.components.SessionSummary;
import com.jagent.desktop.ui.components.TerminalPanel;
import com.jagent.desktop.ui.components.UiFactory;
import com.jagent.desktop.ui.dialogs.MissingWorktreeRecovery;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

public final class SessionView extends AbstractWorkspaceView {

    private final transient Project project;
    private final transient Session session;
    private final JLabel gitStatus = new JLabel();
    private int terminalNumber;

    public SessionView(final ActionContext actionContext) {
        super(actionContext, ViewId.SESSION);
        final var state = actionContext.appState();
        final var sessionId = state.currentSessionId();
        this.project = state.projects().get(state.currentProjectId());
        final Session persistedSession = sessionId == null ? null : state.sessions().get(sessionId);
        this.session = persistedSession;
        validateSelection();
        restoreSession(actionContext, state);
    }

    private void validateSelection() {
        if (project == null || session == null) {
            throw new IllegalStateException("A project and session must be selected.");
        }
    }

    private void restoreSession(
            final ActionContext actionContext, final com.jagent.desktop.services.AppState state) {
        MissingWorktreeRecovery.check(actionContext, project, session);
        final boolean hasSelectedTab = viewCoordinator.hasSelectedTab(id());
        final int selectedTab = viewCoordinator.selectedTab(id());
        initializeWorkspace(session.name());
        restoreTerminals(state);
        restoreSelectedTabOrSummary(actionContext, hasSelectedTab, selectedTab);
        updateCurrentTerminal();
    }

    private void restoreTerminals(final com.jagent.desktop.services.AppState state) {
        terminalNumber = session.terminalIds().size();
        for (final TerminalId terminalId : session.terminalIds()) {
            final Terminal terminal = state.terminals().get(terminalId);
            if (terminal != null) {
                addTerminal(
                        terminalId, terminalDefinitionForRestore(session, terminalId, terminal));
            }
        }
    }

    private void restoreSelectedTabOrSummary(
            final ActionContext actionContext,
            final boolean hasSelectedTab,
            final int selectedTab) {
        if (!selectTerminal(actionContext.appState().currentTerminalId(), terminalIds)
                && !restoreSelectedTab(hasSelectedTab, selectedTab)) {
            openSummary();
        }
    }

    private void addSummary() {
        final JScrollPane summary =
                new JScrollPane(
                        new SessionSummary(
                                project,
                                session,
                                () -> new RemoveSessionAction(actionContext).execute()));
        summary.setBorder(null);
        summary.getVerticalScrollBar().setUnitIncrement(14);
        tabs.addTab("Summary", summary);
    }

    @Override
    protected Path workspacePath() {
        final String worktreePath = session.worktreePath();
        if (worktreePath != null && !worktreePath.isBlank()) {
            return Path.of(worktreePath);
        }
        return Path.of(project.path());
    }

    @Override
    protected void addTitleDetails(final JPanel titleArea) {
        titleArea.add(gitStatus);
    }

    @Override
    protected void addDefaultTabs() {
        addSummary();
    }

    @Override
    protected void showActions(final JButton button) {
        UiFactory.showPopupMenu(
                SessionActions.menu(actionContext, actionContext.appState().currentSessionId()),
                button,
                0,
                button.getHeight());
    }

    @Override
    protected void openTerminal(final Path path) {
        addTerminal("Terminal", PlatformCommands.userShell());
    }

    public void createTerminal() {
        addTerminal("Terminal", PlatformCommands.userShell());
    }

    public void openSummary() {
        tabs.setSelectedIndex(tabs.indexOfTab("Summary"));
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
        titleLabel().setText(updated.trim());
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
        TerminalPanel terminal = TerminalPanel.existing(terminalId);
        if (terminal == null) {
            terminal =
                    TerminalPanel.retained(
                            terminalId,
                            persistedTerminal,
                            worktree,
                            project.name()
                                    + " > "
                                    + session.name()
                                    + " > "
                                    + persistedTerminal.title());
        }
        if (terminal.getParent() != null) {
            terminal.getParent().remove(terminal);
        }
        terminalIds.put(terminal, terminalId);
        mountTerminal(persistedTerminal.title(), terminalId, terminal, true);
    }

    protected static Terminal terminalDefinitionForRestore(
            final Session session, final TerminalId terminalId, final Terminal persistedTerminal) {
        final boolean isAgentTerminal =
                session.agent() != null
                        && !session.terminalIds().isEmpty()
                        && terminalId.equals(session.terminalIds().getFirst());
        if (!isAgentTerminal) {
            return persistedTerminal;
        }
        return new Terminal(
                persistedTerminal.sessionId(),
                persistedTerminal.projectId(),
                persistedTerminal.title(),
                PlatformCommands.userShell());
    }

    @Override
    protected void terminalClosed() {
        updateCurrentTerminal();
    }
}
