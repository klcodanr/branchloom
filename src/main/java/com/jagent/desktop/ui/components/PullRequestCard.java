package com.jagent.desktop.ui.components;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.async.BackgroundOperations;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.services.GitHubPullRequest;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.actions.CopyPathAction;
import com.jagent.desktop.ui.actions.ImportBranchAction;
import com.jagent.desktop.ui.dialogs.ReviewDialog;
import com.jagent.desktop.ui.utils.RelativeTime;
import java.awt.Dimension;
import java.util.Date;
import java.util.concurrent.CompletionException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.UIManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Displays one pull request and its actions. */
public final class PullRequestCard extends JPanel {
    private static final Logger LOG = LoggerFactory.getLogger(PullRequestCard.class);
    private static final String PULL_REQUEST_ACTION = "pull-request-action";
    private final transient ActionContext actionContext;
    private final transient Runnable onPullRequestApproved;
    private final transient PullRequestDetails details;
    private final JLabel metadata;

    public PullRequestCard(final ActionContext actionContext, final PullRequest request) {
        this(actionContext, request, null, () -> {});
    }

    public PullRequestCard(
            final ActionContext actionContext,
            final PullRequest request,
            final PullRequestDetails details,
            final Runnable onPullRequestApproved) {
        super();
        this.actionContext = actionContext;
        this.onPullRequestApproved = onPullRequestApproved;
        this.details = details;
        final JPopupMenu contextMenu = menu(request);
        setBackground(UIManager.getColor("TextField.background"));
        setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
                        UiFactory.cardBorder()));
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
        final JLabel title = UiFactory.label(request.title(), Theme.FontSize.MD);
        title.setAlignmentX(LEFT_ALIGNMENT);
        title.setToolTipText(request.title());
        title.setFont(Theme.boldFont(Theme.FontSize.MD));
        title.setForeground(UIManager.getColor("Label.foreground"));
        title.setComponentPopupMenu(contextMenu);
        add(title);
        final JComponent statusDot =
                new StatusDot(details == null ? Theme.mutedColor() : details.indicatorColor());
        final JPanel statusRow = new JPanel();
        statusRow.setOpaque(false);
        statusRow.setLayout(new BoxLayout(statusRow, BoxLayout.X_AXIS));
        statusRow.setAlignmentX(LEFT_ALIGNMENT);
        metadata = UiFactory.label(metadataText(request, details), Theme.FontSize.XS);
        metadata.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
        metadata.setToolTipText(metadataText(request, details));
        statusRow.add(statusDot);
        statusRow.add(Box.createHorizontalStrut(UiConstants.SPACING_XS));
        statusRow.add(metadata);
        statusRow.setComponentPopupMenu(contextMenu);
        add(statusRow);
        add(timestamps(request, contextMenu));
        setComponentPopupMenu(contextMenu);
    }

    private static JPanel timestamps(final PullRequest request, final JPopupMenu contextMenu) {
        final JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(LEFT_ALIGNMENT);
        addTimestamp(row, UiIcons.pullRequestCreate(), "Opened", request.createdAt(), contextMenu);
        row.add(Box.createHorizontalStrut(UiConstants.SPACING_SM));
        addTimestamp(row, UiIcons.rotateCwClock(), "Updated", request.updatedAt(), contextMenu);
        return row;
    }

    private static void addTimestamp(
            final JPanel row,
            final javax.swing.Icon icon,
            final String label,
            final Date timestamp,
            final JPopupMenu contextMenu) {
        final String offset = RelativeTime.offsetTime(timestamp);
        final JLabel value = new JLabel(offset, icon, JLabel.LEFT);
        value.setFont(Theme.font(Theme.FontSize.XS));
        value.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
        final String description =
                "unknown".equals(offset)
                        ? label
                        : label + ("now".equals(offset) ? " just now" : " " + offset + " ago");
        final String localDateTime = RelativeTime.localDateTime(timestamp);
        value.setToolTipText(timestamp == null ? description : description + "\n" + localDateTime);
        value.setComponentPopupMenu(contextMenu);
        row.add(value);
    }

    private JPopupMenu menu(final PullRequest request) {
        final JPopupMenu menu = new JPopupMenu();
        addCommonActions(menu, request);
        final Project project = actionContext.appState().projects().get(request.projectId());
        addReviewerActions(menu, project, request, details);
        addMergeAndCloseActions(menu, project, request, details);
        return menu;
    }

    private void addCommonActions(final JPopupMenu menu, final PullRequest request) {
        final JMenuItem open = new JMenuItem("Open PR");
        open.addActionListener(event -> PlatformCommands.openUrl(request.url().toExternalForm()));
        final JMenuItem copyUrl = new JMenuItem("Copy URL");
        copyUrl.addActionListener(event -> CopyPathAction.copy(request.url().toExternalForm()));
        final JMenuItem importItem = new JMenuItem("Import PR branch");
        importItem.addActionListener(
                event -> ImportBranchAction.importPullRequest(actionContext, request));
        final JMenuItem reviewItem = new JMenuItem("Review PR");
        reviewItem.addActionListener(event -> startReview(request));
        menu.add(open);
        menu.add(copyUrl);
        menu.addSeparator();
        menu.add(importItem);
        menu.add(reviewItem);
    }

    private void addReviewerActions(
            final JPopupMenu menu,
            final Project project,
            final PullRequest request,
            final PullRequestDetails requestDetails) {
        if (project == null) {
            return;
        }
        final String githubUser = project.githubUser();
        if (githubUser == null || githubUser.isBlank()) {
            return;
        }
        if (githubUser.equalsIgnoreCase(request.author().login())) {
            menu.addSeparator();
            if (isDraft(requestDetails)) {
                final JMenuItem requestApproval = new JMenuItem("Request approval");
                requestApproval.addActionListener(event -> requestApproval(project, request));
                menu.add(requestApproval);
            } else {
                final JMenuItem makeDraft = new JMenuItem("Make draft");
                makeDraft.addActionListener(event -> makeDraft(project, request));
                menu.add(makeDraft);
            }
            return;
        }
        final JMenuItem approveItem = new JMenuItem("Approve");
        approveItem.addActionListener(event -> approve(project, request));
        menu.add(approveItem);
    }

    private static boolean isDraft(final PullRequestDetails requestDetails) {
        return requestDetails != null && requestDetails.draft();
    }

    private void addMergeAndCloseActions(
            final JPopupMenu menu,
            final Project project,
            final PullRequest request,
            final PullRequestDetails requestDetails) {
        if (project == null) {
            return;
        }
        if (requestDetails != null
                && requestDetails.status() == PullRequestDetails.Status.READY
                && request.state() == PullRequest.State.OPEN) {
            final JMenuItem mergeItem = new JMenuItem("Merge PR");
            mergeItem.addActionListener(event -> merge(project, request));
            menu.add(mergeItem);
        }
    }

    private void requestApproval(final Project project, final PullRequest request) {
        if (!confirm(request, "request approval for")) {
            return;
        }
        BackgroundOperations.submit(
                        "Request PR approval",
                        PULL_REQUEST_ACTION,
                        () -> {
                            GitHubPullRequest.markReady(
                                    project,
                                    request.number(),
                                    actionContext.appState().githubConnections());
                            return null;
                        })
                .exceptionally(
                        failure -> {
                            LOG.warn("Request PR approval failed: {}", rootMessage(failure));
                            return null;
                        });
    }

    private void makeDraft(final Project project, final PullRequest request) {
        if (!confirm(request, "make draft")) {
            return;
        }
        BackgroundOperations.submit(
                        "Make PR draft",
                        PULL_REQUEST_ACTION,
                        () -> {
                            GitHubPullRequest.convertToDraft(
                                    project,
                                    request.number(),
                                    actionContext.appState().githubConnections());
                            return null;
                        })
                .exceptionally(
                        failure -> {
                            LOG.warn("Make PR draft failed: {}", rootMessage(failure));
                            return null;
                        });
    }

    private void approve(final Project project, final PullRequest request) {
        if (!confirm(request, "approve")) {
            return;
        }
        BackgroundOperations.submit(
                        "Approve",
                        PULL_REQUEST_ACTION,
                        () -> {
                            GitHubPullRequest.approve(
                                    project,
                                    request.number(),
                                    actionContext.appState().githubConnections());
                            return null;
                        })
                .thenRun(onPullRequestApproved::run)
                .exceptionally(
                        failure -> {
                            LOG.warn("Approve failed: {}", rootMessage(failure));
                            return null;
                        });
    }

    private void merge(final Project project, final PullRequest request) {
        if (!confirm(request, "merge")) {
            return;
        }
        BackgroundOperations.submit(
                        "Merge PR",
                        PULL_REQUEST_ACTION,
                        () -> {
                            GitHubPullRequest.merge(
                                    project,
                                    request.number(),
                                    actionContext.appState().githubConnections());
                            return null;
                        })
                .exceptionally(
                        failure -> {
                            LOG.warn("Merge PR failed: {}", rootMessage(failure));
                            return null;
                        });
    }

    private boolean confirm(final PullRequest request, final String action) {
        return JOptionPane.showConfirmDialog(
                        actionContext.window(),
                        "Are you sure you want to " + action + " PR #" + request.number() + "?",
                        "Confirm " + action,
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE)
                == JOptionPane.YES_OPTION;
    }

    private static String rootMessage(final Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }

    private void startReview(final PullRequest request) {
        if (actionContext.appState().appSettings().agents().isEmpty()) {
            LOG.warn("Review PR: No agents configured");
            final Object[] options = {"Open settings", "Cancel"};
            final int choice =
                    JOptionPane.showOptionDialog(
                            actionContext.window(),
                            "Configure an agent before starting a review.",
                            "No review agent configured",
                            JOptionPane.DEFAULT_OPTION,
                            JOptionPane.WARNING_MESSAGE,
                            null,
                            options,
                            options[0]);
            if (choice == 0) {
                actionContext
                        .viewCoordinator()
                        .updateView(ViewId.SETTINGS, ViewCoordinator.ViewState.reset());
            }
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

    private static String metadataText(
            final PullRequest request, final PullRequestDetails requestDetails) {
        final String status =
                requestDetails == null
                        ? "Loading"
                        : UiText.titleCase(requestDetails.status().name());
        return "@" + request.author().login() + "  ·  " + status;
    }
}
