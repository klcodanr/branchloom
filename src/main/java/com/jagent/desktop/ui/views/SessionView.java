package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.ui.actions.RemoveSessionAction;
import com.jagent.desktop.ui.components.EmptyStatePanel;
import com.jagent.desktop.ui.components.SessionActions;
import com.jagent.desktop.ui.components.SessionSummary;
import com.jagent.desktop.ui.components.UiPopupMenus;
import com.jagent.desktop.ui.components.terminal.TerminalPanel;
import com.jagent.desktop.ui.dialogs.MissingWorktreeRecovery;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SessionView extends AbstractWorkspaceView {
    private static final Logger LOG = LoggerFactory.getLogger(SessionView.class);

    private final transient SessionId sessionId;
    private final transient Project project;
    private final transient Session session;
    private SessionSummary summary;
    private final JLabel gitStatus = new JLabel();
    private int terminalNumber;

    public SessionView(final ActionContext actionContext) {
        super(actionContext, ViewId.SESSION);
        final var state = actionContext.appState();
        final var selectedSessionId = actionContext.viewController().currentSessionId();
        this.sessionId = selectedSessionId;
        this.project = state.projects().get(actionContext.viewController().currentProjectId());
        final Session persistedSession =
                selectedSessionId == null ? null : state.sessions().get(selectedSessionId);
        this.session = persistedSession;
        validateSelection();
        restoreSession(actionContext, state);
    }

    private void validateSelection() {
        if (project == null || session == null) {
            throw new IllegalStateException("A project and session must be selected.");
        }
        final String worktreePath = session.worktreePath();
        if (session.agent() != null && (worktreePath == null || worktreePath.isBlank())) {
            throw new IllegalStateException("An agent session must have a worktree path.");
        }
    }

    private void restoreSession(final ActionContext actionContext, final AppState state) {
        MissingWorktreeRecovery.check(actionContext, project, session);
        final boolean hasSelectedTab = viewController.hasSelectedSessionTab(sessionId);
        final int selectedTab = viewController.selectedSessionTab(sessionId);
        initializeWorkspace(session.name());
        restoreTerminals(state);
        restoreSelectedTabOrSummary(actionContext, hasSelectedTab, selectedTab);
        updateCurrentTerminal();
    }

    private void restoreTerminals(final AppState state) {
        terminalNumber = session.terminalIds().size();
        for (final TerminalId terminalId : session.terminalIds()) {
            final Terminal terminal = state.terminals().get(terminalId);
            if (terminal != null) {
                try {
                    addTerminal(
                            terminalId,
                            terminalDefinitionForRestore(session, terminalId, terminal),
                            false);
                } catch (RuntimeException exception) {
                    LOG.error("Failed to restore terminal tab {}", terminalId, exception);
                    tabs.addTab(
                            terminal.title(),
                            new EmptyStatePanel(
                                    "Could not load terminal",
                                    exception.getClass().getSimpleName()
                                            + ": "
                                            + exception.getMessage()));
                }
            }
        }
    }

    private void restoreSelectedTabOrSummary(
            final ActionContext actionContext,
            final boolean hasSelectedTab,
            final int selectedTab) {
        if (!selectTerminal(actionContext.viewController().currentTerminalId(), terminalIds)
                && !restoreSelectedTab(hasSelectedTab, selectedTab)) {
            openSummary();
        }
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
        try {
            summary =
                    new SessionSummary(
                            sessionId,
                            actionContext.appState(),
                            viewController,
                            () -> new RemoveSessionAction(actionContext).execute(),
                            viewController.getAsyncLoadingState(ViewId.SESSION));
            final JScrollPane summaryScroll = new JScrollPane(summary);
            summaryScroll.setBorder(null);
            summaryScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            summaryScroll.getVerticalScrollBar().setUnitIncrement(14);
            tabs.addTab("Summary", summaryScroll);
        } catch (RuntimeException exception) {
            LOG.error("Failed to create session summary for {}", sessionId, exception);
            tabs.addTab(
                    "Summary",
                    new EmptyStatePanel(
                            "Could not load session summary",
                            exception.getClass().getSimpleName() + ": " + exception.getMessage()));
        }
    }

    @Override
    protected void showActions(final JButton button) {
        UiPopupMenus.show(
                SessionActions.menu(
                        actionContext, actionContext.viewController().currentSessionId()),
                button,
                0,
                button.getHeight());
    }

    @Override
    protected void openTerminal() {
        final String tabTitle = "Terminal " + (++terminalNumber);
        final TerminalId terminalId =
                actionContext
                        .appState()
                        .addTerminal(
                                actionContext.viewController().currentSessionId(),
                                new Terminal(
                                        actionContext.viewController().currentSessionId(),
                                        tabTitle,
                                        PlatformCommands.userShell()));
        addTerminal(terminalId, actionContext.appState().terminals().get(terminalId), true);
    }

    public void openSummary() {
        tabs.setSelectedIndex(tabs.indexOfTab("Summary"));
    }

    @Override
    protected void updateSelectedTab(final int selectedIndex) {
        viewController.updateSelectedSessionTab(sessionId, selectedIndex);
        super.updateSelectedTab(selectedIndex);
    }

    @Override
    public CompletableFuture<?> refresh() {
        if (summary != null) {
            return summary.refresh();
        }
        return CompletableFuture.completedFuture(null);
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
                        actionContext.viewController().currentSessionId(),
                        new Session(
                                session.projectId(),
                                updated.trim(),
                                session.agent(),
                                session.prompt(),
                                session.worktreePath(),
                                session.created(),
                                session.terminalIds()));
    }

    private void addTerminal(
            final TerminalId terminalId, final Terminal persistedTerminal, final boolean selected) {
        if (terminalId == null
                || persistedTerminal == null
                || !Files.isDirectory(workspacePath())) {
            return;
        }
        final Path worktree = workspacePath().toAbsolutePath().normalize();
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
        mountTerminal(persistedTerminal.title(), terminalId, terminal, selected);
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
}
