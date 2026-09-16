package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.services.PullRequestCache;
import com.jagent.desktop.services.ReviewPlanAgent;
import com.jagent.desktop.ui.components.PullRequestsBoard;
import com.jagent.desktop.ui.components.TerminalPanel;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiConstants;
import com.jagent.desktop.ui.components.UiFactory;
import java.awt.BorderLayout;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ScrollPaneConstants;

/** Review requests with a browsable queue and deterministic review plan. */
public final class ReviewQueueView extends JPanel implements View {
    private final transient ActionContext actionContext;
    private final transient PullRequestCache pullRequestCache;
    private final PullRequestsBoard board;
    private final JTabbedPane tabs = new JTabbedPane();
    private final JPanel reviewPlanContent = new JPanel(new BorderLayout());

    public ReviewQueueView(final ActionContext actionContext) {
        super(new BorderLayout());
        this.actionContext = actionContext;
        this.pullRequestCache = PullRequestCache.get(actionContext.appState());
        board = new PullRequestsBoard(actionContext, this::reviewRequests);
        tabs.addTab("Queue", board);
        tabs.addTab("Review Plan", reviewPlan());
        add(tabs, BorderLayout.CENTER);
    }

    @Override
    public ViewId id() {
        return ViewId.REVIEW_QUEUE;
    }

    @Override
    public String title() {
        return "Review Queue";
    }

    @Override
    public JPanel render() {
        return this;
    }

    @Override
    public void refresh() {
        board.refresh();
    }

    @Override
    public void detach() {}

    private List<PullRequest> reviewRequests() {
        return actionContext.appState().projects().keySet().stream()
                .flatMap(projectId -> pullRequestCache.get(projectId).review().stream())
                .toList();
    }

    private List<PullRequest> cachedReviewRequests() {
        return actionContext.appState().projects().keySet().stream()
                .flatMap(projectId -> pullRequestCache.getCached(projectId).review().stream())
                .toList();
    }

    private JPanel reviewPlan() {
        final JPanel panel = new JPanel(new BorderLayout(0, UiConstants.COMPONENT_GAP));
        panel.setBorder(UiFactory.sectionBorder());
        reviewPlanContent.setOpaque(false);
        final JScrollPane scroll = new JScrollPane(reviewPlanContent);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(scroll, BorderLayout.CENTER);
        loadPlan(reviewPlanContent, cachedReviewRequests());
        return panel;
    }

    private void loadPlan(final JPanel content, final List<PullRequest> requests) {
        content.removeAll();
        final JPanel plan = new JPanel(new BorderLayout());
        plan.setOpaque(false);
        populatePlan(plan, requests);
        content.add(
                agentLauncher(
                        plan, requests, actionContext.appState().appSettings().reviewPlanPrompt()),
                BorderLayout.NORTH);
        content.add(plan, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    private JPanel agentLauncher(
            final JPanel plan, final List<PullRequest> requests, final String reviewPrompt) {
        final List<Agent> agents = actionContext.appState().appSettings().agents();
        final JPanel launcher = new JPanel(new BorderLayout(UiConstants.COMPONENT_GAP, 0));
        launcher.setOpaque(false);
        final JComboBox<Agent> selector = new JComboBox<>(agents.toArray(Agent[]::new));
        final JButton start = UiFactory.button("Start review plan");
        start.setEnabled(canStartAgent(selector));
        selector.addActionListener(event -> start.setEnabled(canStartAgent(selector)));
        start.addActionListener(
                event -> {
                    final Agent selected = (Agent) selector.getSelectedItem();
                    if (!hasCommand(selected)) {
                        return;
                    }
                    final String command =
                            ReviewPlanAgent.command(
                                    selected.newSessionCommand, reviewPrompt, requests);
                    final TerminalPanel terminal =
                            new TerminalPanel(
                                    command,
                                    Path.of(System.getProperty("user.home")),
                                    "review-plan-agent");
                    plan.removeAll();
                    plan.add(terminal, BorderLayout.CENTER);
                    plan.revalidate();
                    plan.repaint();
                    terminal.start();
                });
        launcher.add(UiFactory.label("Agent", Theme.FontSize.MD), BorderLayout.WEST);
        launcher.add(selector, BorderLayout.CENTER);
        launcher.add(start, BorderLayout.EAST);
        return launcher;
    }

    private static boolean canStartAgent(final JComboBox<Agent> selector) {
        return selector.getItemCount() > 0 && hasCommand((Agent) selector.getSelectedItem());
    }

    private static boolean hasCommand(final Agent selected) {
        return selected != null
                && selected.newSessionCommand != null
                && !selected.newSessionCommand.isBlank();
    }

    private void populatePlan(final JPanel items, final List<PullRequest> requests) {
        items.removeAll();
        final String detail =
                requests.isEmpty()
                        ? "Start review plan to have your configured agent rank urgency and identify low-hanging fruit."
                        : requests.size()
                                + " review requests available. Start review plan to have your configured agent rank urgency and identify low-hanging fruit.";
        items.add(UiFactory.empty("Review plan", detail));
        items.revalidate();
        items.repaint();
    }

    protected static String contextFor(final PullRequest request) {
        return "Change size: +"
                + request.additions()
                + " / -"
                + request.deletions()
                + " across "
                + request.changedFiles()
                + " files"
                + "  ·  Checks: "
                + request.checksPassed()
                + "/"
                + request.checksTotal()
                + " "
                + request.checksStatus()
                + "  ·  Mergeability: "
                + request.mergeable()
                + "  ·  Draft: "
                + request.draft();
    }

    protected static String commentSummaryFor(final PullRequest request) {
        if (request.commentSummary().isBlank()) {
            return "No recent comments captured.";
        }
        return request.commentSummary();
    }
}
