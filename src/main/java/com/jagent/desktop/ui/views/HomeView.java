package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.services.AppState;
import com.jagent.desktop.services.BackgroundTasks;
import com.jagent.desktop.services.Git;
import com.jagent.desktop.services.PullRequestCache;
import com.jagent.desktop.ui.components.ProjectCards;
import com.jagent.desktop.ui.components.PullRequestsBoard;
import com.jagent.desktop.ui.components.TabBody;
import com.jagent.desktop.ui.components.TerminalPanel;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiFactory;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.border.EmptyBorder;

public final class HomeView extends JPanel implements View {
    private static final Logger LOG = Logger.getLogger(HomeView.class.getName());
    private final transient AppState appState;
    private final transient PullRequestCache pullRequestCache;
    private final PullRequestsBoard authoredPullRequests;
    private final PullRequestsBoard reviewPullRequests;
    private final JPanel terminalHost = new JPanel(new BorderLayout());
    private final JTabbedPane tabs;
    private TerminalPanel summaryTerminal;

    public HomeView(final ActionContext actionContext) {
        super();
        this.appState = actionContext.appState();
        this.pullRequestCache = PullRequestCache.get(appState);
        this.authoredPullRequests = new PullRequestsBoard(actionContext, () -> pullRequests(true));
        this.reviewPullRequests = new PullRequestsBoard(actionContext, () -> pullRequests(false));

        setLayout(new BorderLayout(0, 18));

        final JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        add(header, BorderLayout.NORTH);
        tabs = new JTabbedPane();
        tabs.addTab("Projects", TabBody.wrap(new ProjectCards(actionContext)));
        tabs.addTab("My PRs", TabBody.wrap(authoredPullRequests));
        tabs.addTab("Review requests", TabBody.wrap(reviewPullRequests));
        tabs.addTab("PR Summary", TabBody.wrap(summaryTab()));
        add(tabs, BorderLayout.CENTER);
    }

    @Override
    public ViewId id() {
        return ViewId.HOME;
    }

    @Override
    public String title() {
        return "Home";
    }

    @Override
    public JPanel render() {
        return this;
    }

    private java.util.List<PullRequest> pullRequests(final boolean authored) {
        return appState.projects().keySet().stream()
                .flatMap(
                        projectId -> {
                            final var requests = pullRequestCache.get(projectId);
                            return (authored ? requests.authored() : requests.review()).stream();
                        })
                .toList();
    }

    private JComponent summaryTab() {
        final var agents = this.appState.appSettings().agents();
        final JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        panel.setOpaque(false);
        final JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        final JComboBox<Agent> agent = new JComboBox<>(agents.toArray(new Agent[0]));
        final JButton run = UiFactory.button("Run summary");
        run.setEnabled(!agents.isEmpty());
        controls.add(UiFactory.label("Agent", Theme.FontSize.SM));
        controls.add(agent);
        controls.add(run);
        panel.add(controls, BorderLayout.NORTH);
        terminalHost.setOpaque(false);
        terminalHost.add(
                UiFactory.empty(
                        "Run a pull request summary",
                        "The selected agent will receive pull request data from every project."),
                BorderLayout.CENTER);
        panel.add(terminalHost, BorderLayout.CENTER);
        run.addActionListener(event -> runSummary((Agent) agent.getSelectedItem(), run));
        return panel;
    }

    private void runSummary(final Agent agent, final JButton run) {
        if (agent == null || appState.projects().isEmpty()) {
            return;
        }
        run.setEnabled(false);
        BackgroundTasks.submit(
                "Pull Requests",
                "prepare-summary-prompt",
                () -> {
                    try {
                        final String prompt = summaryPrompt();
                        javax.swing.SwingUtilities.invokeLater(
                                () -> startSummary(agent, prompt, run));
                    } catch (Throwable failure) {
                        javax.swing.SwingUtilities.invokeLater(
                                () -> {
                                    run.setEnabled(true);
                                    LOG.log(Level.SEVERE, "Prepare pull request summary", failure);
                                });
                    }
                });
    }

    private void startSummary(final Agent agent, final String prompt, final JButton run) {
        final String command = agent.newSessionCommand.replace("{prompt}", Git.shellQuote(prompt));
        if (summaryTerminal != null) {
            summaryTerminal.dispose();
        }
        terminalHost.removeAll();
        summaryTerminal =
                new TerminalPanel(
                        command,
                        Path.of(appState.projects().values().stream().toList().getFirst().path()));
        terminalHost.add(summaryTerminal, BorderLayout.CENTER);
        terminalHost.revalidate();
        terminalHost.repaint();
        summaryTerminal.start();
        run.setEnabled(true);
    }

    private String summaryPrompt() {
        final StringBuilder prompt =
                new StringBuilder(
                        "Prioritize and summarize the following pull requests across all projects. "
                                + "Return a concise Markdown or plain-text summary with priority and "
                                + "short reasoning for each request.\n\n");
        appState.projects()
                .entrySet()
                .forEach(
                        (p) -> {
                            final var projectId = p.getKey();
                            final var project = p.getValue();
                            final var pullRequests = pullRequestCache.get(projectId);
                            pullRequests
                                    .authored()
                                    .forEach(request -> appendPrompt(prompt, project, request));
                            pullRequests
                                    .review()
                                    .forEach(request -> appendPrompt(prompt, project, request));
                        });
        return prompt.toString();
    }

    private static void appendPrompt(
            final StringBuilder prompt, final Project project, final PullRequest request) {
        prompt.append("project=")
                .append(project.name())
                .append(" #")
                .append(request.number())
                .append(" title=")
                .append(request.title())
                .append(" author=")
                .append(request.author())
                .append(" review=")
                .append(request.reviewDecision())
                .append(" merge=")
                .append(request.mergeable())
                .append(" checks=")
                .append(request.checksStatus())
                .append(" description=")
                .append(request.description())
                .append(" recentComments=")
                .append(request.commentSummary())
                .append(" url=")
                .append(request.url())
                .append('\n');
    }

    public void dispose() {
        if (summaryTerminal != null) {
            summaryTerminal.dispose();
        }
    }
}
