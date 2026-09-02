package com.jagent.desktop.ui.components;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.actions.ImportBranchAction;
import com.jagent.desktop.ui.dialogs.ReviewDialog;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

/** Displays one pull request and its actions. */
public final class PullRequestCard extends JPanel {
    private static final Logger LOG = Logger.getLogger(PullRequestCard.class.getName());
    private final transient ActionContext actionContext;

    public PullRequestCard(final ActionContext actionContext, final PullRequest request) {
        super();
        this.actionContext = actionContext;
        final JPopupMenu contextMenu = menu(request);
        setBackground(UIManager.getColor("TextField.background"));
        setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
                        new EmptyBorder(7, 7, 7, 7)));
        setPreferredSize(new Dimension(UiConstants.PR_CARD_WIDTH, UiConstants.PR_CARD_HEIGHT));
        setMinimumSize(new Dimension(UiConstants.PR_CARD_WIDTH, UiConstants.PR_CARD_HEIGHT));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, UiConstants.PR_CARD_HEIGHT));
        setAlignmentX(LEFT_ALIGNMENT);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        final JLabel number = UiFactory.label("#" + request.number(), Theme.FontSize.XS);
        number.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
        number.setFont(Theme.boldFont(Theme.FontSize.XS));
        number.setAlignmentX(LEFT_ALIGNMENT);
        number.setComponentPopupMenu(contextMenu);
        add(number);
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
                            PlatformCommands.openUrl(request.url());
                        }
                    }
                });
        add(title);
        final JComponent statusDot =
                new StatusDot(UiText.checksColor(request.checksStatus()), checksSummary(request));
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
        add(statusRow);
        setComponentPopupMenu(contextMenu);
    }

    private JPopupMenu menu(final PullRequest request) {
        final JPopupMenu menu = new JPopupMenu();
        final JMenuItem open = new JMenuItem("Open PR");
        open.addActionListener(event -> PlatformCommands.openUrl(request.url()));
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
                                            "{prompt}", PlatformCommands.shellQuote(prompt));
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
