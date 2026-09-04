package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.PullRequestCache;
import com.jagent.desktop.ui.components.ProjectActions;
import com.jagent.desktop.ui.components.PullRequestsBoard;
import com.jagent.desktop.ui.components.TabBody;
import com.jagent.desktop.ui.components.TerminalPanel;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JPanel;

public final class ProjectView extends AbstractWorkspaceView {
    private final transient Project project;
    private final transient ProjectId projectId;
    private final transient PullRequestCache pullRequestCache;
    private final PullRequestsBoard authoredPullRequests;
    private final PullRequestsBoard reviewPullRequests;
    private int terminalNumber;

    public ProjectView(final ActionContext actionContext, final Project project) {
        super(actionContext, ViewId.PROJECT);
        this.project = project;
        this.pullRequestCache = PullRequestCache.get(actionContext.appState());
        this.projectId =
                actionContext.appState().projects().entrySet().stream()
                        .filter(entry -> entry.getValue().equals(project))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(null);
        this.authoredPullRequests =
                new PullRequestsBoard(
                        actionContext,
                        () ->
                                this.projectId == null
                                        ? List.of()
                                        : pullRequestCache.get(this.projectId).authored());
        this.reviewPullRequests =
                new PullRequestsBoard(
                        actionContext,
                        () ->
                                this.projectId == null
                                        ? List.of()
                                        : pullRequestCache.get(this.projectId).review());
        initializeWorkspace(project.name());
        final TerminalId selectedTerminal = actionContext.appState().currentTerminalId();
        actionContext.appState().terminals().entrySet().stream()
                .filter(entry -> belongsToProject(entry.getValue()))
                .forEach(
                        entry ->
                                showTerminal(
                                        entry.getKey(),
                                        entry.getValue(),
                                        Path.of(project.path()),
                                        entry.getKey().equals(selectedTerminal)));
        restoreSelectedTab();
    }

    @Override
    protected Path workspacePath() {
        return Path.of(project.path());
    }

    @Override
    protected void addTitleDetails(final JPanel titleArea) {}

    @Override
    protected void addDefaultTabs() {
        tabs.addTab("My PRs", TabBody.wrap(authoredPullRequests));
        tabs.addTab("Review requests", TabBody.wrap(reviewPullRequests));
    }

    @Override
    protected void showActions(final JButton actions) {
        if (projectId != null) {
            ProjectActions.menu(actionContext, projectId).show(actions, 0, actions.getHeight());
        }
    }

    @Override
    protected void openTerminal(final Path path) {
        addTerminal("Terminal", PlatformCommands.userShell(), path);
    }

    private void addTerminal(final String title, final String command, final Path directory) {
        final String tabTitle = title + " " + (++terminalNumber);
        final Terminal definition = new Terminal(null, projectId, tabTitle, command);
        final TerminalId terminalId = actionContext.appState().addTerminal(definition);
        showTerminal(terminalId, definition, directory, true);
    }

    private boolean belongsToProject(final Terminal terminal) {
        return terminal.sessionId() == null && projectId.equals(terminal.projectId());
    }

    private void showTerminal(
            final TerminalId terminalId,
            final Terminal terminalDefinition,
            final Path directory,
            final boolean selected) {
        if (terminalId == null || terminalDefinition == null) {
            return;
        }
        final TerminalPanel terminal =
                TerminalPanel.retained(
                        terminalId,
                        terminalDefinition,
                        directory,
                        project.name() + " > " + terminalDefinition.title());
        mountTerminal(terminalDefinition.title(), terminalId, terminal, selected);
    }

    @Override
    protected void terminalClosed() {
        updateCurrentTerminal();
    }

    public void reviewPullRequest(final PullRequest request) {
        final PullRequestCache.ProjectPullRequests requests =
                pullRequestCache.getCached(this.projectId);
        if (requests.authored().contains(request)) {
            tabs.setSelectedIndex(0);
        } else {
            tabs.setSelectedIndex(1);
        }
    }

    public boolean focusPullRequestSearch() {
        return false;
    }

    /** Adds a project terminal; project summary and pull-request tabs are not terminal tabs. */
    public void createTerminal() {
        addTerminal("Terminal", PlatformCommands.userShell(), Path.of(project.path()));
    }

    public void openSummary() {
        tabs.setSelectedIndex(0);
    }

    private void restoreSelectedTab() {
        if (actionContext.appState().currentTerminalId() != null) {
            return;
        }
        if (!viewCoordinator.hasSelectedTab(id())) {
            return;
        }
        final int selectedTab = viewCoordinator.selectedTab(id());
        if (selectedTab < tabs.getTabCount()) {
            tabs.setSelectedIndex(selectedTab);
        }
    }
}
