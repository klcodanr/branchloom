package com.jagent.desktop.ui.views;

import com.jagent.desktop.api.View;
import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Agent;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.services.BackgroundTasks;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.PullRequestCache;
import com.jagent.desktop.services.ReviewPlanAgent;
import com.jagent.desktop.ui.components.PullRequestsBoard;
import com.jagent.desktop.ui.components.TerminalPanel;
import com.jagent.desktop.ui.components.Theme;
import com.jagent.desktop.ui.components.UiConstants;
import com.jagent.desktop.ui.components.UiFactory;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

/** Review requests with a browsable queue and deterministic review plan. */
public final class ReviewQueueView extends JPanel implements View {
    private final transient ActionContext actionContext;
    private final transient PullRequestCache pullRequestCache;
    private final JTabbedPane tabs = new JTabbedPane();

    public ReviewQueueView(final ActionContext actionContext) {
        super(new BorderLayout());
        this.actionContext = actionContext;
        this.pullRequestCache = PullRequestCache.get(actionContext.appState());
        tabs.addTab("Queue", new PullRequestsBoard(actionContext, this::reviewRequests));
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
    public void detach() {}

    private List<PullRequest> reviewRequests() {
        return actionContext.appState().projects().keySet().stream()
                .flatMap(projectId -> pullRequestCache.get(projectId).review().stream())
                .toList();
    }

    private JPanel reviewPlan() {
        final JPanel panel = new JPanel(new BorderLayout(0, UiConstants.COMPONENT_GAP));
        panel.setBorder(UiFactory.sectionBorder());
        final JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.add(UiFactory.loading("Loading pull requests..."), BorderLayout.CENTER);
        final JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(scroll, BorderLayout.CENTER);
        BackgroundTasks.submit("Pull Requests", "review-plan-load", this::reviewRequests)
                .thenAcceptAsync(
                        requests -> loadPlan(content, requests), SwingUtilities::invokeLater)
                .exceptionally(
                        error -> {
                            SwingUtilities.invokeLater(
                                    () -> {
                                        content.removeAll();
                                        content.add(
                                                UiFactory.label(
                                                        "Unable to load review requests.",
                                                        Theme.FontSize.MD));
                                        content.revalidate();
                                        content.repaint();
                                    });
                            return null;
                        });
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
        start.setEnabled(selector.getItemCount() > 0);
        start.addActionListener(
                event -> {
                    final Agent selected = (Agent) selector.getSelectedItem();
                    if (selected == null || selected.newSessionCommand.isBlank()) {
                        return;
                    }
                    final TerminalPanel terminal =
                            new TerminalPanel(
                                    ReviewPlanAgent.command(
                                            selected.newSessionCommand, reviewPrompt, requests),
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

    private void populatePlan(final JPanel items, final List<PullRequest> requests) {
        items.removeAll();
        final List<PullRequest> plannedRequests = requests;
        if (plannedRequests.isEmpty()) {
            items.add(
                    UiFactory.empty(
                            "No review requests",
                            "Pull requests requiring your review will appear here."));
        }
        for (int index = 0; index < plannedRequests.size(); index++) {
            final PullRequest request = plannedRequests.get(index);
            final Project project = actionContext.appState().projects().get(request.projectId());
            final JPanel row = new JPanel(new BorderLayout(UiConstants.COMPONENT_GAP, 0));
            row.setOpaque(false);
            final JPanel details = new JPanel(new GridBagLayout());
            details.setOpaque(false);
            final GridBagConstraints titleConstraints = new GridBagConstraints();
            titleConstraints.gridx = 0;
            titleConstraints.gridy = 0;
            titleConstraints.gridwidth = 2;
            titleConstraints.weightx = 1;
            titleConstraints.fill = GridBagConstraints.HORIZONTAL;
            titleConstraints.anchor = GridBagConstraints.NORTHWEST;
            titleConstraints.insets = new Insets(0, 0, UiConstants.SPACING_XS, 0);
            final JTextArea title =
                    UiFactory.selectableText(
                            (index + 1)
                                    + ". "
                                    + (project == null ? "Project" : project.name())
                                    + " #"
                                    + request.number()
                                    + " - "
                                    + request.title(),
                            Theme.FontSize.MD);
            title.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            title.addMouseListener(
                    new MouseAdapter() {
                        @Override
                        public void mouseClicked(final MouseEvent event) {
                            PlatformCommands.openUrl(request.url());
                        }
                    });
            details.add(title, titleConstraints);
            final GridBagConstraints labelConstraints = new GridBagConstraints();
            labelConstraints.gridx = 0;
            labelConstraints.anchor = GridBagConstraints.NORTHWEST;
            labelConstraints.insets =
                    new Insets(0, 0, UiConstants.SPACING_XS, UiConstants.COMPONENT_GAP);
            final GridBagConstraints valueConstraints = new GridBagConstraints();
            valueConstraints.gridx = 1;
            valueConstraints.weightx = 1;
            valueConstraints.fill = GridBagConstraints.HORIZONTAL;
            valueConstraints.anchor = GridBagConstraints.NORTHWEST;
            valueConstraints.insets = new Insets(0, 0, UiConstants.SPACING_XS, 0);
            details.add(UiFactory.label("Why:", Theme.FontSize.SM), labelConstraints);
            details.add(
                    UiFactory.selectableText(reason(request), Theme.FontSize.SM), valueConstraints);
            labelConstraints.gridy = 1;
            valueConstraints.gridy = 1;
            details.add(UiFactory.label("Focus:", Theme.FontSize.SM), labelConstraints);
            details.add(
                    UiFactory.selectableText(focus(request), Theme.FontSize.SM), valueConstraints);
            row.add(details, BorderLayout.CENTER);
            items.add(row);
            items.add(Box.createVerticalStrut(UiConstants.COMPONENT_GAP));
        }
        items.revalidate();
        items.repaint();
    }

    private int bucket(final PullRequest request) {
        if (!request.draft()
                && "MERGEABLE".equals(request.mergeable())
                && !"FAILING".equals(request.checksStatus())) {
            return 0;
        }
        return 1;
    }

    private String reason(final PullRequest request) {
        if (bucket(request) == 0) {
            return "Review requested, ready to inspect, and not blocked by checks or conflicts.";
        }
        return "Review requested, but the request is waiting on author, checks, or mergeability.";
    }

    private String focus(final PullRequest request) {
        if (request.draft()) {
            return "Confirm whether the draft is ready for review.";
        }
        if ("FAILING".equals(request.checksStatus())) {
            return "Check failing CI before spending time on implementation details.";
        }
        if ("CONFLICTING".equals(request.mergeable())) {
            return "Confirm the conflict scope and whether a useful review is possible.";
        }
        return "Review the change, checks, and recent comments.";
    }
}
