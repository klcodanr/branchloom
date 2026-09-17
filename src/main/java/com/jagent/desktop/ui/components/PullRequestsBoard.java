package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.services.BackgroundTasks;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
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

public final class PullRequestsBoard extends JPanel {
    private static final Logger LOG = Logger.getLogger(PullRequestsBoard.class.getName());
    private final transient ActionContext actionContext;
    private final transient Function<String, List<PullRequest>> onRefresh;
    private final JButton refreshButton;
    private final JLabel refreshStatus;
    private final transient RotatingIcon refreshIcon = new RotatingIcon(UiIcons.refresh());
    private final Timer refreshAnimation;
    private final SearchInput query;
    private final JComponent loading = UiFactory.loading("Loading pull requests...");
    private final JPanel list = new JPanel();
    private final PullRequestSummaryPanel summary = new PullRequestSummaryPanel();
    private final JScrollPane listScroll;
    private final JScrollPane summaryScroll;
    private final JSplitPane splitPane;

    private transient List<PullRequest> requests = List.of();
    private transient PullRequest selectedRequest;
    private boolean refreshInFlight;
    private boolean refreshQueued;
    private String localFilter = "";
    private String currentQuery;

    public PullRequestsBoard(
            final ActionContext actionContext, final Supplier<List<PullRequest>> onRefresh) {
        this(actionContext, "", ignored -> onRefresh.get());
    }

    public PullRequestsBoard(
            final ActionContext actionContext,
            final String initialQuery,
            final Function<String, List<PullRequest>> onRefresh) {
        super();
        this.actionContext = actionContext;
        setLayout(new BorderLayout(0, UiConstants.CONTENT_PADDING));
        this.onRefresh = onRefresh;
        currentQuery = initialQuery == null ? "" : initialQuery.trim();

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
        refresh();
    }

    public void setFilter(final String filter) {
        localFilter = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        render();
    }

    public void setQuery(final String queryValue) {
        currentQuery = queryValue == null ? "" : queryValue.trim();
        if (!Objects.equals(query.getText(), currentQuery)) {
            query.setText(currentQuery);
        }
    }

    public boolean focusSearch() {
        return query.requestFocusInWindow();
    }

    public void refresh() {
        if (refreshInFlight) {
            refreshQueued = true;
            refreshStatus.setText("Refreshing PRs... (queued)");
            return;
        }
        currentQuery = query.getText() == null ? "" : query.getText().trim();
        refreshInFlight = true;
        final long started = System.nanoTime();
        LOG.info(
                () ->
                        "PR board refresh started: projects="
                                + this.actionContext.appState().projects().size());
        refreshButton.setEnabled(false);
        startRefreshAnimation();
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
        BackgroundTasks.submit(
                        "Pull Requests",
                        "pull-request-cache-refresh",
                        () -> {
                            final List<PullRequest> loaded = onRefresh.apply(currentQuery);
                            LOG.info(
                                    () ->
                                            "PR board load finished: count="
                                                    + loaded.size()
                                                    + ", query="
                                                    + currentQuery
                                                    + ", elapsedMs="
                                                    + (System.nanoTime() - started) / 1_000_000);
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
                                        stopRefreshAnimation();
                                        refreshStatus.setText("PR refresh failed");
                                        LOG.log(
                                                Level.WARNING,
                                                "PR board load failed after "
                                                        + (System.nanoTime() - started) / 1_000_000
                                                        + "ms",
                                                failure);
                                        completeRefresh(requests, "PR refresh failed", started);
                                    });
                            return null;
                        });
    }

    private void completeRefresh(
            final List<PullRequest> loaded, final String status, final long started) {
        requests = loaded;
        refreshButton.setEnabled(true);
        stopRefreshAnimation();
        refreshStatus.setText(status);
        remove(loading);
        add(splitPane, BorderLayout.CENTER);
        if (loaded.isEmpty()) {
            selectedRequest = null;
        }
        render();
        revalidate();
        repaint();
        LOG.fine(
                () ->
                        "PR board refresh completed: status="
                                + status
                                + ", elapsedMs="
                                + (System.nanoTime() - started) / 1_000_000);
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
                                                || contains(request.title(), localFilter)
                                                || contains(request.author(), localFilter)
                                                || contains(request.headBranch(), localFilter))
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
            final PullRequestCard card = new PullRequestCard(actionContext, request, this::refresh);
            final JPanel cardRow = new JPanel(new BorderLayout());
            cardRow.setOpaque(false);
            cardRow.setMaximumSize(
                    new java.awt.Dimension(
                            Integer.MAX_VALUE,
                            UiConstants.PR_CARD_HEIGHT + UiConstants.SPACING_XS));
            cardRow.setBorder(
                    request.equals(selectedRequest)
                            ? BorderFactory.createCompoundBorder(
                                    BorderFactory.createLineBorder(selectionColor(), 2),
                                    new EmptyBorder(0, 0, 0, 0))
                            : new EmptyBorder(2, 2, 2, 2));
            cardRow.add(card, BorderLayout.CENTER);
            registerSelectionClick(cardRow, request);
            list.add(cardRow);
            list.add(Box.createVerticalStrut(UiConstants.CONTENT_PADDING));
        }
        summary.render(selectedRequest);
        list.revalidate();
        list.repaint();
        summary.revalidate();
        summary.repaint();
    }

    private void registerSelectionClick(final JComponent component, final PullRequest request) {
        component.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(final MouseEvent event) {
                        if (SwingUtilities.isLeftMouseButton(event)) {
                            selectedRequest = request;
                            render();
                        }
                    }
                });
        for (final java.awt.Component child : component.getComponents()) {
            if (child instanceof JComponent nested) {
                registerSelectionClick(nested, request);
            }
        }
    }

    private static Color selectionColor() {
        final Color color = UIManager.getColor("Component.focusColor");
        if (color != null) {
            return color;
        }
        final Color borderColor = UIManager.getColor("Component.borderColor");
        return borderColor == null ? Color.GRAY : borderColor;
    }

    private static boolean contains(final String value, final String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private void startRefreshAnimation() {
        refreshIcon.reset();
        refreshAnimation.start();
    }

    private void stopRefreshAnimation() {
        refreshAnimation.stop();
        refreshIcon.reset();
        refreshButton.repaint();
    }
}
