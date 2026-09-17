package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestFilter;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.Git;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.PullRequestCache;
import com.jagent.desktop.ui.components.ProjectActions;
import com.jagent.desktop.ui.components.PullRequestsBoard;
import com.jagent.desktop.ui.components.TabBody;
import com.jagent.desktop.ui.components.TerminalPanel;
import com.jagent.desktop.ui.components.UiFactory;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public final class ProjectView extends AbstractWorkspaceView {
    private static final String DEFAULT_FILTER = "Active";
    private static final String REVIEWABLE_FILTER = "Reviewable";
    private final transient Project project;
    private final transient ProjectId projectId;
    private final transient PullRequestCache pullRequestCache;
    private final PullRequestsBoard pullRequests;
    private final JComboBox<PullRequestFilter> filters;
    private final JPanel gitWarning = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JButton initializeGit = new JButton("Initialize Git");
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
        this.filters =
                new JComboBox<>(
                        actionContext
                                .appState()
                                .appSettings()
                                .pullRequestFilters()
                                .toArray(PullRequestFilter[]::new));
        this.filters.setSelectedItem(
                actionContext.appState().appSettings().filterNamed(DEFAULT_FILTER));
        final PullRequestFilter defaultFilter =
                actionContext.appState().appSettings().filterNamed(DEFAULT_FILTER);
        this.pullRequests =
                new PullRequestsBoard(
                        actionContext,
                        defaultFilter.query(),
                        query ->
                                this.projectId == null
                                        ? List.of()
                                        : pullRequestCache.loadForProjectFilter(
                                                this.projectId,
                                                (PullRequestFilter) this.filters.getSelectedItem()
                                                                == null
                                                        ? new PullRequestFilter(
                                                                DEFAULT_FILTER, query)
                                                        : new PullRequestFilter(
                                                                ((PullRequestFilter)
                                                                                this.filters
                                                                                        .getSelectedItem())
                                                                        .name(),
                                                                query)));
        this.filters.addActionListener(
                event -> {
                    final PullRequestFilter selected =
                            (PullRequestFilter) this.filters.getSelectedItem();
                    this.pullRequests.setQuery(selected == null ? "" : selected.query());
                    this.pullRequests.refresh();
                });
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
    protected void addTitleDetails(final JPanel titleArea) {
        if (Git.isRepository(Path.of(project.path()))) {
            return;
        }
        gitWarning.setOpaque(false);
        gitWarning.add(new JLabel("This folder is not a Git repository."));
        initializeGit.addActionListener(event -> initializeGit());
        gitWarning.add(initializeGit);
        titleArea.add(gitWarning);
    }

    private void initializeGit() {
        initializeGit.setEnabled(false);
        new Git()
                .initializeRepository(Path.of(project.path()))
                .whenComplete(
                        (ignored, failure) ->
                                SwingUtilities.invokeLater(
                                        () -> {
                                            if (failure == null) {
                                                gitWarning.setVisible(false);
                                                gitWarning.getParent().revalidate();
                                                gitWarning.getParent().repaint();
                                            } else {
                                                initializeGit.setEnabled(true);
                                                JOptionPane.showMessageDialog(
                                                        this,
                                                        "Git could not be initialized: "
                                                                + failure.getMessage(),
                                                        "Initialize Git",
                                                        JOptionPane.ERROR_MESSAGE);
                                            }
                                        }));
    }

    @Override
    protected void addDefaultTabs() {
        final JPanel tab = new JPanel(new BorderLayout(0, 8));
        final JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filterRow.setOpaque(false);
        filterRow.add(new JLabel("Filter"));
        filterRow.add(filters);
        tab.add(filterRow, BorderLayout.NORTH);
        tab.add(TabBody.wrap(pullRequests), BorderLayout.CENTER);
        tabs.addTab("Pull Requests", tab);
    }

    @Override
    protected void showActions(final JButton actions) {
        if (projectId != null) {
            UiFactory.showPopupMenu(
                    ProjectActions.menu(actionContext, projectId), actions, 0, actions.getHeight());
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

    @Override
    public void refresh() {
        pullRequests.refresh();
    }

    public void reviewPullRequest(final PullRequest request) {
        final PullRequestFilter reviewable =
                actionContext.appState().appSettings().filterNamed(REVIEWABLE_FILTER);
        filters.setSelectedItem(reviewable);
        pullRequests.setQuery(reviewable.query());
        tabs.setSelectedIndex(0);
    }

    public boolean focusPullRequestSearch() {
        return pullRequests.focusSearch();
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
