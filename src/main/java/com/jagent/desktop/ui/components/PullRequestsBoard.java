package com.jagent.desktop.ui.components;

import com.jagent.desktop.async.PullRequestInstanceState;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.PullRequestFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PullRequestsBoard extends JPanel {

    private static final Logger LOG = LoggerFactory.getLogger(PullRequestsBoard.class);
    private final transient ActionContext actionContext;
    private final Map<ProjectId, PullRequestInstanceState> pullRequestStates = new HashMap<>();
    private final RefreshButton refreshButton = new RefreshButton();
    private final JLabel refreshStatus;
    private final JComboBox<PullRequestFilter> filters;
    private final SearchInput query;
    private final JComponent loading = new LoadingPanel("Loading pull requests...");
    private final JPanel list = new JPanel();
    private final PullRequestSummaryPanel summary;
    private final JSplitPane splitPane;

    private transient List<PullRequestDetails> requests = List.of();
    private transient PullRequestDetails selectedRequest;
    private boolean refreshInFlight;
    private boolean refreshQueued;
    private String localFilter = "";
    private String currentQuery = "";

    public PullRequestsBoard(final ActionContext actionContext) {
        this(actionContext, null);
    }

    public PullRequestsBoard(final ActionContext actionContext, final ProjectId projectId) {
        super();
        this.actionContext = actionContext;
        if (projectId != null) {
            pullRequestStates.put(projectId, stateFor(projectId));
        } else {
            actionContext
                    .appState()
                    .projects()
                    .keySet()
                    .forEach(id -> pullRequestStates.put(id, stateFor(id)));
        }
        setLayout(new BorderLayout(0, UiConstants.CONTENT_PADDING));

        this.summary = new PullRequestSummaryPanel();

        final JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        filters =
                new JComboBox<>(
                        actionContext
                                .appState()
                                .appSettings()
                                .pullRequestFilters()
                                .toArray(PullRequestFilter[]::new));
        controls.add(new JLabel("Filter"));
        controls.add(filters);
        query =
                new SearchInput(
                        new SearchInput.Text(
                                "pull-request-query",
                                "GitHub pull request query",
                                "GitHub pull request query"));
        query.setColumns(32);
        query.setVisible(true);
        currentQuery = "";
        query.setText("");
        query.onSubmit(this::refresh);
        filters.addActionListener(
                event -> {
                    currentQuery = selectedFilter().query();
                    query.setText(currentQuery);
                    refresh();
                });
        controls.add(query);
        refreshStatus = new JLabel("Loading PRs...");
        refreshStatus.setFont(Theme.font(Theme.FontSize.SM));
        refreshButton.addActionListener(event -> this.refresh());
        controls.add(refreshButton);
        controls.add(refreshStatus);
        add(controls, BorderLayout.NORTH);
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(
                new EmptyBorder(
                        UiConstants.SPACING_XS,
                        UiConstants.SPACING_XS,
                        UiConstants.SPACING_XS,
                        UiConstants.SPACING_XS));
        final JScrollPane listScroll = new JScrollPane(list);
        listScroll.setOpaque(false);
        listScroll.getViewport().setOpaque(false);
        listScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        listScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        summary.setOpaque(false);
        summary.setLayout(new BorderLayout(0, UiConstants.SPACING_MD));
        summary.setBorder(
                new EmptyBorder(
                        UiConstants.SPACING_XS,
                        UiConstants.SPACING_XS,
                        UiConstants.SPACING_XS,
                        UiConstants.SPACING_XS));
        final JScrollPane summaryScroll = new JScrollPane(summary);
        summaryScroll.setOpaque(false);
        summaryScroll.getViewport().setOpaque(false);
        summaryScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        summaryScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, summaryScroll);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);
        splitPane.setContinuousLayout(true);
        splitPane.setResizeWeight(0.3);
        SwingUtilities.invokeLater(() -> splitPane.setDividerLocation(0.3));
        add(loading, BorderLayout.CENTER);
        refresh();
    }

    public void setFilter(final String filter) {
        localFilter = UiText.valueOrDefault(filter, "").trim().toLowerCase(Locale.ROOT);
        render();
    }

    public void setQuery(final String queryValue) {
        currentQuery = UiText.valueOrDefault(queryValue, "").trim();
        if (!Objects.equals(query.getText(), currentQuery)) {
            query.setText(currentQuery);
        }
    }

    private PullRequestFilter selectedFilter() {
        final PullRequestFilter selected = (PullRequestFilter) filters.getSelectedItem();
        if (selected != null) {
            return selected;
        }
        return new PullRequestFilter("All Open PRs", "");
    }

    public CompletableFuture<?> refresh() {
        return refresh(null);
    }

    public CompletableFuture<?> refresh(final ProjectId projectId) {
        if (refreshInFlight) {
            refreshQueued = true;
            refreshStatus.setText("Refreshing PRs... (queued)");
            return CompletableFuture.completedFuture(null);
        }
        currentQuery = UiText.valueOrDefault(query.getText(), "").trim();
        if (currentQuery.isBlank()) {
            refreshStatus.setText("Enter a pull request query");
            remove(loading);
            add(splitPane, BorderLayout.CENTER);
            requests = List.of();
            selectedRequest = null;
            render();
            revalidate();
            repaint();
            return CompletableFuture.completedFuture(null);
        }
        refreshInFlight = true;
        final long started = System.nanoTime();
        LOG.info(
                "PR board refresh started: projects={}",
                this.actionContext.appState().projects().size());
        refreshButton.setEnabled(false);
        refreshButton.start();
        refreshStatus.setText("Refreshing PRs...");
        if (splitPane.getParent() == null) {
            add(loading, BorderLayout.CENTER);
            revalidate();
            repaint();
        }
        if (pullRequestStates.isEmpty()) {
            completeRefresh("PRs refreshed", started);
            return CompletableFuture.completedFuture(null);
        }
        final List<Map.Entry<ProjectId, PullRequestInstanceState>> entries =
                pullRequestStates.entrySet().stream()
                        .filter(entry -> projectId == null || entry.getKey().equals(projectId))
                        .toList();
        final List<CompletableFuture<List<PullRequestDetails>>> loads =
                entries.stream()
                        .map(
                                entry ->
                                        entry.getValue()
                                                .fetch(
                                                        new PullRequestInstanceState.PullRequestKey(
                                                                entry.getKey(), currentQuery),
                                                        new PullRequestInstanceState.PullRequestKey(
                                                                entry.getKey(), currentQuery)))
                        .toList();
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new))
                .whenComplete(
                        (ignored, failure) -> {
                            completeRefresh(
                                    failure == null ? "PRs refreshed" : "PR refresh failed",
                                    started);
                        });
    }

    private PullRequestInstanceState stateFor(final ProjectId project) {
        return actionContext
                .viewController()
                .getOrDefaultAsyncLoadingState(
                        project,
                        new PullRequestInstanceState(
                                actionContext.viewController().backgroundJobs(),
                                actionContext.githubProvider()));
    }

    private void completeRefresh(final String status, final long started) {
        requests =
                pullRequestStates.entrySet().stream()
                        .map(
                                entry ->
                                        entry.getValue()
                                                .get(
                                                        new PullRequestInstanceState.PullRequestKey(
                                                                entry.getKey(), currentQuery)))
                        .filter(Optional::isPresent)
                        .flatMap(optional -> optional.get().stream())
                        .toList();
        refreshButton.setEnabled(true);
        refreshButton.stop();
        refreshStatus.setText(status);
        remove(loading);
        add(splitPane, BorderLayout.CENTER);
        if (requests.isEmpty()) {
            selectedRequest = null;
        }
        render();
        revalidate();
        repaint();
        LOG.debug(
                "PR board refresh completed: status={}, elapsedMs={}",
                status,
                (System.nanoTime() - started) / 1_000_000);
        refreshInFlight = false;
        if (refreshQueued) {
            refreshQueued = false;
            refresh();
        }
    }

    private void render() {
        final List<PullRequestDetails> requests =
                this.requests.stream()
                        .filter(
                                request ->
                                        localFilter.isBlank()
                                                || Integer.toString(request.number())
                                                        .contains(localFilter)
                                                || PullRequestPresentation.contains(
                                                        request.title(), localFilter)
                                                || PullRequestPresentation.contains(
                                                        request.author().login(), localFilter)
                                                || PullRequestPresentation.contains(
                                                        request.description(), localFilter)
                                                || PullRequestPresentation.contains(
                                                        request.headBranch(), localFilter))
                        .toList();
        if (selectedRequest == null && !requests.isEmpty()) {
            selectedRequest = requests.getFirst();
        }
        if (selectedRequest != null && !requests.contains(selectedRequest)) {
            selectedRequest = requests.isEmpty() ? null : requests.getFirst();
        }
        list.removeAll();
        final JLabel results = new JLabel("Results  " + requests.size());
        results.setFont(Theme.font(Theme.FontSize.LG));
        results.setAlignmentX(LEFT_ALIGNMENT);
        list.add(results);
        list.add(Box.createVerticalStrut(UiConstants.CONTENT_PADDING));
        if (requests.isEmpty()) {
            final JLabel empty = new JLabel("No pull requests match this filter.");
            empty.setFont(Theme.font(Theme.FontSize.MD));
            empty.setAlignmentX(LEFT_ALIGNMENT);
            empty.setForeground(Theme.Colors.muted());
            list.add(empty);
        }
        for (final PullRequestDetails request : requests) {
            final PullRequestCard card =
                    new PullRequestCard(actionContext, request, request, this::refresh);
            final JPanel cardRow = new JPanel(new BorderLayout());
            cardRow.setOpaque(false);
            cardRow.putClientProperty("pullRequest", request);
            cardRow.setMaximumSize(
                    new java.awt.Dimension(
                            Integer.MAX_VALUE,
                            UiConstants.PR_CARD_HEIGHT + UiConstants.SPACING_XS));
            cardRow.setBorder(
                    request.equals(selectedRequest)
                            ? BorderFactory.createCompoundBorder(
                                    BorderFactory.createLineBorder(Theme.Colors.focus(), 2),
                                    new EmptyBorder(0, 0, 0, 0))
                            : UiBorders.cardRow());
            cardRow.add(card, BorderLayout.CENTER);
            final Runnable onSelect =
                    () -> {
                        this.selectedRequest = request;
                        this.render();
                    };
            addSelectionListener(cardRow, onSelect);
            attachSelectionListeners(cardRow, onSelect);
            list.add(cardRow);
            list.add(Box.createVerticalStrut(UiConstants.CONTENT_PADDING));
        }
        summary.render(selectedRequest, selectedRequest);
        list.revalidate();
        list.repaint();
        summary.revalidate();
        summary.repaint();
    }

    private static void attachSelectionListeners(final Container parent, final Runnable onSelect) {
        for (final Component child : parent.getComponents()) {
            if (child instanceof JComponent childComponent && !(child instanceof AbstractButton)) {
                addSelectionListener(childComponent, onSelect);
            }
            if (child instanceof Container container) {
                attachSelectionListeners(container, onSelect);
            }
        }
    }

    private static void addSelectionListener(final JComponent component, final Runnable onSelect) {
        component.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(final MouseEvent event) {
                        if (SwingUtilities.isLeftMouseButton(event)) {
                            onSelect.run();
                        }
                    }
                });
    }
}
