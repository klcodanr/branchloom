package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.models.TerminalId;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.PullRequestCache;
import com.jagent.desktop.ui.components.ClosableTabHeader;
import com.jagent.desktop.ui.components.ProjectActions;
import com.jagent.desktop.ui.components.PullRequestsBoard;
import com.jagent.desktop.ui.components.StatusDots;
import com.jagent.desktop.ui.components.TabBody;
import com.jagent.desktop.ui.components.TerminalPanel;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiFactory;
import com.jagent.desktop.ui.components.UiIcons;
import com.jagent.desktop.ui.components.WorkspaceTreePanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.border.EmptyBorder;

public final class ProjectView extends JPanel implements View {
    private final transient Project project;
    private final transient ProjectId projectId;
    private final transient ActionContext actionContext;
    private final transient PullRequestCache pullRequestCache;
    private final JTabbedPane tabs = new JTabbedPane();
    private final PullRequestsBoard authoredPullRequests;
    private final PullRequestsBoard reviewPullRequests;
    private int terminalNumber;

    public ProjectView(final ActionContext actionContext, final Project project) {
        super();
        this.actionContext = actionContext;
        this.project = project;
        this.pullRequestCache = PullRequestCache.get(actionContext.appState());
        this.projectId =
                actionContext.appState().projects().entrySet().stream()
                        .filter(entry -> entry.getValue().equals(project))
                        .map(java.util.Map.Entry::getKey)
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
        setLayout(new BorderLayout(0, 16));
        add(header(), BorderLayout.NORTH);
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        add(tabs, BorderLayout.CENTER);
        tabs.addTab("Files", TabBody.wrap(workspace()));
        tabs.addTab("My PRs", TabBody.wrap(authoredPullRequests));
        tabs.addTab("Review requests", TabBody.wrap(reviewPullRequests));
        final TerminalId selectedTerminal = actionContext.appState().currentTerminalId();
        actionContext.appState().terminals().entrySet().stream()
                .filter(entry -> belongsToProject(entry.getValue()))
                .forEach(
                        entry ->
                                javax.swing.SwingUtilities.invokeLater(
                                        () ->
                                                showTerminal(
                                                        entry.getKey(),
                                                        entry.getValue(),
                                                        Path.of(project.path()),
                                                        entry.getKey().equals(selectedTerminal))));
    }

    @Override
    public ViewId id() {
        return ViewId.PROJECT;
    }

    @Override
    public String title() {
        return project.name();
    }

    @Override
    public JPanel render() {
        return this;
    }

    public void dispose() {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getComponentAt(i) instanceof TerminalPanel terminal) {
                terminal.dispose();
            }
        }
    }

    private JPanel header() {
        final JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, 0, 12));
        final JLabel title = UiFactory.label(project.name(), Theme.FontSize.XXL);
        title.setMinimumSize(new Dimension(0, title.getMinimumSize().height));
        header.add(title, BorderLayout.CENTER);
        final JButton actions = UiFactory.iconButton(UiIcons.ellipsis());
        actions.setToolTipText("Project actions");
        actions.getAccessibleContext().setAccessibleName("Project actions");
        actions.addActionListener(
                event -> {
                    if (projectId != null) {
                        ProjectActions.menu(actionContext, projectId)
                                .show(actions, 0, actions.getHeight());
                    }
                });
        final JPanel actionArea = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actionArea.setOpaque(false);
        actionArea.setBorder(new EmptyBorder(0, 12, 0, 0));
        actionArea.add(actions);
        actionArea.setMinimumSize(actionArea.getPreferredSize());
        header.add(actionArea, BorderLayout.EAST);
        return header;
    }

    private JPanel workspace() {
        final JPanel workspace = new JPanel(new BorderLayout());
        workspace.setOpaque(false);
        workspace.setBorder(new EmptyBorder(16, 2, 2, 2));
        workspace.add(
                new WorkspaceTreePanel(
                        actionContext,
                        Path.of(project.path()),
                        path -> addTerminal("Terminal", PlatformCommands.userShell(), path)),
                BorderLayout.CENTER);
        return workspace;
    }

    private void addTerminal(final String title, final String command, final Path directory) {
        final String tabTitle = title + " " + (++terminalNumber);
        final Terminal definition = new Terminal(null, projectId, tabTitle, command);
        final TerminalId terminalId = actionContext.appState().addTerminal(definition);
        showTerminal(terminalId, definition, directory, true);
    }

    private boolean belongsToProject(final Terminal terminal) {
        if (projectId.equals(terminal.projectId())) {
            return true;
        }
        if (terminal.sessionId() == null) {
            return false;
        }
        final var session = actionContext.appState().sessions().get(terminal.sessionId());
        return session != null && projectId.equals(session.projectId());
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
        if (terminal.getParent() != null) {
            terminal.getParent().remove(terminal);
        }
        final JComponent terminalStatus = StatusDots.terminal(terminal.state());
        terminal.setStateChanged(state -> StatusDots.updateTerminal(terminalStatus, state));
        tabs.addTab(terminalDefinition.title(), terminal);
        final int index = tabs.indexOfComponent(terminal);
        tabs.setTabComponentAt(
                index,
                ClosableTabHeader.create(
                        tabs,
                        terminalDefinition.title(),
                        terminal,
                        () -> {
                            actionContext.appState().removeTerminal(terminalId);
                            terminal.dispose();
                        },
                        null,
                        terminalStatus));
        if (selected) {
            tabs.setSelectedComponent(terminal);
        }
        terminal.start();
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

    public void closeActiveTerminal() {
        final int index = tabs.getSelectedIndex();
        if (index < 0 || !(tabs.getComponentAt(index) instanceof TerminalPanel terminal)) {
            return;
        }
        tabs.removeTabAt(index);
        terminal.dispose();
    }

    public void renameActiveTerminal() {
        final int index = tabs.getSelectedIndex();
        if (index < 0 || !(tabs.getComponentAt(index) instanceof TerminalPanel)) {
            return;
        }
        final String updated =
                (String)
                        javax.swing.JOptionPane.showInputDialog(
                                this,
                                "Terminal tab name:",
                                "Rename terminal tab",
                                javax.swing.JOptionPane.PLAIN_MESSAGE,
                                null,
                                null,
                                tabs.getTitleAt(index));
        if (updated != null && !updated.isBlank()) {
            tabs.setTitleAt(index, updated.trim());
        }
    }

    /** Selects the 1-based terminal index; non-terminal tabs, including Summary, are excluded. */
    public void selectTerminal(final int index) {
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

    public void openSummary() {
        tabs.setSelectedIndex(0);
    }
}
