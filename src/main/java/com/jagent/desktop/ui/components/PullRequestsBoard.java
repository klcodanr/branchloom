package com.jagent.desktop.ui.components;

import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.services.github.GitHub;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
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
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PullRequestsBoard extends JPanel {

    private static final Logger LOG = LoggerFactory.getLogger(PullRequestsBoard.class);
    private final transient ActionContext actionContext;
    private final transient BiFunction<String, Boolean, List<PullRequest>> onRefresh;
    private final JButton refreshButton;
    private final JLabel refreshStatus;
    private final transient RotatingIcon refreshIcon = new RotatingIcon(UiIcons.refresh());
    private final Timer refreshAnimation;
    private final SearchInput query;
    private final JComponent loading = UiFactory.loading("Loading pull requests...");
    private final JPanel list = new JPanel();
    private final PullRequestSummaryPanel summary;
    private final JScrollPane listScroll;
    private final JScrollPane summaryScroll;
    private final JSplitPane splitPane;

    private transient List<PullRequest> requests = List.of();
    private transient PullRequest selectedRequest;
    private final transient Map<String, PullRequestDetails> detailsByKey =
            new ConcurrentHashMap<>();
    private final transient Set<String> loadedDetailKeys = new HashSet<>();
    private final transient Set<String> loadingDetailKeys = new HashSet<>();
    private boolean refreshInFlight;
    private boolean refreshQueued;
    private String localFilter = "";
    private String currentQuery = "";

    public PullRequestsBoard(
            final ActionContext actionContext,
            final BiFunction<String, Boolean, List<PullRequest>> onRefresh) {
        super();
        this.actionContext = actionContext;
        setLayout(new BorderLayout(0, UiConstants.CONTENT_PADDING));
        this.onRefresh = onRefresh;
        this.summary = new PullRequestSummaryPanel(actionContext.appState());

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
        refreshButton = UiFactory.iconButton(refreshIcon, "Refresh pull requests");
        refreshStatus = UiFactory.label("Loading PRs...", Theme.FontSize.SM);
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
        listScroll
                .getViewport()
                .addChangeListener(
                        event ->
                                PullRequestBoardSupport.loadVisibleDetails(
                                        list, listScroll, this::ensureDetailsLoaded));
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
        summaryScroll.setHorizontalScrollBarPolicy(
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        summaryScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, summaryScroll);
        splitPane.setBorder(null);
        splitPane.setOpaque(false);
        splitPane.setContinuousLayout(true);
        splitPane.setResizeWeight(0.3);
        SwingUtilities.invokeLater(() -> splitPane.setDividerLocation(0.3));
        add(loading, BorderLayout.CENTER);
        refresh(false);
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
        refresh(true);
    }

    private void refresh(final boolean forceRefresh) {
        if (refreshInFlight) {
            refreshQueued = true;
            refreshStatus.setText("Refreshing PRs... (queued)");
            return;
        }
        currentQuery = UiText.valueOrDefault(query.getText(), "").trim();
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
        if (this.actionContext.appState().projects().isEmpty()) {
            completeRefresh(List.of(), "PRs refreshed", started);
            return;
        }
        BackgroundOperations.submit(
                        "Pull Requests",
                        "pull-request-cache-refresh",
                        () -> {
                            final List<PullRequest> loaded =
                                    onRefresh.apply(currentQuery, forceRefresh);
                            LOG.info(
                                    "PR board load finished: count={}, query={}, elapsedMs={}",
                                    loaded.size(),
                                    currentQuery,
                                    (System.nanoTime() - started) / 1_000_000);
                            return loaded;
                        })
                .thenAcceptAsync(
                        loaded -> completeRefresh(loaded, "PRs refreshed", started),
                        SwingUtilities::invokeLater)
                .exceptionally(
                        failure -> {
                            SwingUtilities.invokeLater(
                                    () -> {
                                        refreshButton.setEnabled(true);
                                        PullRequestBoardSupport.stopRefreshAnimation(
                                                refreshIcon, refreshAnimation, refreshButton);
                                        refreshStatus.setText("PR refresh failed");
                                        LOG.warn(
                                                "PR board load failed after {}ms",
                                                (System.nanoTime() - started) / 1_000_000,
                                                failure);
                                        completeRefresh(requests, "PR refresh failed", started);
                                    });
                            return null;
                        });
    }

    private void completeRefresh(
            final List<PullRequest> loaded, final String status, final long started) {
        requests = loaded;
        loadedDetailKeys.clear();
        loadingDetailKeys.clear();
        detailsByKey.clear();
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
        final List<PullRequest> requests =
                this.requests.stream()
                        .filter(
                                request ->
                                        localFilter.isBlank()
                                                || Integer.toString(request.number())
                                                        .contains(localFilter)
                                                || PullRequestPresentation.contains(
                                                        request.title(), localFilter)
                                                || PullRequestPresentation.contains(
                                                        PullRequestPresentation.authorLogin(
                                                                request),
                                                        localFilter)
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
        final JLabel results = UiFactory.label("Results  " + requests.size(), Theme.FontSize.LG);
        results.setAlignmentX(LEFT_ALIGNMENT);
        list.add(results);
        list.add(Box.createVerticalStrut(UiConstants.CONTENT_PADDING));
        if (requests.isEmpty()) {
            final JLabel empty =
                    UiFactory.label("No pull requests match this filter.", Theme.FontSize.MD);
            empty.setAlignmentX(LEFT_ALIGNMENT);
            empty.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
            list.add(empty);
        }
        for (final PullRequest request : requests) {
            final PullRequestDetails details =
                    detailsByKey.get(PullRequestPresentation.detailKey(request));
            final PullRequestCard card =
                    new PullRequestCard(actionContext, request, details, this::refresh);
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
                                    BorderFactory.createLineBorder(
                                            PullRequestPresentation.selectionColor(), 2),
                                    new EmptyBorder(0, 0, 0, 0))
                            : new EmptyBorder(2, 2, 2, 2));
            cardRow.add(card, BorderLayout.CENTER);
            PullRequestBoardSupport.registerSelectionClick(
                    cardRow,
                    request,
                    selected -> {
                        selectedRequest = selected;
                        render();
                    });
            list.add(cardRow);
            list.add(Box.createVerticalStrut(UiConstants.CONTENT_PADDING));
        }
        summary.render(
                selectedRequest,
                detailsByKey.get(PullRequestPresentation.detailKey(selectedRequest)));
        PullRequestBoardSupport.loadSelectedDetails(selectedRequest, this::ensureDetailsLoaded);
        SwingUtilities.invokeLater(
                () ->
                        PullRequestBoardSupport.loadVisibleDetails(
                                list, listScroll, this::ensureDetailsLoaded));
        list.revalidate();
        list.repaint();
        summary.revalidate();
        summary.repaint();
    }

    private PullRequestDetails loadDetails(final PullRequest request) {
        final var project = actionContext.appState().projects().get(request.projectId());
        if (project == null) {
            return new PullRequestDetails(
                    request.projectId(),
                    request.project(),
                    request.number(),
                    false,
                    false,
                    "",
                    0,
                    0,
                    0);
        }
        try {
            return GitHub.forProject(this.actionContext.appState(), request.projectId())
                    .getPullRequestDetails(request);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PR detail loading was interrupted", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not load PR details", exception);
        }
    }

    private void ensureDetailsLoaded(final PullRequest request) {
        final String detailKey = PullRequestPresentation.detailKey(request);
        if (loadedDetailKeys.contains(detailKey) || loadingDetailKeys.contains(detailKey)) {
            return;
        }
        loadingDetailKeys.add(detailKey);
        BackgroundOperations.submit("Pull Requests", "load-pr-details", () -> loadDetails(request))
                .thenAcceptAsync(
                        details -> {
                            loadedDetailKeys.add(detailKey);
                            loadingDetailKeys.remove(detailKey);
                            detailsByKey.put(detailKey, details);
                            if (request.equals(selectedRequest)
                                    || PullRequestBoardSupport.isVisible(
                                            request, list, listScroll)) {
                                render();
                            }
                        },
                        SwingUtilities::invokeLater)
                .exceptionally(
                        failure -> {
                            SwingUtilities.invokeLater(() -> loadingDetailKeys.remove(detailKey));
                            return null;
                        });
    }
}
