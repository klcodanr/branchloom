package com.jagent.desktop.ui.components;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.services.BackgroundTasks;
import com.jagent.desktop.services.GitHubPullRequest;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.ViewCoordinator;
import com.jagent.desktop.ui.actions.CopyPathAction;
import com.jagent.desktop.ui.actions.ImportBranchAction;
import com.jagent.desktop.ui.dialogs.ReviewDialog;
import com.jagent.desktop.ui.utils.RelativeTime;
import java.awt.Dimension;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Displays one pull request and its actions. */
public final class PullRequestCard extends JPanel {
    private static final Logger LOG = Logger.getLogger(PullRequestCard.class.getName());
    private final transient ActionContext actionContext;
    private final transient Runnable onPullRequestApproved;
    private transient PullRequest request;
    private final JLabel metadata;

    public PullRequestCard(final ActionContext actionContext, final PullRequest request) {
        this(actionContext, request, () -> {});
    }

    public PullRequestCard(
            final ActionContext actionContext,
            final PullRequest request,
            final Runnable onPullRequestApproved) {
        super();
        this.request = request;
        this.actionContext = actionContext;
        this.onPullRequestApproved = onPullRequestApproved;
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
        final JButton title =
                UiFactory.link(request.title(), () -> PlatformCommands.openUrl(request.url()));
        title.setAlignmentX(LEFT_ALIGNMENT);
        title.setToolTipText(request.title());
        title.getAccessibleContext().setAccessibleName("Open pull request: " + request.title());
        title.setFont(Theme.boldFont(Theme.FontSize.MD));
        title.setComponentPopupMenu(contextMenu);
        add(title);
        final JComponent statusDot =
                new StatusDot(UiText.checksColor(request.checksStatus()), checksSummary(request));
        final JPanel statusRow = new JPanel();
        statusRow.setOpaque(false);
        statusRow.setLayout(new BoxLayout(statusRow, BoxLayout.X_AXIS));
        statusRow.setAlignmentX(LEFT_ALIGNMENT);
        metadata = UiFactory.label(metadataText(request), Theme.FontSize.XS);
        metadata.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
        metadata.setToolTipText(metadataText(request));
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
            final String timestamp,
            final JPopupMenu contextMenu) {
        final String offset = RelativeTime.offsetTime(timestamp, Instant.now());
        final JLabel value = new JLabel(offset, icon, JLabel.LEFT);
        value.setFont(Theme.font(Theme.FontSize.XS));
        value.setForeground(UIManager.getColor(UiConstants.DISABLED_FOREGROUND));
        final String description =
                "unknown".equals(offset)
                        ? label
                        : label + ("now".equals(offset) ? " just now" : " " + offset + " ago");
        final String localDateTime =
                RelativeTime.localDateTime(timestamp, ZoneId.systemDefault(), Locale.getDefault());
        value.setToolTipText(timestamp == null ? description : description + "\n" + localDateTime);
        value.setComponentPopupMenu(contextMenu);
        row.add(value);
    }

    private JPopupMenu menu(final PullRequest request) {
        final JPopupMenu menu = new JPopupMenu();
        addCommonActions(menu, request);
        final Project project =
                request.projectId() == null
                        ? null
                        : actionContext.appState().projects().get(request.projectId());
        addReviewerActions(menu, project, request);
        addMergeAndCloseActions(menu, project, request);
        return menu;
    }

    private void addCommonActions(final JPopupMenu menu, final PullRequest request) {
        final JMenuItem open = new JMenuItem("Open PR");
        open.addActionListener(event -> PlatformCommands.openUrl(request.url()));
        final JMenuItem copyUrl = new JMenuItem("Copy URL");
        copyUrl.addActionListener(event -> CopyPathAction.copy(request.url()));
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
            final JPopupMenu menu, final Project project, final PullRequest request) {
        if (project == null) {
            return;
        }
        final String githubUser = project.githubUser();
        if (githubUser == null || githubUser.isBlank()) {
            return;
        }
        if (githubUser.equalsIgnoreCase(request.author())) {
            menu.addSeparator();
            if (request.draft()) {
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

    private void addMergeAndCloseActions(
            final JPopupMenu menu, final Project project, final PullRequest request) {
        if (project == null) {
            return;
        }
        if (request.mergeActionAllowed()) {
            final JMenuItem mergeItem = new JMenuItem("Merge PR");
            mergeItem.addActionListener(event -> merge(project, request));
            menu.add(mergeItem);
        }
    }

    private void requestApproval(final Project project, final PullRequest request) {
        if (!confirm(request, "request approval for")) {
            return;
        }
        runGitHubAction(
                "Request PR approval",
                () -> GitHubPullRequest.markReady(project, request.number()),
                () -> {});
    }

    private void makeDraft(final Project project, final PullRequest request) {
        if (!confirm(request, "make draft")) {
            return;
        }
        runGitHubAction(
                "Make PR draft",
                () -> GitHubPullRequest.convertToDraft(project, request.number()),
                () -> {});
    }

    private void approve(final Project project, final PullRequest request) {
        if (!confirm(request, "approve")) {
            return;
        }
        runGitHubAction(
                "Approve",
                () -> GitHubPullRequest.approve(project, request.number()),
                this::markApprovedAndRefresh);
    }

    private void markApprovedAndRefresh() {
        markApproved();
        onPullRequestApproved.run();
    }

    private void merge(final Project project, final PullRequest request) {
        if (!confirm(request, "merge")) {
            return;
        }
        runGitHubAction(
                "Merge PR", () -> GitHubPullRequest.merge(project, request.number()), () -> {});
    }

    private void runGitHubAction(
            final String name, final ThrowingAction action, final Runnable onSuccess) {
        BackgroundTasks.submit(
                        name,
                        "pull-request-action",
                        () -> {
                            try {
                                action.run();
                            } catch (IOException exception) {
                                throw new CompletionException(exception);
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                throw new CompletionException(exception);
                            }
                        })
                .thenRunAsync(onSuccess, SwingUtilities::invokeLater)
                .exceptionally(
                        failure -> {
                            LOG.warning(name + " failed: " + rootMessage(failure));
                            return null;
                        });
    }

    protected void markApproved() {
        request =
                new PullRequest(
                        request.projectId(),
                        request.number(),
                        request.title(),
                        request.description(),
                        request.commentSummary(),
                        request.url(),
                        request.createdAt(),
                        request.updatedAt(),
                        "APPROVED",
                        request.mergeable(),
                        request.draft(),
                        request.author(),
                        request.headBranch(),
                        request.additions(),
                        request.deletions(),
                        request.changedFiles(),
                        request.checksPassed(),
                        request.checksTotal(),
                        request.checksStatus(),
                        request.checks());
        metadata.setText(metadataText(request));
        metadata.setToolTipText(metadataText(request));
        metadata.repaint();
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

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws IOException, InterruptedException;
    }

    private void startReview(final PullRequest request) {
        if (actionContext.appState().appSettings().agents().isEmpty()) {
            LOG.warning("Review PR: No agents configured");
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
        return GitFormatter.mergeStatus(request.mergeState());
    }

    private static String checksSummary(final PullRequest request) {
        return request.checksPassed()
                + "/"
                + request.checksTotal()
                + " checks "
                + UiText.titleCase(request.checksStatus());
    }

    private static String metadataText(final PullRequest request) {
        return "@"
                + request.author()
                + "  "
                + reviewStatus(request)
                + "  ·  "
                + mergeStatus(request);
    }
}
