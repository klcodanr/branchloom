package com.jagent.desktop.ui.components;

import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.services.BackgroundTasks;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

public final class PullRequestsBoard extends JPanel {
    private static final Logger LOG = Logger.getLogger(PullRequestsBoard.class.getName());
    private final transient ActionContext actionContext;
    private final transient Supplier<List<PullRequest>> onRefresh;
    private final JPanel board = new JPanel(new GridLayout(1, 0, 14, 0));
    private final JButton refreshButton;
    private final JLabel refreshStatus;
    private final transient RotatingIcon refreshIcon = new RotatingIcon(UiIcons.refresh());
    private final Timer refreshAnimation;
    private final SearchInput search;
    private final JComponent loading = UiFactory.loading("Loading pull requests...");
    private final JScrollPane scroll;

    private transient List<PullRequest> requests = List.of();
    private String filter = "";

    public PullRequestsBoard(
            final ActionContext actionContext, final Supplier<List<PullRequest>> onRefresh) {
        super();
        this.actionContext = actionContext;
        setLayout(new BorderLayout(0, UiConstants.CONTENT_PADDING));
        this.onRefresh = onRefresh;

        final var parent = this;

        final JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        final JLabel status = UiFactory.label("All projects", Theme.FontSize.SM);
        controls.add(status);
        search =
                new SearchInput(
                        new SearchInput.Text(
                                "pull-request-search",
                                "Filter pull requests by number, title, author, or branch",
                                "Filter pull requests by number, title, author, or branch"));
        search.setVisible(true);
        search.onChange(parent::setFilter);
        search.onCancel(() -> search.setText(""));
        controls.add(search);
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
        board.setOpaque(false);
        scroll = new JScrollPane(board);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        add(loading, BorderLayout.CENTER);
        refresh();
    }

    public void setFilter(final String filter) {
        this.filter = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        render();
    }

    public boolean focusSearch() {
        return search.requestFocusInWindow();
    }

    public void refresh() {
        final long started = System.nanoTime();
        LOG.info(
                () ->
                        "PR board refresh started: projects="
                                + this.actionContext.appState().projects().size());
        refreshButton.setEnabled(false);
        startRefreshAnimation();
        refreshStatus.setText("Refreshing PRs...");
        if (scroll.getParent() == null) {
            add(loading, BorderLayout.CENTER);
            revalidate();
            repaint();
        }
        if (this.actionContext.appState().projects().isEmpty()) {
            refreshButton.setEnabled(true);
            stopRefreshAnimation();
            remove(loading);
            add(scroll, BorderLayout.CENTER);
            return;
        }
        BackgroundTasks.submit(
                        "Pull Requests",
                        "pull-request-cache-refresh",
                        () -> {
                            this.requests = onRefresh.get();
                            LOG.info(
                                    () ->
                                            "PR board load finished: count="
                                                    + this.requests.size()
                                                    + ", elapsedMs="
                                                    + (System.nanoTime() - started) / 1_000_000);
                            return null;
                        })
                .thenRunAsync(
                        () -> {
                            refreshButton.setEnabled(true);
                            stopRefreshAnimation();
                            refreshStatus.setText("PRs refreshed");
                            remove(loading);
                            add(scroll, BorderLayout.CENTER);
                            render();
                            revalidate();
                            repaint();
                        },
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
                                        remove(loading);
                                        add(scroll, BorderLayout.CENTER);
                                        revalidate();
                                        repaint();
                                    });
                            return null;
                        });
    }

    private void render() {
        final List<PullRequest> requests =
                this.requests.stream()
                        .filter(
                                request ->
                                        filter.isBlank()
                                                || Integer.toString(request.number())
                                                        .contains(filter)
                                                || contains(request.title(), filter)
                                                || contains(request.author(), filter)
                                                || contains(request.headBranch(), filter))
                        .toList();
        board.removeAll();
        for (final String group : groups()) {
            final JPanel column = UiFactory.panel();
            column.setPreferredSize(new Dimension(240, 0));
            column.setBorder(UiFactory.cardBorder());
            column.setLayout(new BorderLayout(0, UiConstants.CONTENT_PADDING));
            final List<PullRequest> items =
                    requests.stream().filter(request -> group.equals(group(request))).toList();
            column.add(
                    UiFactory.label(group + "  " + items.size(), Theme.FontSize.LG),
                    BorderLayout.NORTH);
            final JPanel cards = new JPanel();
            cards.setOpaque(false);
            cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
            cards.setBorder(
                    new EmptyBorder(
                            UiConstants.SPACING_XS,
                            UiConstants.SPACING_XS,
                            UiConstants.SPACING_XS,
                            UiConstants.SPACING_XS));
            for (final PullRequest request : items) {
                cards.add(new PullRequestCard(actionContext, request));
                cards.add(Box.createVerticalStrut(UiConstants.CONTENT_PADDING));
            }
            final JScrollPane cardScroll = new JScrollPane(cards);
            cardScroll.setOpaque(false);
            cardScroll.getViewport().setOpaque(false);
            cardScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            column.add(cardScroll, BorderLayout.CENTER);
            board.add(column);
        }
        board.revalidate();
        board.repaint();
    }

    private static boolean contains(final String value, final String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private String[] groups() {
        return new String[] {"Not Ready", "Waiting for Changes", "Ready For Review", "Approved"};
    }

    private String group(final PullRequest request) {
        return request.relevanceGroup();
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
