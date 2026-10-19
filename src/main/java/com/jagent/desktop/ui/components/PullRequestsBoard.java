package com.jagent.desktop.ui.components;

import com.jagent.desktop.async.AsyncStateListener;
import com.jagent.desktop.async.PullRequestInstanceState;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequestDetails;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PullRequestsBoard extends JPanel {

    private static final Logger LOG = LoggerFactory.getLogger(PullRequestsBoard.class);
    private final transient ActionContext actionContext;
    private final transient Map<ProjectId, PullRequestInstanceState> pullRequestStates;
    private final JButton refreshButton;
    private final JLabel refreshStatus;
    private final transient RotatingIcon refreshIcon = new RotatingIcon(UiIcons.refresh());
    private final Timer refreshAnimation;
    private final SearchInput query;
    private final JComponent loading = new LoadingPanel("Loading pull requests...");
    private final JPanel list = new JPanel();
    private final PullRequestSummaryPanel summary;
    private final JScrollPane listScroll;
    private final JScrollPane summaryScroll;
    private final JSplitPane splitPane;

    private transient List<PullRequestDetails> requests = List.of();
    private transient PullRequestDetails selectedRequest;
    private boolean refreshInFlight;
    private boolean refreshQueued;
    private String localFilter = "";
    private String currentQuery = "";

    public PullRequestsBoard(final ActionContext actionContext, final String initialQuery) {
        this(actionContext, null, initialQuery);
    }

    public PullRequestsBoard(
            final ActionContext actionContext,
            final ProjectId projectId,
            final String initialQuery) {
        super();
        this.actionContext = actionContext;
        this.pullRequestStates = new java.util.HashMap<>();
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
        currentQuery = UiText.valueOrDefault(initialQuery, "").trim();

        final var parent = this;

        final JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        query =
                new SearchInput(
                        new SearchInput.Text(
                                "pull-request-query",
                                "GitHub pull request query",
                                "GitHub pull request query"));
        query.setColumns(32);
        query.setVisible(true);
        query.setText(currentQuery);
        query.onSubmit(parent::refresh);
        controls.add(query);
        refreshButton = new IconButton(refreshIcon, "Refresh pull requests");
        refreshStatus = new JLabel("Loading PRs...");
        refreshStatus.setFont(Theme.font(Theme.FontSize.SM));
        refreshButton.addActionListener(event -> parent.refresh());
        controls.add(refreshButton);
        controls.add(refreshStatus);
        refreshAnimation =
                new Timer(
                        75,
                        event -> {
                            refreshIcon.rotate();
                            refreshButton.repaint();
                        });
        add(controls, BorderLayout.NORTH);
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(
                new EmptyBorder(
                        UiConstants.SPACING_XS,
                        UiConstants.SPACING_XS,
                        UiConstants.SPACING_XS,
                        UiConstants.SPACING_XS));
        listScroll = new JScrollPane(list);
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
        summaryScroll = new JScrollPane(summary);
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

    public void refresh() {
        if (refreshInFlight) {
            refreshQueued = true;
            refreshStatus.setText("Refreshing PRs... (queued)");
            return;
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
            return;
        }
        refreshInFlight = true;
        final long started = System.nanoTime();
        LOG.info(
                "PR board refresh started: projects={}",
                this.actionContext.appState().projects().size());
        refreshButton.setEnabled(false);
        PullRequestBoardSupport.startRefreshAnimation(refreshIcon, refreshAnimation);
        refreshStatus.setText("Refreshing PRs...");
        if (splitPane.getParent() == null) {
            add(loading, BorderLayout.CENTER);
            revalidate();
            repaint();
        }
        if (pullRequestStates.isEmpty()) {
            completeRefresh(List.of(), "PRs refreshed", started);
            return;
        }
        for (final Map.Entry<ProjectId, PullRequestInstanceState> entry :
                pullRequestStates.entrySet()) {
            final PullRequestInstanceState state = entry.getValue();
            final PullRequestInstanceState.PullRequestKey key =
                    new PullRequestInstanceState.PullRequestKey(entry.getKey(), currentQuery);
            state.setListener(
                    key,
                    new AsyncStateListener<>() {
                        @Override
                        public void onComplete(final List<PullRequestDetails> ignored) {
                            completeRefresh(loadCachedRequests(), "PRs refreshed", started);
                        }

                        @Override
                        public void onFailure(final Throwable failure) {
                            LOG.warn("PR board load failed", failure);
                            completeRefresh(loadCachedRequests(), "PR refresh failed", started);
                        }
                    });
            state.fetch(key, key);
        }
    }

    private PullRequestInstanceState stateFor(final ProjectId project) {
        return actionContext
                .viewCoordinator()
                .getOrDefaultAsyncLoadingState(
                        project,
                        new PullRequestInstanceState(
                                actionContext.appState(),
                                actionContext.viewCoordinator().backgroundJobs()));
    }

    private List<PullRequestDetails> loadCachedRequests() {
        return pullRequestStates.entrySet().stream()
                .map(
                        entry ->
                                entry.getValue()
                                        .get(
                                                new PullRequestInstanceState.PullRequestKey(
                                                        entry.getKey(), currentQuery)))
                .filter(Optional::isPresent)
                .flatMap(optional -> optional.get().stream())
                .toList();
    }

    private void completeRefresh(
            final List<PullRequestDetails> loaded, final String status, final long started) {
        requests = loaded;
        refreshButton.setEnabled(true);
        PullRequestBoardSupport.stopRefreshAnimation(refreshIcon, refreshAnimation, refreshButton);
        refreshStatus.setText(status);
        remove(loading);
        add(splitPane, BorderLayout.CENTER);
        if (loaded.isEmpty()) {
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
            PullRequestBoardSupport.registerSelectionClick(
                    cardRow,
                    request,
                    selected -> {
                        selectedRequest =
                                requests.stream()
                                        .filter(detail -> detail.equals(selected))
                                        .findFirst()
                                        .orElse(null);
                        render();
                    });
            list.add(cardRow);
            list.add(Box.createVerticalStrut(UiConstants.CONTENT_PADDING));
        }
        summary.render(selectedRequest, selectedRequest);
        list.revalidate();
        list.repaint();
        summary.revalidate();
        summary.repaint();
    }
}
