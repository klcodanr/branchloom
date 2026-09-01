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
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class PullRequestsBoard extends JPanel {
    private static final Logger LOG = Logger.getLogger(PullRequestsBoard.class.getName());
    private final transient ActionContext actionContext;
    private final transient Supplier<List<PullRequest>> onRefresh;
    private final JPanel board = new JPanel(new GridLayout(1, 0, 14, 0));
    private final JButton refreshButton;
    private final JLabel refreshStatus;
    private final JComponent loading = UiFactory.loading("Loading pull requests...");
    private final JScrollPane scroll;

    private transient List<PullRequest> requests = List.of();
    private String filter = "";

    public PullRequestsBoard(
            final ActionContext actionContext, final Supplier<List<PullRequest>> onRefresh) {
        super();
        this.actionContext = actionContext;
        setLayout(new BorderLayout());
        this.onRefresh = onRefresh;

        final var parent = this;

        final JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        final JLabel status = UiFactory.label("All projects", Theme.FontSize.SM);
        controls.add(status);
        final JTextField search = new JTextField(20);
        search.setToolTipText("Filter pull requests by number, title, author, or branch");
        controls.add(search);
        refreshButton = UiFactory.iconButton(UiIcons.refresh());
        refreshButton.setToolTipText("Refresh pull requests");
        refreshButton.getAccessibleContext().setAccessibleName("Refresh pull requests");
        refreshStatus = UiFactory.label("Loading PRs...", Theme.FontSize.SM);
        refreshButton.addActionListener(event -> parent.refresh());
        controls.add(refreshButton);
        controls.add(refreshStatus);
        search.getDocument()
                .addDocumentListener(
                        new DocumentListener() {
                            private void update() {
                                parent.setFilter(search.getText());
                            }

                            @Override
                            public void insertUpdate(DocumentEvent event) {
                                update();
                            }

                            @Override
                            public void removeUpdate(DocumentEvent event) {
                                update();
                            }

                            @Override
                            public void changedUpdate(DocumentEvent event) {
                                update();
                            }
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

    private void refresh() {
        refreshButton.setEnabled(false);
        remove(scroll);
        add(loading, BorderLayout.CENTER);
        revalidate();
        repaint();
        if (this.actionContext.appState().projects().isEmpty()) {
            refreshButton.setEnabled(true);
            remove(loading);
            add(scroll, BorderLayout.CENTER);
            return;
        }
        refreshStatus.setText("Refreshing PRs...");
        BackgroundTasks.submit(
                        "Pull Requests",
                        "pull-request-cache-refresh",
                        () -> {
                            this.requests = onRefresh.get();
                            return null;
                        })
                .thenRunAsync(
                        () -> {
                            refreshButton.setEnabled(true);
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
                                        refreshStatus.setText("PR refresh failed");
                                        LOG.log(Level.WARNING, "Pull request refresh", failure);
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
        for (final String group :
                new String[] {"Not Ready", "Waiting for Changes", "Ready For Review", "Approved"}) {
            final JPanel column = UiFactory.panel();
            column.setPreferredSize(new Dimension(240, 0));
            column.setBorder(new EmptyBorder(8, 8, 8, 8));
            column.setLayout(new BorderLayout(0, 6));
            final List<PullRequest> items =
                    requests.stream()
                            .filter(request -> group.equals(request.relevanceGroup()))
                            .toList();
            column.add(
                    UiFactory.label(group + "  " + items.size(), Theme.FontSize.LG),
                    BorderLayout.NORTH);
            final JPanel cards = new JPanel();
            cards.setOpaque(false);
            cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
            cards.setBorder(new EmptyBorder(4, 4, 4, 4));
            for (final PullRequest request : items) {
                cards.add(new PullRequestCard(actionContext, request));
                cards.add(Box.createVerticalStrut(8));
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
}
