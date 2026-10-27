package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.git.GitRepository;
import com.jagent.desktop.ui.components.ProjectActions;
import com.jagent.desktop.ui.components.PullRequestsBoard;
import com.jagent.desktop.ui.components.TabBody;
import com.jagent.desktop.ui.components.TerminalPanel;
import com.jagent.desktop.ui.components.UiPopupMenus;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.Map;
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
    private final transient ProjectId projectId;
    private final PullRequestsBoard pullRequests;
    private final JPanel gitWarning = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private int terminalNumber;

    public ProjectView(final ActionContext actionContext, final Project project) {
        super(actionContext, ViewId.PROJECT);
        this.project = project;
        this.projectId =
                actionContext.appState().projects().entrySet().stream()
                        .filter(entry -> entry.getValue().equals(project))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(null);
        this.pullRequests = new PullRequestsBoard(actionContext, projectId);
        initializeWorkspace(project.name());
        final TerminalId selectedTerminal = actionContext.viewController().currentTerminalId();
        actionContext.appState().terminals().entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getKey() != null)
                .filter(
                        entry ->
                                entry.getValue().sessionId() == null
                                        && projectId.equals(entry.getValue().projectId()))
                .forEach(
                        entry ->
                                showTerminal(
                                        entry.getKey(),
                                        entry.getValue(),
                                        entry.getKey().equals(selectedTerminal)));
        restoreSelectedTab();
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
        if (projectId != null) {
            UiPopupMenus.show(
                    ProjectActions.menu(actionContext, projectId), actions, 0, actions.getHeight());
        }
    }

    @Override
    protected void openTerminal() {
        final String tabTitle = "Terminal " + (++terminalNumber);
        final Terminal definition =
                new Terminal(null, projectId, tabTitle, PlatformCommands.userShell());
        final TerminalId terminalId = actionContext.appState().addTerminal(definition);
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
                        project.name() + " > " + terminalDefinition.title());
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
        if (actionContext.viewController().currentTerminalId() != null) {
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
