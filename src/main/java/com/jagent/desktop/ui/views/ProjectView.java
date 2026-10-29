package com.jagent.desktop.ui.views;

import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.async.AsyncLoadingStateFactory;
import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundJobs;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.dialog.DialogService;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.services.github.GitHubProvider;
import com.jagent.desktop.ui.components.ProjectActions;
import com.jagent.desktop.ui.components.PullRequestsBoard;
import com.jagent.desktop.ui.components.TabBody;
import com.jagent.desktop.ui.components.UiPopupMenus;
import com.jagent.desktop.ui.components.terminal.TerminalPanel;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProjectView extends AbstractWorkspaceView {
    private static final Logger LOG = LoggerFactory.getLogger(ProjectView.class);
    private final transient Project project;
    private final PullRequestsBoard pullRequests;
    private final JPanel gitWarning = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final transient ProjectActions projectActions;
    private int terminalNumber;

    @AssistedInject
    public ProjectView(
            final AsyncLoadingStateFactory asyncLoadingStateFactory,
            final BackgroundJobs backgroundJobs,
            final ViewController viewController,
            final AppState appState,
            final GitHubProvider githubProvider,
            final DialogService dialogService,
            final ProjectActions projectActions,
            final @Assisted Project project) {
        super(viewController, appState, githubProvider, dialogService, ViewId.PROJECT);
        this.project = project;
        this.projectActions = projectActions;
        this.pullRequests =
                new PullRequestsBoard(
                        asyncLoadingStateFactory,
                        backgroundJobs,
                        viewController,
                        appState,
                        githubProvider,
                        dialogService,
                        project.id());
        initializeWorkspace(project.name());
        final TerminalId selectedTerminal = viewController.currentTerminalId();
        appState.terminals().entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getKey() != null)
                .filter(entry -> project.id().equals(entry.getValue().owner()))
                .forEach(
                        entry ->
                                showTerminal(
                                        entry.getKey(),
                                        entry.getValue(),
                                        entry.getKey().equals(selectedTerminal)));
        restoreSelectedTab();
    }

    @FunctionalInterface
    public interface Factory {
        ProjectView create(Project project);
    }

    @Override
    protected Path workspacePath() {
        return Path.of(project.path());
    }

    @Override
    protected void addTitleDetails(final JPanel titleArea) {
        gitWarning.add(new JLabel("This folder is not a Git repository."));
        titleArea.add(gitWarning);
        gitWarning.setVisible(false);
        BackgroundOperations.submit(
                        "Project",
                        "git-repository-check",
                        () -> GitRepository.isRepository(Path.of(project.path())))
                .thenAcceptAsync(
                        isRepository -> gitWarning.setVisible(!isRepository),
                        SwingUtilities::invokeLater)
                .exceptionally(
                        failure -> {
                            LOG.warn(
                                    "Could not verify Git repository state for project {}",
                                    project.name(),
                                    failure);
                            return null;
                        });
    }

    @Override
    protected void addDefaultTabs() {
        final JPanel tab = TabBody.wrap(pullRequests);
        tabs.addTab("Pull Requests", tab);
    }

    @Override
    protected void showActions(final JButton actions) {

        UiPopupMenus.show(projectActions.menu(project.id()), actions, 0, actions.getHeight());
    }

    @Override
    protected void openTerminal() {
        final String tabTitle = "Terminal " + (++terminalNumber);
        final Terminal definition =
                Terminal.create(project.id(), tabTitle, PlatformCommands.userShell());
        final TerminalId terminalId = appState.addTerminal(definition);
        showTerminal(terminalId, definition, true);
    }

    private void showTerminal(
            @NotNull final TerminalId terminalId,
            @NotNull final Terminal terminalDefinition,
            final boolean selected) {
        final TerminalPanel terminal =
                TerminalPanel.retained(
                        terminalId,
                        terminalDefinition,
                        Path.of(project.path()),
                        project.name() + " > " + terminalDefinition.title(),
                        this.dialogService);
        mountTerminal(terminalDefinition.title(), terminalId, terminal, selected);
    }

    @Override
    public CompletableFuture<?> refresh() {
        return pullRequests.refresh();
    }

    public void openSummary() {
        tabs.setSelectedIndex(0);
    }

    private void restoreSelectedTab() {
        if (viewController.currentTerminalId() != null) {
            return;
        }
        if (!viewController.hasSelectedTab(id())) {
            return;
        }
        final int selectedTab = viewController.selectedTab(id());
        if (selectedTab < tabs.getTabCount()) {
            tabs.setSelectedIndex(selectedTab);
        }
    }
}
