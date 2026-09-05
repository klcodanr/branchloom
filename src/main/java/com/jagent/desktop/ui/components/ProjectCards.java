package com.jagent.desktop.ui.components;

import com.jagent.desktop.api.Action;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.Session;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundTasks;
import com.jagent.desktop.services.PullRequestCache;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.services.terminal.TerminalManager;
import com.jagent.desktop.services.terminal.TerminalState;
import com.jagent.desktop.ui.actions.CreateSessionAction;
import com.jagent.desktop.ui.actions.CreateTerminalAction;
import com.jagent.desktop.ui.actions.OpenDirectoryAction;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.Map.Entry;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;

public final class ProjectCards extends JPanel {
    private final transient AppState appState;
    private final transient ActionContext actionContext;
    private final transient PullRequestCache pullRequestCache;
    private final transient ViewCoordinator viewCoordinator;
    private final transient TerminalManager terminalManager = TerminalManager.get();
    private final JPanel cards = new JPanel();

    public ProjectCards(final ActionContext actionContext) {
        super();
        this.actionContext = actionContext;
        this.appState = actionContext.appState();
        this.viewCoordinator = actionContext.viewCoordinator();
        this.pullRequestCache = PullRequestCache.get(appState);
        setLayout(new BorderLayout());
        cards.setOpaque(false);
        cards.setLayout(new GridLayout(0, 1, UiConstants.COMPONENT_GAP, UiConstants.COMPONENT_GAP));
        cards.addComponentListener(
                new ComponentAdapter() {
                    @Override
                    public void componentResized(final ComponentEvent event) {
                        updateColumns();
                    }
                });
        add(cards, BorderLayout.NORTH);
        refresh();
    }

    public void refresh() {
        renderCards();
        BackgroundTasks.submit(
                "Home",
                "project-card-pr-counts",
                () -> {
                    for (final ProjectId projectId : appState.projects().keySet()) {
                        pullRequestCache.refresh(projectId);
                    }
                    SwingUtilities.invokeLater(this::renderCards);
                });
    }

    private void renderCards() {
        cards.removeAll();
        for (final Entry<ProjectId, Project> project : appState.projects().entrySet()) {
            final ProjectId projectId = project.getKey();
            final Project projectDefinition = project.getValue();
            final JPanel card = new JPanel();
            card.setAlignmentX(LEFT_ALIGNMENT);
            card.setBorder(cardBorder());
            card.setBackground(UIManager.getColor("TextField.background"));
            card.setPreferredSize(new Dimension(250, 150));
            card.setMinimumSize(new Dimension(250, 150));
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
            final Runnable openProject =
                    () ->
                            viewCoordinator.updateView(
                                    ViewId.PROJECT, ViewCoordinator.ViewState.project(projectId));
            final JButton name = UiFactory.link(projectDefinition.name(), openProject);
            name.setFont(Theme.boldFont(Theme.FontSize.LG));
            name.setAlignmentX(LEFT_ALIGNMENT);
            card.addMouseListener(
                    new MouseAdapter() {
                        @Override
                        public void mouseClicked(final MouseEvent event) {
                            if (event.getButton() == MouseEvent.BUTTON1) {
                                openProject.run();
                            }
                        }
                    });
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.add(name);
            card.add(path(projectDefinition));
            card.add(Box.createVerticalStrut(UiConstants.SPACING_SM));
            card.add(metrics(projectId, projectDefinition));
            card.add(Box.createVerticalGlue());
            card.add(recentSessions(projectId, projectDefinition));
            card.add(Box.createVerticalStrut(UiConstants.SPACING_SM));
            card.add(actions(projectId));
            installProjectMenu(card, projectId);
            installProjectMenu(name, projectId);
            cards.add(card);
        }
        if (appState.projects().isEmpty()) {
            cards.add(UiFactory.empty("Add a Git project to begin", ""));
        }
        cards.revalidate();
        cards.repaint();
        updateColumns();
    }

    private void updateColumns() {
        final int width = cards.getWidth();
        if (width <= 0) {
            return;
        }
        final int columns = Math.max(1, width / 280);
        final GridLayout layout = (GridLayout) cards.getLayout();
        if (layout.getColumns() != columns) {
            layout.setColumns(columns);
            cards.revalidate();
        }
    }

    private JPanel metrics(final ProjectId projectId, final Project project) {
        final JPanel metrics = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        metrics.setOpaque(false);
        metrics.setAlignmentX(LEFT_ALIGNMENT);
        final ProjectTerminalCounts terminalCounts = terminalCounts(project);
        metrics.add(
                metric(
                        UiIcons.folderOpen(),
                        Integer.toString(project.sessionIds().size()),
                        "Sessions"));
        metrics.add(
                metric(UiIcons.activity(), Integer.toString(terminalCounts.active()), "Active"));
        if (terminalCounts.failed() > 0) {
            metrics.add(
                    metric(
                            UiIcons.alertCircle(),
                            Integer.toString(terminalCounts.failed()),
                            "Failed"));
        }
        if (pullRequestCache.hasCached(projectId)) {
            final var pullRequests = pullRequestCache.getCached(projectId);
            metrics.add(
                    metric(
                            UiIcons.userRoundArrowLeft(),
                            Integer.toString(pullRequests.authored().size()),
                            "My pull requests"));
            metrics.add(
                    metric(
                            UiIcons.messageSquareDiff(),
                            Integer.toString(pullRequests.review().size()),
                            "Review requests"));
            return metrics;
        }
        metrics.add(UiFactory.inlineLoading(""));
        return metrics;
    }

    private JPanel metric(final javax.swing.Icon icon, final String value, final String tooltip) {
        final JPanel metric = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        metric.setOpaque(false);
        metric.setToolTipText(tooltip);
        metric.add(new JLabel(icon));
        metric.add(UiFactory.label(value, Theme.FontSize.SM));
        return metric;
    }

    private JLabel path(final Project project) {
        final JLabel path = UiFactory.label(compactPath(project.path()), Theme.FontSize.XS);
        path.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
        path.setToolTipText(project.path());
        path.setAlignmentX(LEFT_ALIGNMENT);
        return path;
    }

    private String compactPath(final String value) {
        final String home = System.getProperty("user.home");
        final String display =
                value.startsWith(home) ? "~" + value.substring(home.length()) : value;
        return display.length() <= 38 ? display : "..." + display.substring(display.length() - 35);
    }

    private JPanel recentSessions(final ProjectId projectId, final Project project) {
        final JPanel sessions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        sessions.setOpaque(false);
        sessions.setAlignmentX(LEFT_ALIGNMENT);
        final var recentSessions =
                project.projectSessions(appState).stream()
                        .filter(entry -> entry.getValue() != null)
                        .sorted(
                                Comparator.comparing(
                                                (Entry<com.jagent.desktop.models.SessionId, Session>
                                                                entry) ->
                                                        entry.getValue().created())
                                        .reversed())
                        .limit(3)
                        .toList();
        if (recentSessions.isEmpty()) {
            sessions.add(UiFactory.label("No sessions yet", Theme.FontSize.XS));
            return sessions;
        }
        sessions.add(UiFactory.label("Recent:", Theme.FontSize.XS));
        for (final Entry<com.jagent.desktop.models.SessionId, Session> entry : recentSessions) {
            final Session session = entry.getValue();
            final JButton sessionLink =
                    UiFactory.link(
                            compactSessionName(session.name()),
                            () ->
                                    viewCoordinator.updateView(
                                            ViewId.SESSION,
                                            ViewCoordinator.ViewState.session(
                                                    projectId, entry.getKey())));
            sessionLink.setToolTipText("Open session " + session.name());
            sessions.add(sessionLink);
        }
        return sessions;
    }

    private String compactSessionName(final String value) {
        return value.length() <= 22 ? value : value.substring(0, 19) + "...";
    }

    private JPanel actions(final ProjectId projectId) {
        final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        actions.add(
                iconAction(
                        UiIcons.plus(),
                        "New session",
                        () -> runProjectAction(projectId, new CreateSessionAction(actionContext))));
        actions.add(
                iconAction(
                        UiIcons.forAction(new CreateTerminalAction(actionContext)),
                        "New terminal",
                        () ->
                                runProjectAction(
                                        projectId, new CreateTerminalAction(actionContext))));
        actions.add(
                iconAction(
                        UiIcons.forAction(new OpenDirectoryAction(actionContext)),
                        "Open folder",
                        () -> openFolder(projectId)));
        return actions;
    }

    private JButton iconAction(
            final javax.swing.Icon icon, final String tooltip, final Runnable action) {
        final JButton control = UiFactory.iconButton(icon, tooltip);
        control.addActionListener(event -> action.run());
        return control;
    }

    private void openFolder(final ProjectId projectId) {
        final Project project = appState.projects().get(projectId);
        if (project != null) {
            OpenDirectoryAction.open(project.path(), actionContext.window());
        }
    }

    private void runProjectAction(final ProjectId projectId, final Action action) {
        appState.updateCurrentProject(projectId);
        appState.updateCurrentSession(null);
        action.execute();
    }

    private ProjectTerminalCounts terminalCounts(final Project project) {
        int active = 0;
        int failed = 0;
        for (final Session session :
                project.projectSessions(appState).stream().map(Entry::getValue).toList()) {
            if (session == null) {
                continue;
            }
            for (final var terminalId : session.terminalIds()) {
                final TerminalState state = terminalManager.state(terminalId);
                if (state == TerminalState.WORKING || state == TerminalState.STARTING) {
                    active++;
                } else if (state == TerminalState.FAILED) {
                    failed++;
                }
            }
        }
        return new ProjectTerminalCounts(active, failed);
    }

    private record ProjectTerminalCounts(int active, int failed) {}

    private void installProjectMenu(final JComponent component, final ProjectId projectId) {
        component.setComponentPopupMenu(ProjectActions.menu(actionContext, projectId));
    }

    private Border cardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(
                        UiConstants.CARD_PADDING,
                        UiConstants.COMPONENT_GAP,
                        UiConstants.CARD_PADDING,
                        UiConstants.COMPONENT_GAP));
    }
}
