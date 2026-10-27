package com.jagent.desktop.ui.components;

import com.jagent.desktop.api.ViewId;
import com.jagent.desktop.async.ProgressOperation;
import com.jagent.desktop.models.ActionContext;
import com.jagent.desktop.models.Project;
import com.jagent.desktop.models.ProjectId;
import com.jagent.desktop.models.PullRequest;
import com.jagent.desktop.models.PullRequestDetails;
import com.jagent.desktop.models.PullRequestReview;
import com.jagent.desktop.models.Terminal;
import com.jagent.desktop.services.PlatformCommands;
import com.jagent.desktop.services.ViewController;
import com.jagent.desktop.services.github.GitHub;
import com.jagent.desktop.ui.actions.CopyPathAction;
import com.jagent.desktop.ui.actions.ImportBranchAction;
import com.jagent.desktop.ui.actions.OpenSettingsAction;
import com.jagent.desktop.ui.dialogs.ReviewDialog;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Displays one pull request and its actions. */
public final class PullRequestCard extends CardPanel {
    private static final Logger LOG = LoggerFactory.getLogger(PullRequestCard.class);
    private final transient ActionContext actionContext;
    private final transient Function<ProjectId, CompletableFuture<?>> refreshProject;
    private final transient PullRequestDetails details;
    private final JLabel metadata;
    private final transient PullRequest request;
    private final transient Project project;
    private final transient GitHub gitHub;
    private boolean actionInFlight;

    public PullRequestCard(
            final ActionContext actionContext,
            final PullRequest request,
            final PullRequestDetails details,
            final Function<ProjectId, CompletableFuture<?>> refreshProject) {
        super();
        this.request = request;
        this.actionContext = actionContext;
        this.refreshProject = refreshProject;
        this.details = details;
        this.project = actionContext.appState().projects().get(request.projectId());
        this.gitHub = GitHub.forProject(actionContext.appState(), request.projectId());
        final JPopupMenu contextMenu = menu();
        addNumber(request.project().name() + " · PR · #" + request.number(), contextMenu);
        addTitle(request.title(), contextMenu);
        final JComponent statusDot =
                new StatusDot(details == null ? Theme.Colors.muted() : details.indicatorColor());
        final JPanel statusRow = new JPanel();
        statusRow.setOpaque(false);
        statusRow.setLayout(new BoxLayout(statusRow, BoxLayout.X_AXIS));
        statusRow.setAlignmentX(LEFT_ALIGNMENT);
        metadata = new JLabel(metadataText(request, details));
        metadata.setFont(Theme.font(Theme.FontSize.XS));
        metadata.setForeground(Theme.Colors.muted());
        metadata.setToolTipText(metadataText(request, details));
        statusRow.add(statusDot);
        statusRow.add(Box.createHorizontalStrut(UiConstants.SPACING_XS));
        statusRow.add(metadata);
        statusRow.setComponentPopupMenu(contextMenu);
        add(statusRow);
        add(timestamps(contextMenu));
        setComponentPopupMenu(contextMenu);
    }

    private JPanel timestamps(final JPopupMenu contextMenu) {
        final JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(LEFT_ALIGNMENT);
        addTimestamp(
                row, UiIcons.pullRequestCreate(), "Opened", this.request.createdAt(), contextMenu);
        row.add(Box.createHorizontalStrut(UiConstants.SPACING_SM));
        addTimestamp(
                row, UiIcons.rotateCwClock(), "Updated", this.request.updatedAt(), contextMenu);
        return row;
    }

    private static void addTimestamp(
            final JPanel row,
            final javax.swing.Icon icon,
            final String label,
            final Date timestamp,
            final JPopupMenu contextMenu) {
        final String offset = UiText.relativeTime(timestamp);
        final JLabel value = new JLabel(offset, icon, JLabel.LEFT);
        value.setFont(Theme.font(Theme.FontSize.XS));
        value.setForeground(Theme.Colors.muted());
        final String description = "Unknown".equals(offset) ? label : label + " " + offset;
        final String localDateTime = UiText.localDateTime(timestamp);
        value.setToolTipText(timestamp == null ? description : description + "\n" + localDateTime);
        value.setComponentPopupMenu(contextMenu);
        row.add(value);
    }

    private JPopupMenu menu() {
        final JPopupMenu menu = new JPopupMenu();
        addCommonActions(menu);
        addReviewerActions(menu, project, request, details);
        addMergeAndCloseActions(menu, project, request, details);
        return menu;
    }

    private void addCommonActions(final JPopupMenu menu) {
        final URL url = request.url();
        final JMenuItem open = new JMenuItem("Open PR");
        open.setEnabled(url != null);
        open.addActionListener(event -> PlatformCommands.openUrl(url.toExternalForm()));
        final JMenuItem copyUrl = new JMenuItem("Copy URL");
        copyUrl.setEnabled(url != null);
        copyUrl.addActionListener(event -> CopyPathAction.copy(url.toExternalForm()));
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
        final String githubUser = currentGithubUser();
        if (githubUser == null || githubUser.isBlank()) {
            return;
        }
        if (githubUser.equalsIgnoreCase(request.author().login())) {
            menu.addSeparator();
            if (isDraft(requestDetails)) {
                final JMenuItem requestApproval = new JMenuItem("Request approval");
                requestApproval.addActionListener(
                        event ->
                                performAction(
                                        request,
                                        "request approval for",
                                        "Request PR approval",
                                        "Requesting PR approval...",
                                        () -> {
                                            this.gitHub.markReady(request);
                                            return null;
                                        }));
                menu.add(requestApproval);
            } else {
                final JMenuItem makeDraft = new JMenuItem("Make draft");
                makeDraft.addActionListener(
                        event ->
                                performAction(
                                        request,
                                        "make draft",
                                        "Make PR draft",
                                        "Making PR draft...",
                                        () -> {
                                            this.gitHub.convertToDraft(request);
                                            return null;
                                        }));
                menu.add(makeDraft);
            }
            return;
        }
        boolean approvedByCurrentUser = false;
        if (requestDetails != null) {
            try {
                approvedByCurrentUser =
                        requestDetails.reviews().statusFor(this.gitHub.getLogin())
                                == PullRequestReview.Status.APPROVED;
            } catch (IOException exception) {
                LOG.warn("Failed to determine current review status", exception);
            }
        }
        if (!approvedByCurrentUser) {
            final JMenuItem approveItem = new JMenuItem("Approve");
            approveItem.addActionListener(
                    event ->
                            performAction(
                                    request,
                                    "approve",
                                    "Approve",
                                    "Approving PR...",
                                    () -> {
                                        this.gitHub.approve(request);
                                        return null;
                                    }));
            menu.add(approveItem);
        }
    }

    private String currentGithubUser() {
        try {
            return this.gitHub.getLogin();
        } catch (IOException exception) {
            LOG.warn("Failed to get GitHub login", exception);
            return null;
        }
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
            mergeItem.addActionListener(
                    event ->
                            performAction(
                                    request,
                                    "merge",
                                    "Merge PR",
                                    "Merging PR...",
                                    () -> {
                                        this.gitHub.merge(request);
                                        return null;
                                    }));
            menu.add(mergeItem);
        }
    }

    private void performAction(
            final PullRequest request,
            final String action,
            final String title,
            final String message,
            final Callable<Void> operation) {
        if (actionInFlight || !confirm(request, action)) {
            return;
        }
        actionInFlight = true;
        ProgressOperation.runWithCompletion(
                        actionContext,
                        title,
                        message,
                        operation,
                        ignored -> refreshProject.apply(request.projectId()))
                .thenRun(() -> actionInFlight = false)
                .exceptionally(
                        failure -> {
                            actionInFlight = false;
                            LOG.warn("{} failed: {}", title, rootMessage(failure));
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
                new OpenSettingsAction(actionContext).execute();
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
                                            .viewController()
                                            .appState()
                                            .addTerminal(
                                                    new Terminal(
                                                            null,
                                                            request.projectId(),
                                                            title,
                                                            command));
                            actionContext
                                    .viewController()
                                    .updateView(
                                            ViewId.PROJECT,
                                            ViewController.ViewState.projectTerminal(
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
