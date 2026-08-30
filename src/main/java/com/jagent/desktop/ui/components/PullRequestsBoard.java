package com.jagent.desktop.ui.components;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.services.BackgroundTasks;
import com.jagent.desktop.services.Git;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.actions.ImportBranchAction;
import com.jagent.desktop.ui.dialogs.ReviewDialog;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
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
                    SwingUtilities.invokeLater(
                            () -> {
                                refreshButton.setEnabled(true);
                                refreshStatus.setText("PRs refreshed");
                                remove(loading);
                                add(scroll, BorderLayout.CENTER);
                                render();
                                revalidate();
                                repaint();
                            });
                },
                failure ->
                        SwingUtilities.invokeLater(
                                () -> {
                                    refreshButton.setEnabled(true);
                                    refreshStatus.setText("PR refresh failed");
                                    LOG.log(Level.WARNING, "Pull request refresh", failure);
                                    remove(loading);
                                    add(scroll, BorderLayout.CENTER);
                                    revalidate();
                                    repaint();
                                }));
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
                cards.add(card(request));
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

    private JPanel card(final PullRequest request) {
        final JPanel card = UiFactory.panel();
        final JPopupMenu contextMenu = menu(request);
        card.setBackground(UIManager.getColor("TextField.background"));
        card.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
                        new EmptyBorder(7, 7, 7, 7)));
        card.setPreferredSize(new Dimension(UiConstants.PR_CARD_WIDTH, UiConstants.PR_CARD_HEIGHT));
        card.setMinimumSize(new Dimension(UiConstants.PR_CARD_WIDTH, UiConstants.PR_CARD_HEIGHT));
        card.setMaximumSize(new Dimension(UiConstants.PR_CARD_WIDTH, UiConstants.PR_CARD_HEIGHT));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setAlignmentX(LEFT_ALIGNMENT);
        final JLabel number = UiFactory.label("#" + request.number(), Theme.FontSize.XS);
        number.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
        number.setFont(Theme.boldFont(Theme.FontSize.XS));
        number.setAlignmentX(LEFT_ALIGNMENT);
        number.setComponentPopupMenu(contextMenu);
        card.add(number);
        final JTextArea title = new JTextArea(request.title());
        title.setLineWrap(true);
        title.setWrapStyleWord(true);
        title.setEditable(false);
        title.setFocusable(false);
        title.setOpaque(false);
        title.setBorder(null);
        title.setMargin(new Insets(0, 0, 0, 0));
        title.setAlignmentX(LEFT_ALIGNMENT);
        title.setFont(Theme.boldFont(Theme.FontSize.MD));
        title.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        title.setComponentPopupMenu(contextMenu);
        title.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mouseClicked(final MouseEvent event) {
                        if (event.getButton() == MouseEvent.BUTTON1) {
                            open(request.url());
                        }
                    }
                });
        card.add(title);
        final JComponent statusDot =
                StatusDots.create(
                        UiText.checksColor(request.checksStatus()), checksSummary(request));
        final JPanel statusRow = new JPanel();
        statusRow.setOpaque(false);
        statusRow.setLayout(new BoxLayout(statusRow, BoxLayout.X_AXIS));
        statusRow.setAlignmentX(LEFT_ALIGNMENT);
        final JLabel metadata =
                UiFactory.label(
                        "@"
                                + request.author()
                                + "  "
                                + reviewStatus(request)
                                + "  ·  "
                                + mergeStatus(request),
                        Theme.FontSize.XS);
        metadata.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
        statusRow.add(statusDot);
        statusRow.add(Box.createHorizontalStrut(5));
        statusRow.add(metadata);
        statusRow.setComponentPopupMenu(contextMenu);
        card.add(statusRow);
        card.setComponentPopupMenu(contextMenu);
        return card;
    }

    private JPopupMenu menu(final PullRequest request) {
        final JPopupMenu menu = new JPopupMenu();
        final JMenuItem open = new JMenuItem("Open PR");
        open.addActionListener(event -> open(request.url()));
        final JMenuItem importItem = new JMenuItem("Import PR branch");
        importItem.addActionListener(
                event -> ImportBranchAction.importPullRequest(actionContext, request));
        menu.add(open);
        menu.addSeparator();
        menu.add(importItem);
        final JMenuItem reviewItem = new JMenuItem("Review PR");
        reviewItem.addActionListener(event -> startReview(request));
        menu.add(reviewItem);
        return menu;
    }

    private void startReview(final PullRequest request) {
        if (actionContext.appState().appSettings().agents().isEmpty()) {
            LOG.warning("Review PR: No agents configured");
            return;
        }
        new ReviewDialog(
                        actionContext,
                        request,
                        (agent, prompt) -> {
                            final String title = agent.name + " review #" + request.number();
                            final String command =
                                    agent.newSessionCommand.replace(
                                            "{prompt}", Git.shellQuote(prompt));
                            final var terminalId =
                                    actionContext
                                            .appState()
                                            .addTerminal(
                                                    new Terminal(
                                                            null,
                                                            request.projectId(),
                                                            title,
                                                            command));
                            actionContext
                                    .viewCoordinator()
                                    .updateView(
                                            ViewId.PROJECT,
                                            ViewCoordinator.ViewState.projectTerminal(
                                                    request.projectId(), terminalId));
                        })
                .setVisible(true);
    }

    private static void open(final String url) {
        try {
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception exception) {
            LOG.log(Level.SEVERE, "Open pull request", exception);
        }
    }

    private static String reviewStatus(final PullRequest request) {
        if ("CONFLICTING".equals(request.mergeable())) {
            return "Conflicting";
        }
        if ("APPROVED".equals(request.reviewDecision())) {
            return "Approved";
        }
        if ("CHANGES_REQUESTED".equals(request.reviewDecision())) {
            return "Changes requested";
        }
        if (request.draft()) {
            return "Draft";
        }
        return "Ready for review";
    }

    private static String mergeStatus(final PullRequest request) {
        if ("MERGEABLE".equals(request.mergeable())) {
            return "Can merge";
        }
        if ("CONFLICTING".equals(request.mergeable())) {
            return "Cannot merge";
        }
        return "Mergeability unknown";
    }

    private static String checksSummary(final PullRequest request) {
        return request.checksPassed()
                + "/"
                + request.checksTotal()
                + " checks "
                + UiText.titleCase(request.checksStatus());
    }
}
