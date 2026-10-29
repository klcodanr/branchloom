package com.jagent.desktop.ui.views;

import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.async.AsyncLoadingStateFactory;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.models.SessionId;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundJobs;
import com.jagent.desktop.services.MissingWorktreeRecovery;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.services.github.GitHubProvider;
import com.jagent.desktop.ui.actions.RemoveSessionAction;
import com.jagent.desktop.ui.components.EmptyStatePanel;
import com.jagent.desktop.ui.components.SessionActions;
import com.jagent.desktop.ui.components.SessionSummary;
import com.jagent.desktop.ui.components.UiPopupMenus;
import com.jagent.desktop.ui.components.terminal.TerminalPanel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SessionView extends AbstractWorkspaceView {
    private static final Logger LOG = LoggerFactory.getLogger(SessionView.class);

    private final transient AppState appState;
    private SessionSummary summary;
    private final JLabel gitStatus = new JLabel();
    private int terminalNumber;
    private final transient BackgroundJobs backgroundJobs;
    private final transient MissingWorktreeRecovery missingWorktreeRecovery;
    private final transient AsyncLoadingStateFactory asyncLoadingStateFactory;
    private final transient Session session;
    private final transient SessionActions sessionActions;

    @AssistedInject
    public SessionView(
            final BackgroundJobs backgroundJobs,
            final AsyncLoadingStateFactory asyncLoadingStateFactory,
            final ViewController viewController,
            final AppState appState,
            final GitHubProvider githubProvider,
            final DialogService dialogService,
            final SessionActions sessionActions,
            final @Assisted Session session) {
        super(viewController, appState, githubProvider, dialogService, ViewId.SESSION);
        this.backgroundJobs = backgroundJobs;
        this.asyncLoadingStateFactory = asyncLoadingStateFactory;
        this.appState = appState;
        this.sessionActions = sessionActions;
        this.session = session;
        validateSelection();
        this.missingWorktreeRecovery =
                new MissingWorktreeRecovery(viewController, appState, dialogService);
        restoreSession();
    }

    @FunctionalInterface
    public interface Factory {
        SessionView create(Session session);
    }

    private void validateSelection() {
        if (session == null) {
            throw new IllegalStateException("A session must be selected.");
        }
        final String worktreePath = session.worktreePath();
        if (worktreePath == null || worktreePath.isBlank()) {
            throw new IllegalStateException("An agent session must have a worktree path.");
        }
    }

    private void restoreSession() {
        final SessionId sessionId = session.id();
        this.missingWorktreeRecovery.check(session);
        final boolean hasSelectedTab = viewController.hasSelectedSessionTab(sessionId);
        final int selectedTab = viewController.selectedSessionTab(sessionId);
        initializeWorkspace(session.name());
        restoreTerminals();
        restoreSelectedTabOrSummary(viewController, hasSelectedTab, selectedTab);
        updateCurrentTerminal();
    }

    private void restoreTerminals() {
        terminalNumber = session.terminalIds().size();
        for (final TerminalId terminalId : session.terminalIds()) {
            final Terminal terminal = appState.terminals().get(terminalId);
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
            final ViewController viewController,
            final boolean hasSelectedTab,
            final int selectedTab) {
        if (!selectTerminal(viewController.currentTerminalId(), terminalIds)
                && !restoreSelectedTab(hasSelectedTab, selectedTab)) {
            openSummary();
        }
    }

    @Override
    protected Path workspacePath() {
        final String worktreePath = session.worktreePath();
        return Path.of(worktreePath);
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
                            this.dialogService,
                            session.id(),
                            appState,
                            viewController,
                            () ->
                                    new RemoveSessionAction(
                                                    this.backgroundJobs,
                                                    viewController,
                                                    appState,
                                                    dialogService)
                                            .execute(),
                            asyncLoadingStateFactory.getAsyncLoadingState(ViewId.SESSION));
            final JScrollPane summaryScroll = new JScrollPane(summary);
            summaryScroll.setBorder(null);
            summaryScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            summaryScroll.getVerticalScrollBar().setUnitIncrement(14);
            tabs.addTab("Summary", summaryScroll);
        } catch (RuntimeException exception) {
            LOG.error("Failed to create session summary for {}", session.id(), exception);
            tabs.addTab(
                    "Summary",
                    new EmptyStatePanel(
                            "Could not load session summary",
                            exception.getClass().getSimpleName() + ": " + exception.getMessage()));
        }
    }

    @Override
    protected void showActions(final JButton button) {
        UiPopupMenus.show(sessionActions.menu(session.id()), button, 0, button.getHeight());
    }

    @Override
    protected void openTerminal() {
        final String tabTitle = "Terminal " + (++terminalNumber);
        final TerminalId terminalId =
                appState.addTerminal(
                        viewController.currentSessionId(),
                        Terminal.create(
                                viewController.currentSessionId(),
                                tabTitle,
                                PlatformCommands.userShell()));
        addTerminal(terminalId, appState.terminals().get(terminalId), true);
    }

    public void openSummary() {
        tabs.setSelectedIndex(tabs.indexOfTab("Summary"));
    }

    @Override
    protected void updateSelectedTab(final int selectedIndex) {
        viewController.updateSelectedSessionTab(session.id(), selectedIndex);
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
                        dialogService.input(
                                "Session name:", "Rename session", DialogService.MessageType.PLAIN);
        if (updated == null || updated.isBlank()) {
            return;
        }
        titleLabel().setText(updated.trim());
        appState.updateSession(
                viewController.currentSessionId(),
                new Session(
                        session.id(),
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
        final var project = appState.projects().get(session.projectId());
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
                                    + persistedTerminal.title(),
                            dialogService);
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
                persistedTerminal.id(),
                persistedTerminal.owner(),
                persistedTerminal.title(),
                PlatformCommands.userShell());
    }
}
